package com.mysillydreams.pricingengine.service;

import com.mysillydreams.pricingengine.dto.EnrichedAggregatedMetric; // New DTO for input
import com.mysillydreams.pricingengine.dto.DynamicPricingRuleDto;
import com.mysillydreams.pricingengine.dto.MetricEvent;
import com.mysillydreams.pricingengine.dto.PriceOverrideDto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;


public interface PricingEngineService {

    // These methods might be deprecated or removed if all state updates are via Kafka Streams
    // and direct calls to this service for rule/override updates are not envisioned.
    void updateRules(List<DynamicPricingRuleDto> rules); // Kept as DTO based
    void updateOverrides(List<PriceOverrideDto> overrides); // Kept as DTO based

    /**
     * Processes a raw metric event. (Likely to be deprecated/removed in favor of stream processing)
     * @param event The metric event.
     */
    void processMetric(MetricEvent event);

    /**
     * Calculates and potentially publishes the dynamic price for an item based on enriched aggregated data.
     * This method is called by the Kafka Streams topology.
     * The decision to publish (e.g. based on threshold) and the actual publishing
     * might be handled by the stream itself after this method returns a calculated price event or decision.
     * For now, this method will contain the threshold logic and publishing.
     *
     * @param enrichedData Contains aggregated metrics, and joined rule, override, and base price information.
     * @param lastPublishedFinalPrice Optional last known published final price for threshold checking.
     * @return An Optional<PriceUpdatedEvent> which is present if a new price should be published,
     *         or empty if the price change is below threshold or an error occurred.
     */
    Optional<PriceUpdatedEvent> calculatePrice(EnrichedAggregatedMetric enrichedData, // Renamed for clarity
                                               Optional<BigDecimal> lastPublishedFinalPrice);
}
