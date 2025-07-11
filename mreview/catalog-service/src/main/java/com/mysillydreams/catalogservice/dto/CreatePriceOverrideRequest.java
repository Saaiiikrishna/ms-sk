package com.mysillydreams.catalogservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreatePriceOverrideRequest {

    @NotNull(message = "Item ID cannot be null")
    private UUID itemId;

    @NotNull(message = "Override price cannot be null")
    @DecimalMin(value = "0.00", inclusive = true, message = "Override price must be non-negative") // Allow 0 for "free"
    @Digits(integer = 10, fraction = 2, message = "Override price format is invalid")
    private BigDecimal overridePrice;

    // @FutureOrPresent(message = "Start time must be in the present or future") // Optional: if start time must be now or future
    private Instant startTime; // Nullable for immediate start

    // @Future(message = "End time must be in the future") // Optional: if end time must be future
    private Instant endTime;   // Nullable for no specific end time

    @Builder.Default
    private boolean enabled = true;

    // createdByUserId and createdByRole will be set by the service
}
