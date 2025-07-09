package com.ecommerce.vendorfulfillmentservice.event;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.UUID;
import java.time.OffsetDateTime;

// Placeholder for the event coming from Order-Core service
// The actual schema (likely Avro) would be defined by Order-Core
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderReservationSucceededEvent {
    private String eventId; // Unique ID for this specific event instance, crucial for idempotency
    private UUID orderId;
    private UUID customerId; // May be useful for vendor assignment or context
    private OffsetDateTime reservationTimestamp;
    // Potentially other fields like item list, total amount, etc.
    // For vendor assignment, if a specific vendor is already determined by Order-Core,
    // it could be included here. Otherwise, this service needs assignment logic.
    // private UUID vendorId; // Example: if vendor is pre-assigned
}
