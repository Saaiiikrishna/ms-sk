package com.mysillydreams.payment.service;

import com.mysillydreams.payment.config.CommissionProperties;
import com.mysillydreams.payment.domain.PaymentTransaction; // Assuming this is needed for context
import com.mysillydreams.payment.domain.PayoutStatus;
import com.mysillydreams.payment.domain.PayoutTransaction;
import com.mysillydreams.payment.dto.VendorPayoutFailedEvent;
import com.mysillydreams.payment.dto.VendorPayoutInitiatedEvent;
import com.mysillydreams.payment.dto.VendorPayoutSucceededEvent;
import com.mysillydreams.payment.repository.PaymentRepository; // To fetch PaymentTransaction
import com.mysillydreams.payment.repository.PayoutTransactionRepository;
import com.razorpay.Payout; // Razorpay Payout class
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
// @RequiredArgsConstructor // Cannot use with manual constructor for metrics
@Slf4j
public class VendorPayoutService {

    private final PayoutTransactionRepository payoutTransactionRepository;
    private final PaymentRepository paymentRepository;
    private final RazorpayClient razorpayClient;
    private final OutboxEventService outboxEventService;
    private final CommissionProperties commissionProperties;
    private final MeterRegistry meterRegistry;

import io.micrometer.core.annotation.Counted; // Import @Counted
import io.micrometer.core.annotation.Timed;   // Import @Timed

// ... (other imports)

// @RequiredArgsConstructor // Cannot use with manual constructor for metrics
@Slf4j
public class VendorPayoutService {

    // ... (other fields) ...
    private final MeterRegistry meterRegistry;

    // Metrics
    // Metrics
    private final Counter payoutSuccessTotal;    // Programmatic
    private final Counter payoutFailureTotal;    // Programmatic
    // @Counted for payment.service.payouts.attempts.total will be on initiatePayout method
    // @Timed for Razorpay Payouts.create will be on helper method callRazorpayCreatePayout

    @Value("${kafka.topics.vendorPayoutInitiated:vendor.payout.initiated}")
    private String vendorPayoutInitiatedTopic;
    @Value("${kafka.topics.vendorPayoutSucceeded:vendor.payout.succeeded}")
    private String vendorPayoutSucceededTopic;
    @Value("${kafka.topics.vendorPayoutFailed:vendor.payout.failed}")
    private String vendorPayoutFailedTopic;

    @Value("${payment.razorpay.payout.account-id}")
    private String razorpayXAccountId;

    public VendorPayoutService(PayoutTransactionRepository payoutTransactionRepository,
                               PaymentRepository paymentRepository,
                               RazorpayClient razorpayClient,
                               OutboxEventService outboxEventService,
                               CommissionProperties commissionProperties,
                               MeterRegistry meterRegistry) {
        this.payoutTransactionRepository = payoutTransactionRepository;
        this.paymentRepository = paymentRepository;
        this.razorpayClient = razorpayClient;
        this.outboxEventService = outboxEventService;
        this.commissionProperties = commissionProperties;
        this.meterRegistry = meterRegistry; // Keep for programmatic counters

        // Initialize programmatic counters
        this.payoutSuccessTotal = Counter.builder("payment.service.payouts.success.total") // Renamed for consistency
                .description("Total number of successful vendor payouts (confirmed by API/webhook)")
                .register(meterRegistry);
        this.payoutFailureTotal = Counter.builder("payment.service.payouts.failure.total") // Renamed for consistency
                .description("Total number of failed vendor payouts (confirmed by API/webhook)")
                .register(meterRegistry);
    }

