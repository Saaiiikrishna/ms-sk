package com.mysillydreams.catalogservice.kafka.event;

import com.mysillydreams.catalogservice.dto.StockAdjustmentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockLevelChangedEvent {
    private UUID eventId; // Unique ID for this event
    private UUID itemId;
    private String itemSku;
    private StockAdjustmentType adjustmentType;
    private Integer quantityChanged; // The delta (positive for increase, negative for decrease)
    private Integer quantityBefore;
    private Integer quantityAfter;
    private String reason;
    private String referenceId; // e.g., PO number, order ID, adjustment reference
    private Instant timestamp;
}
