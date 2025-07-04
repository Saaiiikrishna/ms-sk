package com.mysillydreams.catalogservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockAdjustmentRequest {

    @NotNull(message = "Item ID cannot be null")
    private UUID itemId;

    @NotNull(message = "Adjustment type cannot be null")
    private StockAdjustmentType adjustmentType;

    @Min(value = 1, message = "Quantity must be at least 1 for RECEIVE/ISSUE. For ADJUSTMENT, use positive for increase, negative for decrease if allowed by service logic, otherwise use separate field or always positive.")
    // For ADJUSTMENT type, the service will interpret this.
    // If a negative quantity is needed for ADJUSTMENT, the @Min(1) might be too restrictive.
    // Let's assume quantity is always positive, and the type dictates the operation.
    // If ADJUSTMENT can be negative, we might need a different validation or field.
    // For now, sticking to positive quantity and type dictates.
    @NotNull(message = "Quantity cannot be null")
    private Integer quantity; // Always positive. Service logic handles +/- based on type.

    @Size(max = 255, message = "Reason cannot exceed 255 characters")
    private String reason; // Optional, but good for auditing adjustments

    private String referenceId; // Optional: e.g., PO number for RECEIVE, order ID for an ISSUE not handled by reservation system
}
