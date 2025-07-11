package com.mysillydreams.payment.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
// This is a simplified DTO. Actual Razorpay webhook payloads are more complex.
// Refer to Razorpay documentation for the full structure of 'payment.authorized' event.
// Example fields:
// {
//   "entity": "event",
//   "event": "payment.authorized",
//   "contains": ["payment"],
//   "payload": {
//     "payment": {
//       "entity": {
//         "id": "pay_xxxxxxxxxxxxxx",
//         "order_id": "order_xxxxxxxxxxxx",
//         "amount": 10000, // in paise
//         "currency": "INR",
//         "status": "authorized",
//         // ... many other fields
//       }
//     }
//   }
// }

@Data
@NoArgsConstructor
public class PaymentAuthorizedWebhookDto {
    // Assuming we're interested in the 'payment' entity within the payload
    private RazorpayPaymentEntityDto payment;

    @Data
    @NoArgsConstructor
    public static class RazorpayPaymentEntityDto {
        private String id; // Razorpay Payment ID (e.g., pay_xxxxxxxxxxxxxx)
        private String order_id; // Razorpay Order ID
        private long amount; // Amount in smallest currency unit (e.g., paise)
        private String currency;
        private String status; // Should be "authorized" for this event
        private String method;
        private String description;
        private String email;
        private String contact;
        // Add other fields as needed from Razorpay's actual payment entity
    }
    // We might also need top-level event fields like 'event', 'entity', 'created_at' from the webhook if relevant for processing
}
