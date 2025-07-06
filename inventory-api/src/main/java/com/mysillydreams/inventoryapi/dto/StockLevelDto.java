package com.mysillydreams.inventoryapi.dto;

import com.mysillydreams.inventoryapi.domain.StockLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// Removed OffsetDateTime import as updatedAt is removed from this DTO

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockLevelDto {
    private String sku;
    private int available;
    private int reserved;
    // private Instant updatedAt; // Removed as per new DTO spec

    public static StockLevelDto from(StockLevel stockLevel) {
        if (stockLevel == null) {
            return null;
        }
        return new StockLevelDto(
                stockLevel.getSku(),
                stockLevel.getAvailable(),
                stockLevel.getReserved()
                // stockLevel.getUpdatedAt() // Removed
        );
    }
}
