package com.mysillydreams.catalogservice.kafka.event;

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
public class CatalogItemEvent {
    private String eventType; // e.g., "catalog.item.created", "catalog.item.updated", "catalog.item.deleted"
    private UUID itemId;
    private UUID categoryId;
    private String sku;
    private String name;
    private String description; // Consider if full description is always needed in events
    private ItemType itemType;
    private BigDecimal basePrice; // Current base price
    private Map<String, Object> metadata;
    private boolean active;
    private Instant timestamp;

    // Optional: For updates, could include old values
    // private ItemDetails oldDetails;
    // public static class ItemDetails { ... }
}
