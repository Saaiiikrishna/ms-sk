package com.mysillydreams.ordercore.dto;

import com.mysillydreams.ordercore.domain.enums.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderStatusUpdatedEventDto {
    private UUID orderId;
    private OrderStatus oldStatus;
    private OrderStatus newStatus;
    private String changedBy;
    private Instant timestamp;
    private Map<String, Object> metadata; // Optional additional context

    public OrderStatusUpdatedEventDto(UUID orderId, OrderStatus oldStatus, OrderStatus newStatus, String changedBy, Instant timestamp) {
        this.orderId = orderId;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
        this.changedBy = changedBy;
        this.timestamp = timestamp;
        this.metadata = null; // Or Collections.emptyMap();
    }
}
