package com.mysillydreams.catalogservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

// TODO: Consider using a proper Money library (e.g., JavaMoney) for robust currency handling.
// For now, BigDecimal is used, assuming a consistent currency context (e.g., USD).

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PriceDetailDto {
    private UUID itemId;
    private int quantity; // The quantity for which this price detail is calculated

    private BigDecimal basePrice;           // Original base price of the item from CatalogItemEntity
    private BigDecimal overridePrice;       // Nullable. If an admin/manual price override is active for this item.

    private List<PricingComponent> components; // Ordered list of all adjustments (discounts, surcharges)
                                            // The base/override price might also be represented as a component.

    private BigDecimal finalUnitPrice;      // The final calculated price per unit after all components are applied.
                                            // Calculated as: (overridePrice or basePrice) + sum of component amounts.
                                            // Ensure component amounts are signed correctly (negative for discounts).

    private BigDecimal totalPrice;          // finalUnitPrice * quantity
}
