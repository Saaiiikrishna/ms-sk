package com.mysillydreams.pricingengine.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

// Simple DTO for aggregated metrics from Kafka Streams window
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AggregatedMetric {
    private UUID itemId;
    private Long metricCount; // Example: count of views or sales in a window
    private Long windowStartTimestamp;
    private Long windowEndTimestamp;
    // Add other aggregated values as needed, e.g., sum, average
}
