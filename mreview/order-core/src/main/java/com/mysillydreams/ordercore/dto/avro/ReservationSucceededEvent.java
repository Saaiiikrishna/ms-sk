package com.mysillydreams.ordercore.dto.avro;

// Placeholder for an Avro-generated class.
// In a real scenario, this would be generated from an .avsc schema.
// Define fields that would typically be in such an event.
public record ReservationSucceededEvent(
    String orderId, // Assuming String for UUID consistency with other Avro schemas
    String reservationId
    // other relevant fields...
) {
    // This record implicitly has a constructor, getters, equals, hashCode, toString.
    // For Avro specific records, you'd use builders, e.g., ReservationSucceededEvent.newBuilder()...build()
    // This placeholder is just for type compatibility in the listener signature for now.
}
