package com.mysillydreams.payment.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mysillydreams.payment.dto.PaymentAuthorizedWebhookDto;
import com.mysillydreams.payment.dto.PaymentFailedWebhookDto;
import com.mysillydreams.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.util.StringUtils; // Import Spring's StringUtils

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat; // Java 17+ for hex conversion

@RestController
@RequestMapping("/webhook/razorpay")
@RequiredArgsConstructor
@Slf4j
public class RazorpayWebhookController {

    @Value("${payment.razorpay.webhook.secret}")
    private String webhookSecret;

    private final PaymentService paymentService; // For payment webhooks
    private final VendorPayoutService vendorPayoutService; // For payout webhooks
    private final ObjectMapper objectMapper; // Spring Boot auto-configures one

    private static final String HMAC_SHA256_ALGORITHM = "HmacSHA256";

    @PostMapping // This single endpoint handles all Razorpay webhooks
    public ResponseEntity<String> handleRazorpayWebhook(@RequestBody String payload,
                                                        @RequestHeader("X-Razorpay-Signature") String signature) {
        log.info("Received Razorpay webhook. Payload size: {} bytes. Signature: {}", payload.length(), signature);
        log.debug("Webhook payload: {}", payload);

        if (!StringUtils.hasText(webhookSecret)) {
            log.error("Razorpay webhook secret is not configured. Cannot verify signature.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Webhook secret not configured.");
        }
        if (!StringUtils.hasText(payload) || !StringUtils.hasText(signature)) {
            log.warn("Missing payload or signature in Razorpay webhook.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Missing payload or signature.");
        }

        try {
            if (!verifySignature(payload, signature, webhookSecret)) {
                log.warn("Invalid Razorpay webhook signature. Payload: {}, Signature: {}", payload, signature);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid signature.");
            }
            log.info("Razorpay webhook signature verified successfully.");

            JsonNode rootNode = objectMapper.readTree(payload);
            String eventType = rootNode.path("event").asText();
            JsonNode payloadNode = rootNode.path("payload"); // The actual payload for the event

            log.info("Processing Razorpay event type: {}", eventType);

            // Route based on event type
            if (eventType.startsWith("payment.")) {
                handlePaymentEvent(eventType, payloadNode);
            } else if (eventType.startsWith("payout.")) {
                handlePayoutEvent(eventType, payloadNode);
            } else if (eventType.startsWith("order.")) {
                // Handle order events if needed, e.g., order.paid
                log.info("Received Razorpay order event: {}", eventType);
            } else if (eventType.startsWith("refund.")) {
                // Handle refund events
                log.info("Received Razorpay refund event: {}", eventType);
            } else {
                log.info("Received unhandled Razorpay event category: {}", eventType);
            }

            return ResponseEntity.ok("Webhook acknowledged.");

        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Error during Razorpay webhook signature verification (crypto algorithm issue): {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error during signature verification.");
        } catch (Exception e) { // Includes JsonProcessingException from objectMapper
            log.error("Error processing Razorpay webhook payload for event {}: {}", payload, e.getMessage(), e);
            // It's generally safer to return 200 OK to Razorpay if the signature was valid but processing failed,
            // to prevent Razorpay from resending a webhook that might consistently fail on our end.
            // Specific errors (e.g. transient DB issues) could potentially return 5xx for retries.
            return ResponseEntity.ok("Webhook acknowledged, processing error occurred.");
        }
    }

    private void handlePaymentEvent(String eventType, JsonNode payloadNode) throws Exception {
        // Ensure payloadNode contains the "payment" wrapper with "entity" inside for these events
        JsonNode paymentEntityNode = payloadNode.path("payment").path("entity");
        if (paymentEntityNode.isMissingNode()) {
            log.warn("Webhook event {} missing 'payload.payment.entity'. Payload: {}", eventType, payloadNode.toString());
            return;
        }

        switch (eventType) {
            case "payment.authorized":
                PaymentAuthorizedWebhookDto authorizedDto = objectMapper.treeToValue(paymentEntityNode, PaymentAuthorizedWebhookDto.RazorpayPaymentEntityDto.class);
                // The DTO was defined to expect the payment entity directly.
                // Let's adjust how DTO is mapped or how it's defined.
                // For now, let's assume PaymentAuthorizedWebhookDto itself is the payload.payment.entity
                PaymentAuthorizedWebhookDto authEventDto = new PaymentAuthorizedWebhookDto();
                authEventDto.setPayment(objectMapper.treeToValue(paymentEntityNode, PaymentAuthorizedWebhookDto.RazorpayPaymentEntityDto.class));
                paymentService.handleWebhookPaymentAuthorized(authEventDto);
                break;
            case "payment.failed":
                PaymentFailedWebhookDto failEventDto = new PaymentFailedWebhookDto();
                failEventDto.setPayment(objectMapper.treeToValue(paymentEntityNode, PaymentFailedWebhookDto.RazorpayPaymentEntityDto.class));
                paymentService.handleWebhookPaymentFailed(failEventDto);
                break;
            // Add other payment event cases: payment.captured, etc.
            default:
                log.info("Received unhandled Razorpay payment event type: {}", eventType);
                break;
        }
    }

    private void handlePayoutEvent(String eventType, JsonNode payloadNode) throws Exception {
        // Payout events have structure like: payload.payout.entity
        JsonNode payoutEntityNode = payloadNode.path("payout").path("entity");
        if (payoutEntityNode.isMissingNode()) {
            log.warn("Webhook event {} missing 'payload.payout.entity'. Payload: {}", eventType, payloadNode.toString());
            return;
        }

        String razorpayPayoutId = payoutEntityNode.path("id").asText();
        long createdAtEpoch = payoutEntityNode.path("created_at").asLong(); // Assuming created_at for processed time
        Instant processedAt = Instant.ofEpochSecond(createdAtEpoch);


        switch (eventType) {
            case "payout.processed": // This is a common success event for payouts
                // Or "payout.updated" if status changes to "processed"
                log.info("Handling payout.processed webhook for Razorpay Payout ID: {}", razorpayPayoutId);
                vendorPayoutService.handlePayoutSuccess(razorpayPayoutId, processedAt);
                break;
            case "payout.failed":
            case "payout.reversed": // Reversed might also be treated as a failure or specific handling
                String errorCode = payoutEntityNode.path("failure_reason").asText(); // Or a more specific error field
                String errorMessage = payoutEntityNode.path("status_details").path("description").asText("Payout failed/reversed via webhook.");
                log.info("Handling payout.failed/reversed webhook for Razorpay Payout ID: {}, Error: {}", razorpayPayoutId, errorCode);
                vendorPayoutService.handlePayoutFailed(razorpayPayoutId, errorCode, errorMessage, processedAt); // processedAt is more like failedAt here
                break;
            // Add other payout event cases: payout.initiated, payout.queued, etc.
            default:
                log.info("Received unhandled Razorpay payout event type: {}", eventType);
                break;
        }
    }


    /**
     * Verifies the Razorpay webhook signature.
     *
     * @param payload       The raw request payload string.
     * @param signature     The X-Razorpay-Signature header value.
     * @param secret        The configured webhook secret.
     * @return True if the signature is valid, false otherwise.
     * @throws NoSuchAlgorithmException If HmacSHA256 is not available.
     * @throws InvalidKeyException      If the secret key is invalid.
     */
    private boolean verifySignature(String payload, String signature, String secret)
            throws NoSuchAlgorithmException, InvalidKeyException {
        Mac sha256Hmac = Mac.getInstance(HMAC_SHA256_ALGORITHM);
        SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256_ALGORITHM);
        sha256Hmac.init(secretKey);

        byte[] expectedSignatureBytes = sha256Hmac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        // Convert bytes to hex string. Java 17+ has HexFormat.
        String calculatedSignature = HexFormat.of().formatHex(expectedSignatureBytes);

        return calculatedSignature.equals(signature);
    }
}
