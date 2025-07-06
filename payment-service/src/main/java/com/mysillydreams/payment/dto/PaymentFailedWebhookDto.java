package com.mysillydreams.payment.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

// This is a simplified DTO for a 'payment.failed' webhook event.
// Refer to Razorpay documentation for the full structure.
// Example fields (similar to authorized but with failure details):
// {
//   "entity": "event",
//   "event": "payment.failed",
//   "contains": ["payment"],
//   "payload": {
//     "payment": {
//       "entity": {
//         "id": "pay_xxxxxxxxxxxxxx",
//         "order_id": "order_xxxxxxxxxxxx",
//         "amount": 10000,
//         "currency": "INR",
//         "status": "failed",
//         "error_code": "BAD_REQUEST_ERROR",
//         "error_description": "Payment failed due to an invalid CVV.",
//         "error_source": "gateway",
//         "error_step": "payment_authentication",
//         "error_reason": "cvv_invalid",
//         // ... other fields
//       }
//     }
//   }
// }

@Data
@NoArgsConstructor
public class PaymentFailedWebhookDto {
    private RazorpayPaymentEntityDto payment; // Re-use the payment entity DTO

    // Inner class for payment entity, can be same as in PaymentAuthorizedWebhookDto
    // or a shared one if fields are identical for this part of payload.
    // For simplicity, assuming it's similar enough.
    @Data
    @NoArgsConstructor
    public static class RazorpayPaymentEntityDto {
        private String id;
        private String order_id;
        private long amount;
        private String currency;
        private String status; // Should be "failed"
        private String error_code;
        private String error_description;
        private String error_source;
        private String error_step;
        private String error_reason;
        // ... other relevant fields
    }
}
