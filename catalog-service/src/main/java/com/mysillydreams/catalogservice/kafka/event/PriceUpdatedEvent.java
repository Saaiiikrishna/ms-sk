package com.mysillydreams.catalogservice.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PriceUpdatedEvent {
    // eventType will be "catalog.price.updated" implicitly by topic, or can be added if using a generic topic
    private UUID itemId;
    private String sku; // For easier identification by consumers
    private BigDecimal oldPrice;
    private BigDecimal newPrice;
    private Instant timestamp; // When the price change became effective or was recorded
}
