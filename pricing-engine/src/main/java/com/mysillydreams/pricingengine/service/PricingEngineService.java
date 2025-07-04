package com.mysillydreams.pricingengine.service;

import com.mysillydreams.pricingengine.dto.AggregatedMetric;
import com.mysillydreams.pricingengine.dto.DynamicPricingRuleDto;
import com.mysillydreams.pricingengine.dto.MetricEvent;
import com.mysillydreams.pricingengine.dto.PriceOverrideDto; // Changed from Entity to DTO

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional; // For lastPublishedFinalPrice
import java.util.UUID;


public interface PricingEngineService {

    // These methods might be deprecated or removed if all state updates are via Kafka Streams
    // and direct calls to this service for rule/override updates are not envisioned.
    // For now, changing them to DTOs if they were to be used.
    void updateRules(List<DynamicPricingRuleDto> rules);
    void updateOverrides(List<PriceOverrideDto> overrides);

    /**
     * Processes a raw metric event.
     * This method's role might diminish as Kafka Streams handles aggregation
     * and directly calls calculateAndPublishPrice with richer data.
     * @param event The metric event.
     */
    void processMetric(MetricEvent event);

    /**
     * Calculates and publishes the dynamic price for an item.
     * This is the core method called by the Kafka Streams topology after data aggregation and joins.
     *
     * @param itemId                 ID of the item.
     * @param basePrice              Base price of the item.
     * @param aggregatedMetrics      Aggregated metrics for the item.
     * @param applicableRuleDtos     List of applicable dynamic pricing rule DTOs.
     * @param activeOverrideDto      An active price override DTO, if any.
     * @param lastPublishedFinalPrice Optional last known published final price for threshold checking.
     */
    void calculateAndPublishPrice(UUID itemId,
                                  BigDecimal basePrice,
                                  AggregatedMetric aggregatedMetrics,
                                  List<DynamicPricingRuleDto> applicableRuleDtos,
                                  PriceOverrideDto activeOverrideDto,
                                  Optional<BigDecimal> lastPublishedFinalPrice);
}
