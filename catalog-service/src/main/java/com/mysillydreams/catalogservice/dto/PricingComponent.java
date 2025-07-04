package com.mysillydreams.catalogservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PricingComponent {
    // TODO: Consider using an Enum for common codes for type-safety and easier processing.
    private String code;        // e.g., "BASE_PRICE", "MANUAL_OVERRIDE", "BULK_DISCOUNT", "FLASH_SALE", "DEMAND_SURGE"
    private String description; // Human-friendly description, e.g., "10% off for 5 or more items"
    private BigDecimal amount;  // Positive for surcharge/base price component, negative for discount.
                                // This represents the change this component makes to the price, or the component's value itself.
                                // E.g. For a base price component, amount = base price.
                                // For a discount component, amount = negative value of the discount.
}
