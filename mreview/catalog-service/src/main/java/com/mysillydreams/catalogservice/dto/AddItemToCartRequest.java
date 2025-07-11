package com.mysillydreams.catalogservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddItemToCartRequest {

    @NotNull(message = "Catalog Item ID cannot be null")
    private UUID catalogItemId;

    @NotNull(message = "Quantity cannot be null")
    @Min(value = 1, message = "Quantity must be at least 1")
    private Integer quantity;

    // userId will typically be derived from the authenticated principal in the controller, not part of request body.
    // If cartId is known and items are added to an existing cart, it could be part of path or header.
    // For simplicity, let's assume CartService handles finding/creating cart based on userId.
}
