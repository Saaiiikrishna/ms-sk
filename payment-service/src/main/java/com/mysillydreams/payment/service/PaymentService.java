package com.mysillydreams.payment.service;

import com.mysillydreams.payment.dto.PaymentAuthorizedWebhookDto;
import com.mysillydreams.payment.dto.PaymentFailedWebhookDto;
import com.mysillydreams.payment.dto.PaymentRequestedEvent;
// Import DTOs for refund if those methods are to be defined
// import com.mysillydreams.payment.dto.RefundWebhookDto; // Example

public interface PaymentService {

    /**
     * Processes a payment request event.
     * This typically involves interacting with the Razorpay API to create an order and capture payment,
     * then persisting the transaction and publishing success/failure events.
     *
     * @param event The payment request event.
     */
    void processPaymentRequest(PaymentRequestedEvent event);

    /**
     * Handles a 'payment.authorized' webhook event from Razorpay.
     *
     * @param webhookDto The DTO representing the parsed webhook payload.
     */
    void handleWebhookPaymentAuthorized(PaymentAuthorizedWebhookDto webhookDto);

    /**
     * Handles a 'payment.failed' webhook event from Razorpay.
     *
     * @param webhookDto The DTO representing the parsed webhook payload.
     */
    void handleWebhookPaymentFailed(PaymentFailedWebhookDto webhookDto);

    // TODO: Define methods for refund processing if applicable
    // void processRefundRequest(RefundRequestedEvent event);
    // void handleWebhookRefundProcessed(RefundWebhookDto dto); // Example DTO
}
