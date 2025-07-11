package com.mysillydreams.catalogservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockLevelDto {
    private UUID itemId;
    private String itemSku; // For convenience
    private String itemName; // For convenience
    private Integer quantityOnHand;
    private Integer reorderLevel;
    private Instant updatedAt;
    private Long version; // For optimistic locking checks if needed by client
}
