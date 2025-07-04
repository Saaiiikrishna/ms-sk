package com.mysillydreams.pricingengine.dto;

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
public class ItemBasePriceEvent {
    private UUID itemId;
    private BigDecimal basePrice;
    private Instant eventTimestamp; // Timestamp of when this base price event was generated/valid
}
