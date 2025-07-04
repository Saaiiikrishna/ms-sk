package com.mysillydreams.catalogservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// This DTO mirrors the PriceUpdatedEvent from pricing-engine
// It will be consumed by a Kafka listener in catalog-service
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PriceUpdatedEventDto { // Renamed to Dto for consistency within catalog-service
    private UUID eventId;
    private UUID itemId;
    private BigDecimal basePrice;
    private BigDecimal finalPrice;
    private String currency;
    private Instant timestamp;
    private List<PricingComponentDto> components; // Uses the local PricingComponentDto
}
