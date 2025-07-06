package com.mysillydreams.delivery.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryPickedUpEventDto {
    private UUID assignmentId;
    private UUID orderId; // Good for correlation by consumers
    private Instant timestamp; // Actual pickup time
    // private String pickupPhotoUrl; // Optional, if consumers need this
    // private String courierNotes; // Optional
}
