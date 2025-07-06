package com.mysillydreams.payment.service;

import com.mysillydreams.payment.domain.PaymentTransaction;
import com.mysillydreams.payment.dto.PaymentAuthorizedWebhookDto;
import com.mysillydreams.payment.dto.PaymentFailedWebhookDto;
import com.mysillydreams.payment.dto.PaymentRequestedEvent;
import com.mysillydreams.payment.repository.PaymentRepository;
import com.razorpay.Order;
import com.razorpay.Payment;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.annotation.Counted; // For @Counted
import io.micrometer.core.annotation.Timed;   // For @Timed
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
// Timer class no longer needed if all timers are via @Timed
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Service
// @RequiredArgsConstructor // Cannot use with manual constructor for metrics
@Transactional // Apply to all public methods by default
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final OutboxEventService outboxEventService;
    private final RazorpayClient razorpayClient;
    private final VendorPayoutService vendorPayoutService;
    private final MeterRegistry meterRegistry;

    // Metrics
    // Metrics
    private final Counter paymentSuccessTotal; // Programmatic remains for conditional increment
    private final Counter paymentFailureTotal; // Programmatic remains for conditional increment
    // @Counted for payment.service.requests.total will be on processPaymentRequest method
    // @Timed for Razorpay calls will be on helper methods

    public PaymentServiceImpl(PaymentRepository paymentRepository,
                              OutboxEventService outboxEventService,
                              RazorpayClient razorpayClient,
                              VendorPayoutService vendorPayoutService,
                              MeterRegistry meterRegistry) {
        this.paymentRepository = paymentRepository;
        this.outboxEventService = outboxEventService;
        this.razorpayClient = razorpayClient;
        this.vendorPayoutService = vendorPayoutService;
        this.meterRegistry = meterRegistry; // Keep for programmatic counters

        // Initialize Metrics that remain programmatic
        this.paymentSuccessTotal = Counter.builder("payment.service.success.total")
                .description("Total number of successful payment transactions")
                .tags("type", "capture")
                .register(meterRegistry);
        this.paymentFailureTotal = Counter.builder("payment.service.failure.total")
                .description("Total number of failed payment transactions")
                .tags("type", "capture")
                .register(meterRegistry);
    }

    @Value("${kafka.topics.paymentSucceeded}")
    private String paymentSucceededTopic;

    @Value("${kafka.topics.paymentFailed}")
    private String paymentFailedTopic;

    @Override
    // User sketch suggests @Timed("payment.process.time") and @Counted("payment.process.count") here.
    // My current @Counted is "payment.service.requests.total". I'll align with user sketch name.
    @Timed(value = "payment.process.time", description = "Time to process customer payment")
    @Counted(value = "payment.process.count", description = "Total customer payments processed")
    public void processPaymentRequest(PaymentRequestedEvent event) {
        log.info("Processing payment request for order ID: {}, Amount: {} {}",
                event.getOrderId(), event.getAmount(), event.getCurrency());

        // TODO: Extract vendorId from event or lookup based on orderId.
        // This is a placeholder. In a real system, vendorId would come from the order context.
        UUID vendorId = determineVendorIdForOrder(event.getOrderId());

        // Idempotency check: Has a payment transaction for this orderId already been successfully processed?
        // This simple check might need to be more robust based on retry / idempotency key strategy.
        // Optional<PaymentTransaction> existingTx = paymentRepository.findByOrderId(UUID.fromString(event.getOrderId()));
        // if (existingTx.isPresent() && "SUCCEEDED".equals(existingTx.get().getStatus())) {
        //     log.warn("Payment for order ID {} already succeeded. Skipping.", event.getOrderId());
        //     // Optionally re-publish success event or handle as needed
        //     return;
        // }

        String razorpayOrderId = null;
        String razorpayPaymentId = null;
        String paymentStatus = "PENDING"; // Initial status

        // Persist initial transaction record
        PaymentTransaction transaction = new PaymentTransaction(
                UUID.fromString(event.getOrderId()),
                BigDecimal.valueOf(event.getAmount()),
                event.getCurrency(),
                paymentStatus,
                null, null, null); // razorpay IDs and error message are null initially
        transaction = paymentRepository.save(transaction); // Save to get generated ID and persist initial state

        try {
            // 1. Create an Order in Razorpay
            // Amount should be in the smallest currency unit (e.g., paise for INR)
            long amountInPaise = (long) (event.getAmount() * 100);
            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", amountInPaise);
            orderRequest.put("currency", event.getCurrency());
            orderRequest.put("receipt", event.getOrderId()); // Your internal order ID as receipt
            // orderRequest.put("payment_capture", 1); // Auto-capture payment after authorization
            // orderRequest.put("notes", new JSONObject().put("internal_order_id", event.getOrderId()));

            log.debug("Creating Razorpay order with request: {}", orderRequest.toString());
            Order rpOrder = razorpayClient.Orders.create(orderRequest);
            razorpayOrderId = rpOrder.get("id");
            transaction.setRazorpayOrderId(razorpayOrderId);
            log.info("Razorpay Order created: ID = {} for our Order ID = {}", razorpayOrderId, event.getOrderId());


            // 2. Capture Payment (Simulated for server-to-server flow)
            // The guide's example `razorpay.Payments.capture(rpOrder.get("id"), captureReq)` seems to imply
            // that a payment ID is already available to capture against the Razorpay order ID.
            // This is not standard for Orders API. Usually, client-side integration (e.g. Razorpay Checkout)
            // would use this `order_id` to make a payment, yielding a `razorpay_payment_id`.
            // Then, if `payment_capture` was 0, you'd capture it using `razorpay_payment_id`.
            // If `payment_capture` was 1, it's auto-captured.

            // For a purely server-to-server synchronous capture *after* order creation,
            // this step is unusual unless it's a specific Razorpay flow (e.g. with saved cards or tokens).
            // Let's assume for this example, the "capture" step is more about confirming the auto-capture
            // or fetching the payment details associated with this auto-captured order.
            // A more realistic server-to-server payment creation (without frontend) might use different APIs
            // or involve a pre-existing customer token.

            // Simulating fetching the payment after potential auto-capture or for testing:
            // This part needs clarification on the exact Razorpay flow being used.
            // If payment is auto-captured with "payment_capture": 1, the order status itself might reflect this,
            // and webhooks are crucial.

            // The example `Payment rpPayment = razorpay.Payments.capture(rpOrder.get("id"), captureReq);`
            // is problematic because `rpOrder.get("id")` is an order_id, not a payment_id.
            // `Payments.capture(paymentId, captureRequest)` requires a `payment_id` that is in 'authorized' state.

            // Let's assume the example meant to fetch payments for an order and find the auto-captured one,
            // or that there's a direct way to get payment_id from an auto-captured order.
            // This is a simplification: in reality, you'd get payment_id from checkout or webhook.
            // For now, to make the code runnable based on the guide's structure, we'll simulate a successful capture
            // and assign a dummy payment ID if one cannot be directly obtained here.
            // THIS IS A MOCK/SIMULATION for the synchronous capture part.
            // A real implementation relies on webhooks or client-side returning payment_id.

            // Calls to helper methods that are resilience-enabled
            Order rpOrder = callRazorpayCreateOrder(orderRequest);
            razorpayOrderId = rpOrder.get("id");
            transaction.setRazorpayOrderId(razorpayOrderId);
            log.info("Razorpay Order created: ID = {} for our Order ID = {}", razorpayOrderId, event.getOrderId());

            List<Payment> payments = callRazorpayFetchPayments(razorpayOrderId);
            if (!payments.isEmpty()) {
                Payment firstPayment = payments.get(0);
                if ("captured".equalsIgnoreCase(firstPayment.get("status")) || "authorized".equalsIgnoreCase(firstPayment.get("status"))) {
                    if ("authorized".equalsIgnoreCase(firstPayment.get("status"))) {
                        JSONObject capturePaymentRequest = new JSONObject();
                        capturePaymentRequest.put("amount", amountInPaise);
                        capturePaymentRequest.put("currency", event.getCurrency());
                        firstPayment = callRazorpayCapturePayment(firstPayment.get("id"), capturePaymentRequest);
                    }
                    razorpayPaymentId = firstPayment.get("id");
                    paymentStatus = "SUCCEEDED";
                    transaction.setRazorpayPaymentId(razorpayPaymentId);
                    transaction.setStatus(paymentStatus);
                    log.info("Payment captured/verified for Razorpay Order ID {}: Payment ID = {}", razorpayOrderId, razorpayPaymentId);
                } else {
                     throw new RazorpayException("Payment for order " + razorpayOrderId + " was not in capturable state. Status: " + firstPayment.get("status"));
                }
            } else {
                // This case means no payment was made against the order yet, or auto-capture didn't happen as expected.
                // This would typically be an async flow waiting for client-side or webhook.
                // For this synchronous example to proceed per guide, we'll assume this is an error.
                throw new RazorpayException("No payment found for Razorpay Order ID " + razorpayOrderId + " to capture/verify.");
            }


            // 3. Persist final state (already have 'transaction' object)
            // (status and razorpay IDs updated above)
            paymentRepository.save(transaction);

            // 4. Outbox publish
            outboxEventService.publish(
                    "Payment", // Aggregate Type
                    transaction.getId().toString(), // Aggregate ID (our PaymentTransaction ID)
                    paymentSucceededTopic,
                    Map.of("orderId", event.getOrderId(),
                           "paymentId", razorpayPaymentId, // Razorpay's payment ID
                           "transactionTimestamp", System.currentTimeMillis())
            );
            paymentSuccessTotal.increment(); // Increment success counter
            log.info("Payment succeeded for Order ID {}. Published to outbox.", event.getOrderId());

            // After successful customer payment, initiate vendor payout
            if (vendorId != null) { // Ensure vendorId was determined
                log.info("Initiating vendor payout for successful PaymentTransaction ID: {}, Order ID: {}", transaction.getId(), event.getOrderId());
                vendorPayoutService.initiatePayout(
                        transaction.getId(),
                        vendorId,
                        transaction.getAmount(), // Gross amount from the payment transaction
                        transaction.getCurrency()
                );
            } else {
                log.warn("Vendor ID not determined for Order ID {}. Skipping vendor payout initiation.", event.getOrderId());
            }

        } catch (RazorpayException e) {
            log.error("RazorpayException for Order ID {}: {}", event.getOrderId(), e.getMessage(), e);
            transaction.setStatus("FAILED");
            transaction.setErrorMessage(e.getMessage());
            paymentRepository.save(transaction);

            outboxEventService.publish(
                    "Payment",
                    transaction.getId().toString(),
                    paymentFailedTopic,
                    Map.of("orderId", event.getOrderId(),
                           "reason", e.getMessage(),
                           "transactionTimestamp", System.currentTimeMillis())
            );
            paymentFailureTotal.increment(); // Increment failure counter
            log.warn("Payment failed for Order ID {}. Published failure to outbox.", event.getOrderId());
        } catch (Exception e) { // Catch other unexpected errors
            log.error("Unexpected exception during payment processing for Order ID {}: {}", event.getOrderId(), e.getMessage(), e);
            transaction.setStatus("FAILED");
            transaction.setErrorMessage("Unexpected error: " + e.getMessage());
            paymentRepository.save(transaction);

            outboxEventService.publish(
                    "Payment",
                    transaction.getId().toString(),
                    paymentFailedTopic,
                    Map.of("orderId", event.getOrderId(),
                           "reason", "Unexpected processing error: " + e.getMessage(),
                           "transactionTimestamp", System.currentTimeMillis())
            );
            paymentFailureTotal.increment(); // Increment failure counter for unexpected errors too
            log.warn("Payment failed due to unexpected error for Order ID {}. Published failure to outbox.", event.getOrderId());
        }
    }

    @Override
    public void handleWebhookPaymentAuthorized(PaymentAuthorizedWebhookDto webhookDto) {
        log.info("Handling 'payment.authorized' webhook for Razorpay Payment ID: {}", webhookDto.getPayment().getId());
        // 1. Find PaymentTransaction by razorpay_payment_id or razorpay_order_id
        // 2. Update status if needed (e.g., from PENDING to AUTHORIZED or SUCCEEDED if capture is confirmed)
        // 3. Persist changes
        // 4. Optionally, publish internal event via outbox if this state change is significant for other services
        //    (e.g., if initial processing only created an order and this confirms client-side payment)
        // For now, this is a stub.
        // Ensure idempotency: check current status before updating.
    }

    @Override
    public void handleWebhookPaymentFailed(PaymentFailedWebhookDto webhookDto) {
        log.info("Handling 'payment.failed' webhook for Razorpay Payment ID: {}", webhookDto.getPayment().getId());
        // 1. Find PaymentTransaction
        // 2. Update status to FAILED, store error details
        // 3. Persist changes
        // 4. Optionally, publish internal event via outbox (e.g., if a previously PENDING tx now definitively FAILED)
        // For now, this is a stub.
    }

    // TODO: Implement refund methods


    /**
     * Placeholder method to determine the vendor ID for a given order.
     * In a real system, this would involve looking up order details,
     * possibly from another service or a shared data store, or the PaymentRequestedEvent
     * might need to carry the vendorId.
     *
     * @param orderId The order ID.
     * @return The UUID of the vendor, or null if not found/applicable.
     */
    private UUID determineVendorIdForOrder(String orderId) {
        // This is a critical piece of business logic that needs to be implemented correctly.
        // For example, if orderId maps to a specific product, and product maps to vendor.
        log.warn("TODO: determineVendorIdForOrder for Order ID {} is a placeholder. Needs actual implementation.", orderId);
        // Simulate finding a vendor for some orders
        if (orderId.hashCode() % 2 == 0) { // Arbitrary logic for example
            // This would be a lookup, e.g., from an Order object or service.
            // Returning a fixed UUID for testing purposes.
            return UUID.fromString("00000000-0000-0000-0000-000000000001"); // Example vendor UUID
        }
        return null; // Simulate vendor not found or not applicable
    }

    // --- Resilience4j Helper Methods & Fallbacks for Razorpay API calls ---

