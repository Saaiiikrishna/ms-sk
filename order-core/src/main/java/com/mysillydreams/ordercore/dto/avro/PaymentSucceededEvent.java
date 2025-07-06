package com.mysillydreams.ordercore.dto.avro;

// Placeholder for an Avro-generated class.
public record PaymentSucceededEvent(
    String orderId, // Assuming String for UUID
    String paymentTransactionId,
    java.math.BigDecimal amountPaid // Example: Avro might use double or string for decimal
    // other relevant fields...
) {
}
