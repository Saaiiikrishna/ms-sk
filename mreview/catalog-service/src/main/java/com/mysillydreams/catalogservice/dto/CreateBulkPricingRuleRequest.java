package com.mysillydreams.catalogservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateBulkPricingRuleRequest {

    @NotNull(message = "Item ID cannot be null")
    private UUID itemId;

    @NotNull(message = "Minimum quantity cannot be null")
    @Min(value = 1, message = "Minimum quantity must be at least 1")
    private Integer minQuantity;

    @NotNull(message = "Discount percentage cannot be null")
    @DecimalMin(value = "0.01", message = "Discount percentage must be greater than 0")
    @DecimalMax(value = "100.00", message = "Discount percentage cannot exceed 100")
    @Digits(integer = 3, fraction = 2, message = "Discount percentage can have up to 3 integer digits and 2 fraction digits")
    private BigDecimal discountPercentage;

    private Instant validFrom; // Optional, defaults to now or always if not set by service

    private Instant validTo;   // Optional, defaults to indefinite if not set by service

    @Builder.Default
    private boolean active = true;
}
