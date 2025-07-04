package com.mysillydreams.pricingengine.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PriceUpdatedEvent {
    private UUID eventId; // Unique ID for this price update event
    private UUID itemId;
    private BigDecimal basePrice; // The original base price used for calculation
    private BigDecimal finalPrice;
    private String currency; // e.g., "USD"
    private Instant timestamp; // When this price was calculated/published
    private List<PricingComponent> components; // Breakdown of how the final price was derived
}
