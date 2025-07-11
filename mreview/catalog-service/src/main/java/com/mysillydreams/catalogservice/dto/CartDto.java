package com.mysillydreams.catalogservice.dto;

import com.mysillydreams.catalogservice.domain.model.CartStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartDto {
    private UUID id;
    private String userId;
    private CartStatus status;
    private List<CartItemDetailDto> items; // Detailed cart items
    private BigDecimal subtotal; // Sum of (discountedUnitPrice * quantity) for all items
    private BigDecimal totalDiscountAmount; // Sum of all discounts applied
    private BigDecimal finalTotal; // Subtotal - TotalDiscountAmount (or however taxes/shipping might apply later)
    private Instant createdAt;
    private Instant updatedAt;
    private Long version;
}
