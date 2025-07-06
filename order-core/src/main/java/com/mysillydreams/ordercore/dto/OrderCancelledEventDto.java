package com.mysillydreams.ordercore.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderCancelledEventDto {
    private UUID orderId;
    private String reason;
    private Instant cancelledAt;
    // private String cancelledBy; // Could be useful to add who initiated
}
