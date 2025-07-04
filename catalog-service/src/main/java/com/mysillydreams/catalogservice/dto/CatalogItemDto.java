package com.mysillydreams.catalogservice.dto;

import com.mysillydreams.catalogservice.domain.model.ItemType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CatalogItemDto {
    private UUID id;
    private UUID categoryId;
    private String categoryName; // Denormalized for convenience
    private String sku;
    private String name;
    private String description;
    private ItemType itemType;
    private BigDecimal basePrice;
    private Map<String, Object> metadata;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;

    // Optional fields that might be populated by services
    private Integer quantityOnHand; // From StockService
    private BigDecimal currentPrice; // From PricingService (could be basePrice or include temporary sales not part of history)
}
