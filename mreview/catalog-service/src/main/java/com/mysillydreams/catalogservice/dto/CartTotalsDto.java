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
public class CartTotalsDto {
    private UUID cartId;
    private BigDecimal subtotal;
    private BigDecimal totalDiscountAmount;
    private BigDecimal finalTotal;
    // This DTO is a subset of CartDto, specifically focusing on financial totals.
    // The full CartDto also includes the list of items which lead to these totals.
}
