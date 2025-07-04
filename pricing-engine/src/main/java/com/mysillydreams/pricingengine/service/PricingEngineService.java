package com.mysillydreams.pricingengine.service;

import com.mysillydreams.pricingengine.domain.DynamicPricingRuleEntity;
import com.mysillydreams.pricingengine.domain.PriceOverrideEntity;
import com.mysillydreams.pricingengine.dto.MetricEvent;

import java.util.List;

public interface PricingEngineService {

    /**
     * Called when new or updated dynamic pricing rules are received.
     * Implementations might cache these rules or update internal state.
     * @param rules The list of rules (could be a single rule if events are processed one by one).
     */
    void updateRules(List<DynamicPricingRuleEntity> rules);

    /**
     * Called when new or updated price overrides are received.
     * Implementations might cache these or update internal state.
     * @param overrides The list of overrides.
     */
    void updateOverrides(List<PriceOverrideEntity> overrides);

    /**
     * Called when a new demand metric event is received.
     * Implementations will process this metric to potentially adjust prices.
     * @param event The metric event.
     */
    void processMetric(MetricEvent event);
}
