package com.mysillydreams.inventoryapi.dto;

import lombok.Data;
import lombok.NoArgsConstructor; // Added for convenience if needed
import lombok.AllArgsConstructor; // Added for convenience if needed

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
// Removed UUID import as correlationId is removed

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdjustStockRequest {
    @NotBlank(message = "SKU cannot be blank")
    private String sku;

    @NotNull(message = "Delta cannot be null")
    private Integer delta;

    // correlationId and toItem() method removed as per new spec
}
