package com.mysillydreams.inventoryapi.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.Valid;
import javax.validation.constraints.Min; // For @Min
import javax.validation.constraints.NotBlank; // For @NotBlank on String
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReservationRequestDto {
    @NotNull(message = "Order ID cannot be null")
    private UUID orderId;

    @NotEmpty(message = "Items list cannot be empty")
    @Valid // Ensures validation of LineItem objects within the list
    private List<LineItem> items; // Changed from ItemDto to LineItem

    @Data // For getters, setters, toString, equals, hashCode
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LineItem { // Renamed from ItemDto to LineItem
        @NotBlank(message = "SKU cannot be empty") // Changed from @NotEmpty
        private String sku;

        @Min(value = 1, message = "Quantity must be at least 1") // Changed from @Positive
        private int quantity;
    }
}
