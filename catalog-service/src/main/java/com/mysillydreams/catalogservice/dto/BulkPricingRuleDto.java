package com.mysillydreams.catalogservice.dto;

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
public class BulkPricingRuleDto {
    private UUID id;
    private UUID itemId;
    private String itemSku; // Denormalized for convenience
    private Integer minQuantity;
    private BigDecimal discountPercentage; // e.g., 5.00 for 5%
    private Instant validFrom;
    private Instant validTo;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;
}
