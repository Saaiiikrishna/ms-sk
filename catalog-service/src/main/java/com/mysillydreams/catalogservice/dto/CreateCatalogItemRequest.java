package com.mysillydreams.catalogservice.dto;

import com.mysillydreams.catalogservice.domain.model.ItemType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateCatalogItemRequest {

    @NotNull(message = "Category ID cannot be null")
    private UUID categoryId;

    @NotBlank(message = "SKU cannot be blank")
    @Size(min = 3, max = 100, message = "SKU must be between 3 and 100 characters")
    private String sku;

    @NotBlank(message = "Item name cannot be blank")
    @Size(min = 3, max = 255, message = "Item name must be between 3 and 255 characters")
    private String name;

    private String description; // Optional

    @NotNull(message = "Item type (PRODUCT or SERVICE) must be specified")
    private ItemType itemType;

    @NotNull(message = "Base price cannot be null")
    @DecimalMin(value = "0.0", inclusive = false, message = "Base price must be greater than 0")
    @Digits(integer = 10, fraction = 2, message = "Base price can have up to 10 integer digits and 2 fraction digits")
    private BigDecimal basePrice;

    private Map<String, Object> metadata; // Optional

    @Builder.Default
    private boolean active = true;

    // For updates, this DTO can be reused.
    // Consider a separate UpdateCatalogItemRequest if fields differ significantly (e.g., SKU not updatable).
}
