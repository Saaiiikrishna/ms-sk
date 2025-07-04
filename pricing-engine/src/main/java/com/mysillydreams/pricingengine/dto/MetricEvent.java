package com.mysillydreams.pricingengine.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

// Placeholder DTO for demand metrics
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MetricEvent {
    private UUID eventId;
    private String metricType; // e.g., "VIEW", "ADD_TO_CART", "SALE"
    private UUID itemId;
    private Instant timestamp;
    private Map<String, Object> details; // e.g., quantity, price, userSegment
}
