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
public class PriceDetailDto {
    private UUID itemId;
    private Integer quantity;
    private BigDecimal basePrice; // Original base price of the item
    private BigDecimal applicableDiscountPercentage; // The discount % that was applied (e.g., 5.00 for 5%)
    private BigDecimal discountedUnitPrice; // basePrice * (1 - applicableDiscountPercentage/100)
    private BigDecimal totalPrice; // quantity * discountedUnitPrice
}
