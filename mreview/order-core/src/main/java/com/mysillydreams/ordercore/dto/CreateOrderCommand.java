package com.mysillydreams.ordercore.dto;

import com.mysillydreams.ordercore.domain.enums.OrderType;
import lombok.Builder;
import lombok.Data;
import lombok.Value; // Or @Data if mutable or with setters

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

// Using @Value for an immutable command object, or @Data/@Builder
@Value // Makes it immutable, all-args constructor, getters, equals/hashCode/toString
@Builder // Provides a builder
public class CreateOrderCommand {

    @NotNull
    UUID customerId;

    @NotNull
    OrderType orderType; // e.g., CUSTOMER, RESTOCK

    @NotNull
    @NotEmpty
    @Valid // Ensure nested validation of line items
    List<LineItemCommand> items;

    // totalAmount might be calculated by the service or validated if provided
    // For now, assume it's calculated by service based on items.
    // BigDecimal totalAmount;

    @NotNull
    @Size(min = 3, max = 3) // ISO 4217 currency code
    String currency;

    // Inner record/class for line items within the command
    @Value
    @Builder
    public static class LineItemCommand {
        @NotNull
        UUID productId; // Changed from productSku to productId for consistency with OrderItem entity

        String productSku; // Optional: can still be included

        @jakarta.validation.constraints.Min(1) // Ensure quantity is positive
        int quantity;

        @NotNull
        BigDecimal unitPrice; // Price per unit

        BigDecimal discount; // Optional discount per unit or total for line
    }
}
