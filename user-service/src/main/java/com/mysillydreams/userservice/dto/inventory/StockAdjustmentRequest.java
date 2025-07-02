package com.mysillydreams.userservice.dto.inventory;

import com.mysillydreams.userservice.domain.inventory.TransactionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Schema(description = "Request payload for adjusting stock quantity of an inventory item.")
public class StockAdjustmentRequest {

    @NotNull(message = "Transaction type cannot be null.")
    @Schema(description = "Type of stock transaction (RECEIVE, ISSUE, ADJUSTMENT).",
            example = "RECEIVE", requiredMode = Schema.RequiredMode.REQUIRED)
    private TransactionType type;

    @NotNull(message = "Quantity cannot be null.")
    @Min(value = 1, message = "Quantity must be at least 1. For negative adjustments, use type ADJUSTMENT and a positive quantity, or ensure service logic handles negative quantity with ADJUSTMENT type if allowed.")
    // The scaffold for InventoryManagementService implies quantity is always positive, and type determines +/-.
    // So, Min(1) is appropriate here.
    @Schema(description = "The quantity for the stock adjustment. Always a positive value. " +
                          "The 'type' field determines if this quantity is added, removed, or adjusted.",
            example = "10", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer quantity;

    @Schema(description = "Optional reference or reason for the adjustment.", example = "Stock count correction CY2023Q4")
    private String reason; // Optional field

    // Optional: Could add a field for expected current quantity for optimistic locking if needed.
    // private Integer expectedCurrentQuantity;
}
