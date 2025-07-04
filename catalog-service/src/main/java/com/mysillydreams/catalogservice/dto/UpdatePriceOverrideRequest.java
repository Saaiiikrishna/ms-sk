package com.mysillydreams.catalogservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdatePriceOverrideRequest {

    // Item ID is typically not updatable for an existing override.
    // Included for completeness, service layer should validate/prevent change if needed.
    @NotNull(message = "Item ID cannot be null")
    private UUID itemId;

    @NotNull(message = "Override price cannot be null")
    @DecimalMin(value = "0.00", inclusive = true, message = "Override price must be non-negative")
    @Digits(integer = 10, fraction = 2, message = "Override price format is invalid")
    private BigDecimal overridePrice;

    private Instant startTime;

    private Instant endTime;

    @NotNull(message = "Enabled flag must be provided")
    private Boolean enabled;
}
