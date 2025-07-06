package com.mysillydreams.delivery.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryDeliveredEventDto {
    private UUID assignmentId;
    private UUID orderId; // Good for correlation
    private Instant timestamp; // Actual delivery time
    // private String deliveryPhotoUrl; // Optional
    // private String recipientSignatureUrl; // Optional
    // private String deliveryNotes; // Optional
}