import io.micrometer.core.annotation.Timed; // Import @Timed

// ... (other imports)

// --- Resilience4j Helper Methods & Fallbacks for Razorpay API calls ---
    @Timed(value = "payment.service.razorpay.orders.create.timer", description = "Timer for Razorpay Order create API calls", percentiles = {0.5, 0.95, 0.99})
    @CircuitBreaker(name = "razorpayOrdersApi", fallbackMethod = "createOrderFallback")
    @Retry(name = "razorpayApiRetry")
    protected Order callRazorpayCreateOrder(JSONObject orderRequest) throws RazorpayException {
        log.debug("Calling RazorpayClient.Orders.create: {}", orderRequest.toString());
        // return razorpayOrderCreateTimer.recordCallable(() -> razorpayClient.Orders.create(orderRequest)); // Removed programmatic timer
        return razorpayClient.Orders.create(orderRequest);
    }

    protected Order createOrderFallback(JSONObject orderRequest, Throwable t) throws RazorpayException {
        log.warn("Fallback for callRazorpayCreateOrder due to: {}. Request: {}", t.getMessage(), orderRequest.toString());
        // Re-throw as RazorpayException to be caught by the main try-catch block in processPaymentRequest
        if (t instanceof RazorpayException) throw (RazorpayException) t;
        throw new RazorpayException("Razorpay Orders.create call failed and fallback triggered: " + t.getMessage());
    }

    @CircuitBreaker(name = "razorpayOrdersApi", fallbackMethod = "fetchPaymentsFallback") // Can use same CB for all Order API calls
    @Retry(name = "razorpayApiRetry")
    protected List<Payment> callRazorpayFetchPayments(String razorpayOrderId) throws RazorpayException {
        log.debug("Calling RazorpayClient.Orders.fetchPayments for order ID: {}", razorpayOrderId);
        return razorpayPaymentsFetchTimer.recordCallable(() -> razorpayClient.Orders.fetchPayments(razorpayOrderId));
    }

    protected List<Payment> fetchPaymentsFallback(String razorpayOrderId, Throwable t) throws RazorpayException {
        log.warn("Fallback for callRazorpayFetchPayments for order ID {} due to: {}", razorpayOrderId, t.getMessage());
        if (t instanceof RazorpayException) throw (RazorpayException) t;
        throw new RazorpayException("Razorpay Orders.fetchPayments call failed and fallback triggered: " + t.getMessage());
    }

    @CircuitBreaker(name = "razorpayPaymentsApi", fallbackMethod = "capturePaymentFallback")
    @Retry(name = "razorpayApiRetry")
    protected Payment callRazorpayCapturePayment(String paymentId, JSONObject captureRequest) throws RazorpayException {
        log.debug("Calling RazorpayClient.Payments.capture for payment ID {}: {}", paymentId, captureRequest.toString());
        return razorpayPaymentCaptureTimer.recordCallable(() -> razorpayClient.Payments.capture(paymentId, captureRequest));
    }

    protected Payment capturePaymentFallback(String paymentId, JSONObject captureRequest, Throwable t) throws RazorpayException {
        log.warn("Fallback for callRazorpayCapturePayment for payment ID {} due to: {}. Request: {}",
                paymentId, t.getMessage(), captureRequest.toString());
        if (t instanceof RazorpayException) throw (RazorpayException) t;
        throw new RazorpayException("Razorpay Payments.capture call failed and fallback triggered: " + t.getMessage());
    }
}