    @Transactional // Main transaction for creating PayoutTransaction and initiating event
    // User sketch: @Timed(value = "vendor.payout.time", ...) @Counted(value = "vendor.payout.count", ...)
    // My current @Counted is "payment.service.payouts.attempts.total". I'll align with user sketch.
    @Timed(value = "vendor.payout.time", description = "Time taken to initiate vendor payout")
    @Counted(value = "vendor.payout.count", description = "Number of vendor payouts initiated")
    public UUID initiatePayout(UUID paymentTransactionId, UUID vendorId, BigDecimal grossAmount, String currency) {
        log.info("Initiating payout for PaymentTransaction ID: {}, Vendor ID: {}, Amount: {} {}",
                paymentTransactionId, vendorId, grossAmount, currency);

        PaymentTransaction paymentTx = paymentRepository.findById(paymentTransactionId)
                .orElseThrow(() -> new IllegalArgumentException("PaymentTransaction not found with ID: " + paymentTransactionId));

        BigDecimal commissionRate = commissionProperties.getPercent().divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        BigDecimal commissionAmount = grossAmount.multiply(commissionRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal netAmount = grossAmount.subtract(commissionAmount);

        if (netAmount.compareTo(BigDecimal.ZERO) <= 0) {
            log.error("Net amount for payout is zero or negative for PaymentTransaction ID: {}. Gross: {}, Commission: {}",
                    paymentTransactionId, grossAmount, commissionAmount);
            // This scenario should ideally not happen or be handled by creating a FAILED PayoutTransaction directly.
            // For now, throwing an exception to prevent INIT PayoutTransaction with no payout amount.
            throw new IllegalStateException("Net payout amount is not positive.");
        }

        PayoutTransaction payoutTx = new PayoutTransaction(
                paymentTx, vendorId, grossAmount, commissionAmount, netAmount, currency, PayoutStatus.INIT);
        payoutTransactionRepository.save(payoutTx);
        log.info("Saved PayoutTransaction ID: {} in INIT state.", payoutTx.getId());

        // Publish VendorPayoutInitiatedEvent via Outbox
        VendorPayoutInitiatedEvent initiatedEvent = VendorPayoutInitiatedEvent.newBuilder()
                .setPayoutId(payoutTx.getId().toString())
                .setPaymentId(paymentTransactionId.toString())
                .setVendorId(vendorId.toString())
                .setNetAmount(netAmount.doubleValue())
                .setCurrency(currency)
                .setInitiatedAt(Instant.now().toEpochMilli())
                .build();
        outboxEventService.publish("VendorPayout", payoutTx.getId().toString(), vendorPayoutInitiatedTopic,
                // Convert Avro SpecificRecord to Map for outbox payload if needed, or ensure KafkaTemplate handles SpecificRecord
                // For consistency with existing outbox (Map<String,Object>), convert. ObjectMapper can do this.
                // Or, if KafkaTemplate is <String, SpecificRecord>, then pass directly.
                // Current template is <String, Object>, so SpecificRecord should work if Avro serdes are setup.
                // Let's assume SpecificRecord is fine for KafkaTemplate<String, Object> with Avro Serializer.
                // The OutboxPoller sends `event.getPayload()` which is Map.
                // So, for consistency, the payload for outbox should be a Map.
                 Map.of(
                     "payoutId", initiatedEvent.getPayoutId(),
                     "paymentId", initiatedEvent.getPaymentId(),
                     "vendorId", initiatedEvent.getVendorId(),
                     "netAmount", initiatedEvent.getNetAmount(),
                     "currency", initiatedEvent.getCurrency(),
                     "initiatedAt", initiatedEvent.getInitiatedAt()
                 )
        );
        log.info("Published VendorPayoutInitiatedEvent for Payout ID: {} to outbox.", payoutTx.getId());

        // Trigger asynchronous processing of Razorpay Payout API call
        processRazorpayPayoutAsync(payoutTx); // Call the async method

        return payoutTx.getId();
    }

    @Async // Execute in a separate thread, outside the initial transaction
    @Transactional(propagation = Propagation.REQUIRES_NEW) // New transaction for this async operation
    public void processRazorpayPayoutAsync(PayoutTransaction payoutTransaction) {
        log.info("Async processing Razorpay payout for PayoutTransaction ID: {}", payoutTransaction.getId());
        PayoutTransaction pt = payoutTransactionRepository.findById(payoutTransaction.getId()).orElse(null);
        if (pt == null || pt.getStatus() != PayoutStatus.INIT) {
             log.warn("PayoutTransaction ID {} no longer in INIT state or not found. Current status: {}. Aborting Razorpay call.",
                payoutTransaction.getId(), pt != null ? pt.getStatus() : "NOT_FOUND");
            return;
        }

        try {
            // TODO: Implement lookupVendorFundAccount(pt.getVendorId())
            // This method needs to fetch the vendor's pre-registered fund_account_id from RazorpayX.
            // This might involve another service call or DB lookup (e.g., in a VendorProfile entity).
            // For now, using a placeholder. This is a critical integration point.
            String fundAccountId = lookupVendorFundAccount(pt.getVendorId());
            if (fundAccountId == null || fundAccountId.isBlank()) {
                throw new IllegalStateException("Fund account ID not found for vendor: " + pt.getVendorId());
            }

            JSONObject payoutRequest = new JSONObject();
            payoutRequest.put("account_number", razorpayXAccountId); // Your RazorpayX account number from which payout is made
            payoutRequest.put("fund_account_id", fundAccountId); // Vendor's fund account ID
            payoutRequest.put("amount", pt.getNetAmount().multiply(BigDecimal.valueOf(100)).intValueExact()); // Amount in paise
            payoutRequest.put("currency", pt.getCurrency());
            payoutRequest.put("mode", "IMPS"); // Or NEFT, RTGS, UPI
            payoutRequest.put("purpose", "vendor_payout"); // e.g., "vendor_payout", "refund", "cashback"
            payoutRequest.put("queue_if_low_balance", true); // Or false based on policy
            payoutRequest.put("reference_id", "PAYOUT_" + pt.getId().toString()); // Your internal reference
            payoutRequest.put("narration", "Payout for order related to payment " + pt.getPaymentTransaction().getId());
            // Notes can be added if needed:
            // payoutRequest.put("notes", new JSONObject().put("internal_payout_id", pt.getId().toString()));

            // Call resilience-enabled helper method
            Payout razorpayPayout = callRazorpayCreatePayout(payoutRequest);

            String rzpPayoutId = razorpayPayout.get("id");
            String rzpStatus = razorpayPayout.get("status");

            log.info("Razorpay Payout API call successful for Payout ID: {}. Razorpay Payout ID: {}, Razorpay Status: {}",
                    pt.getId(), rzpPayoutId, rzpStatus);

            pt.setRazorpayPayoutId(rzpPayoutId);
            pt.setStatus(PayoutStatus.PENDING);
            payoutTransactionRepository.save(pt);
            log.info("PayoutTransaction ID {} updated to PENDING with Razorpay Payout ID {}.", pt.getId(), rzpPayoutId);

        } catch (RazorpayException e) {
            log.error("RazorpayException during payout for Payout ID {}: {}",
                    pt.getId(), e.getMessage(), e);
            pt.setStatus(PayoutStatus.FAILED);
            pt.setErrorCode(e.getClass().getSimpleName());
            pt.setErrorMessage(e.getMessage());
            payoutTransactionRepository.save(pt);
            payoutFailureTotal.increment(); // Increment failure counter for API call

            VendorPayoutFailedEvent failedEvent = VendorPayoutFailedEvent.newBuilder()
                    .setPayoutId(pt.getId().toString())
                    .setPaymentId(pt.getPaymentTransaction().getId().toString())
                    .setVendorId(pt.getVendorId().toString())
                    .setNetAmount(pt.getNetAmount().doubleValue())
                    .setCurrency(pt.getCurrency())
                    .setErrorCode(pt.getErrorCode()) // Use the code from exception if available
                    .setErrorMessage(e.getMessage())
                    .setFailedAt(Instant.now().toEpochMilli())
                    .build();
            outboxEventService.publish("VendorPayout", pt.getId().toString(), vendorPayoutFailedTopic,
                    Map.of(
                         "payoutId", failedEvent.getPayoutId(),
                         "paymentId", failedEvent.getPaymentId(),
                         "vendorId", failedEvent.getVendorId(),
                         "netAmount", failedEvent.getNetAmount(),
                         "currency", failedEvent.getCurrency(),
                         "errorCode", failedEvent.getErrorCode() != null ? failedEvent.getErrorCode().toString() : "RazorpayException",
                         "errorMessage", failedEvent.getErrorMessage(),
                         "failedAt", failedEvent.getFailedAt()
                     )
            );
            log.warn("PayoutTransaction ID {} marked FAILED due to Razorpay API error. Published failure event to outbox.", pt.getId());
        } catch (Exception e) { // Catch other unexpected errors
            log.error("Unexpected exception during async Razorpay payout for Payout ID {}: {}", pt.getId(), e.getMessage(), e);
            pt.setStatus(PayoutStatus.FAILED);
            pt.setErrorCode(e.getClass().getSimpleName());
            pt.setErrorMessage("Unexpected error: " + e.getMessage());
            payoutTransactionRepository.save(pt);
            payoutFailureTotal.increment(); // Increment failure counter for unexpected error during API call phase
            // Publish failure event (similar to above)
             VendorPayoutFailedEvent failedEvent = VendorPayoutFailedEvent.newBuilder()
                    .setPayoutId(pt.getId().toString())
                    .setPaymentId(pt.getPaymentTransaction().getId().toString())
                    .setVendorId(pt.getVendorId().toString())
                    .setNetAmount(pt.getNetAmount().doubleValue())
                    .setCurrency(pt.getCurrency())
                    .setErrorCode("InternalError")
                    .setErrorMessage(e.getMessage())
                    .setFailedAt(Instant.now().toEpochMilli())
                    .build();
            outboxEventService.publish("VendorPayout", pt.getId().toString(), vendorPayoutFailedTopic,
                 Map.of(
                         "payoutId", failedEvent.getPayoutId(),
                         "paymentId", failedEvent.getPaymentId(),
                         "vendorId", failedEvent.getVendorId(),
                         "netAmount", failedEvent.getNetAmount(),
                         "currency", failedEvent.getCurrency(),
                         "errorCode", failedEvent.getErrorCode().toString(),
                         "errorMessage", failedEvent.getErrorMessage(),
                         "failedAt", failedEvent.getFailedAt()
                     )
            );
        }
    }

    @Transactional // For updating PayoutTransaction from webhook
    public void handlePayoutSuccess(String razorpayPayoutId, Instant processedAt) {
        log.info("Handling successful payout webhook for Razorpay Payout ID: {}", razorpayPayoutId);
        PayoutTransaction pt = payoutTransactionRepository.findByRazorpayPayoutId(razorpayPayoutId)
                .orElseThrow(() -> {
                    log.warn("PayoutTransaction not found for Razorpay Payout ID: {} from webhook.", razorpayPayoutId);
                    // Depending on policy, might ignore or log as an issue.
                    return new IllegalArgumentException("PayoutTransaction not found for razorpayPayoutId: " + razorpayPayoutId);
                });

        if (pt.getStatus() == PayoutStatus.SUCCESS) {
            log.info("Payout ID {} already marked as SUCCESS. Ignoring webhook.", pt.getId());
            return;
        }
        if (pt.getStatus() == PayoutStatus.FAILED) {
            log.warn("Payout ID {} was FAILED but received a SUCCESS webhook for Razorpay ID {}. Manual investigation needed.", pt.getId(), razorpayPayoutId);
            // This indicates a potential inconsistency. For now, we might honor the success webhook.
        }


        pt.setStatus(PayoutStatus.SUCCESS);
        // pt.setUpdatedAt(processedAt); // Or let @UpdateTimestamp handle it
        payoutTransactionRepository.save(pt);
        payoutSuccessTotal.increment(); // Increment success counter from webhook
        log.info("PayoutTransaction ID {} marked SUCCESS.", pt.getId());

        VendorPayoutSucceededEvent succeededEvent = VendorPayoutSucceededEvent.newBuilder()
                .setPayoutId(pt.getId().toString())
                .setPaymentId(pt.getPaymentTransaction().getId().toString())
                .setVendorId(pt.getVendorId().toString())
                .setRazorpayPayoutId(razorpayPayoutId)
                .setNetAmount(pt.getNetAmount().doubleValue())
                .setCurrency(pt.getCurrency())
                .setProcessedAt(processedAt.toEpochMilli())
                .build();
        outboxEventService.publish("VendorPayout", pt.getId().toString(), vendorPayoutSucceededTopic,
                Map.of(
                    "payoutId", succeededEvent.getPayoutId(),
                    "paymentId", succeededEvent.getPaymentId(),
                    "vendorId", succeededEvent.getVendorId(),
                    "razorpayPayoutId", succeededEvent.getRazorpayPayoutId(),
                    "netAmount", succeededEvent.getNetAmount(),
                    "currency", succeededEvent.getCurrency(),
                    "processedAt", succeededEvent.getProcessedAt()
                )
        );
        log.info("Published VendorPayoutSucceededEvent for Payout ID: {} to outbox.", pt.getId());
    }

    @Transactional
    public void handlePayoutFailed(String razorpayPayoutId, String errorCode, String errorMessage, Instant failedAt) {
        log.info("Handling failed payout webhook for Razorpay Payout ID: {}, Error: {} - {}", razorpayPayoutId, errorCode, errorMessage);
         PayoutTransaction pt = payoutTransactionRepository.findByRazorpayPayoutId(razorpayPayoutId)
                .orElseThrow(() -> {
                    log.warn("PayoutTransaction not found for Razorpay Payout ID: {} from failed webhook.", razorpayPayoutId);
                    return new IllegalArgumentException("PayoutTransaction not found for razorpayPayoutId: " + razorpayPayoutId);
                });

        if (pt.getStatus() == PayoutStatus.FAILED && pt.getRazorpayPayoutId() != null && pt.getRazorpayPayoutId().equals(razorpayPayoutId)) {
            log.info("Payout ID {} already marked FAILED. Ignoring webhook.", pt.getId());
            return;
        }
         if (pt.getStatus() == PayoutStatus.SUCCESS) {
            log.warn("Payout ID {} was SUCCESS but received a FAILED webhook for Razorpay ID {}. Manual investigation needed.", pt.getId(), razorpayPayoutId);
            // This indicates a potential inconsistency.
        }

        pt.setStatus(PayoutStatus.FAILED);
        pt.setErrorCode(errorCode);
        pt.setErrorMessage(errorMessage);
        payoutTransactionRepository.save(pt);
        payoutFailureTotal.increment(); // Increment failure counter from webhook
        log.info("PayoutTransaction ID {} marked FAILED due to webhook.", pt.getId());

        VendorPayoutFailedEvent failedEvent = VendorPayoutFailedEvent.newBuilder()
                .setPayoutId(pt.getId().toString())
                .setPaymentId(pt.getPaymentTransaction().getId().toString())
                .setVendorId(pt.getVendorId().toString())
                .setNetAmount(pt.getNetAmount().doubleValue())
                .setCurrency(pt.getCurrency())
                .setErrorCode(errorCode)
                .setErrorMessage(errorMessage)
                .setFailedAt(failedAt.toEpochMilli())
                .build();
         outboxEventService.publish("VendorPayout", pt.getId().toString(), vendorPayoutFailedTopic,
                Map.of(
                    "payoutId", failedEvent.getPayoutId(),
                    "paymentId", failedEvent.getPaymentId(),
                    "vendorId", failedEvent.getVendorId(),
                    "netAmount", failedEvent.getNetAmount(),
                    "currency", failedEvent.getCurrency(),
                    "errorCode", failedEvent.getErrorCode() != null ? failedEvent.getErrorCode().toString() : "WebhookFailure",
                    "errorMessage", failedEvent.getErrorMessage(),
                    "failedAt", failedEvent.getFailedAt()
                )
        );
        log.info("Published VendorPayoutFailedEvent for Payout ID: {} to outbox due to webhook.", pt.getId());
    }


    // Placeholder - this needs actual implementation
    private String lookupVendorFundAccount(UUID vendorId) {
        // TODO: Implement logic to retrieve the vendor's RazorpayX fund_account_id.
        // This might involve:
        // - Querying a local VendorProfile entity that stores this ID.
        // - Calling a Vendor Service.
        // - This fund_account_id must be created via RazorpayX onboarding for the vendor.
        log.warn("TODO: lookupVendorFundAccount for Vendor ID {} is not fully implemented. Using placeholder.", vendorId);
        // Example: return "fa_xxxxxxxxxxxxxxx"; // Replace with actual lookup
        if (vendorId.toString().endsWith("1")) { // Simulate found for testing
             return "fa_mock_fund_account_id_1";
        } else if (vendorId.toString().endsWith("2")) { // Simulate another for testing
             return "fa_mock_fund_account_id_2";
        }
        // Return null or throw if not found, to be handled by caller.
        // Forcing an error if not one of the test vendorId suffixes.
        // throw new IllegalStateException("Fund account ID lookup not implemented for vendor: " + vendorId);
        return null; // Simulate not found to test failure path
    }

    // --- Resilience4j Helper Method & Fallback for Razorpay Payouts.create ---

    @Timed(value = "payment.service.razorpay.payouts.create.timer", description = "Timer for Razorpay Payouts create API calls", percentiles = {0.5, 0.95, 0.99})
    @CircuitBreaker(name = "razorpayPayoutsApi", fallbackMethod = "createPayoutFallback")
    @Retry(name = "razorpayApiRetry") // Using the same general retry policy
    protected Payout callRazorpayCreatePayout(JSONObject payoutRequest) throws RazorpayException {
        log.debug("Calling RazorpayClient.Payouts.create: {}", payoutRequest.toString());
        // return razorpayPayoutCreateTimer.recordCallable(() -> razorpayClient.Payouts.create(payoutRequest)); // Removed programmatic timer
        return razorpayClient.Payouts.create(payoutRequest);
    }

    protected Payout createPayoutFallback(JSONObject payoutRequest, Throwable t) throws RazorpayException {
        log.warn("Fallback for callRazorpayCreatePayout due to: {}. Request: {}", t.getMessage(), payoutRequest.toString());
        if (t instanceof RazorpayException) throw (RazorpayException) t;
        // Wrap other exceptions in RazorpayException to be handled by the calling method's catch block
        throw new RazorpayException("Razorpay Payouts.create call failed and fallback triggered: " + t.getMessage());
    }
}
