package com.mysillydreams.catalogservice.service.pricing;

import com.mysillydreams.catalogservice.dto.PricingComponent;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Interface for a dynamic pricing engine that can provide additional
 * pricing adjustments (discounts or surcharges) based on various factors
 * like demand, user segment, promotions, etc.
 */
public interface DynamicPricingEngine {

    /**
     * Evaluates and returns a list of dynamic pricing components for a given item and quantity.
     *
     * @param itemId The ID of the catalog item.
     * @param quantity The quantity being priced.
     * @param currentPrice The current price of the item before dynamic adjustments
     *                     (e.g., after bulk discounts or manual overrides have been applied).
     *                     This allows the dynamic engine to potentially react to an already adjusted price.
     * @return A list of {@link PricingComponent}s representing dynamic adjustments.
     *         These components should have their 'amount' field correctly signed (negative for discounts).
     *         Returns an empty list if no dynamic adjustments apply.
     */
    List<PricingComponent> evaluate(UUID itemId, int quantity, BigDecimal currentPrice);
}
