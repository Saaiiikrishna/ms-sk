package com.mysillydreams.catalogservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateCartItemRequest {

    // catalogItemId is usually part of the path (e.g., /cart/items/{itemId})
    // private UUID catalogItemId;

    @NotNull(message = "New quantity cannot be null")
    @Min(value = 1, message = "New quantity must be at least 1. To remove item, use DELETE endpoint.")
    private Integer newQuantity;
}
