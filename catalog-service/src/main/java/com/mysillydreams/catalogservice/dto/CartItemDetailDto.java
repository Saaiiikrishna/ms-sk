package com.mysillydreams.catalogservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartItemDetailDto {
    private UUID cartItemId; // ID of the CartItemEntity
    private UUID catalogItemId;
    private String sku;
    private String name;
    private String imageUrl; // Optional: Could be part of CatalogItemDto or fetched separately

    private int quantity;
    private BigDecimal originalUnitPrice; // Base price from CatalogItem
    private BigDecimal discountAppliedPerUnit; // (basePrice - discountedUnitPrice from PriceDetailDto)
    private BigDecimal finalUnitPrice; // Actual price per unit after discount (discountedUnitPrice from PriceDetailDto)
    private BigDecimal lineItemTotal; // quantity * finalUnitPrice
}
