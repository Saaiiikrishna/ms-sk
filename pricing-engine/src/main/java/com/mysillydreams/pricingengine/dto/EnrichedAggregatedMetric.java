package com.mysillydreams.pricingengine.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Optional; // Using Optional for joined DTOs

// This DTO will hold the aggregated metric and the joined rule, override, and base price information.
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EnrichedAggregatedMetric {
    private AggregatedMetric aggregatedMetric;
    private DynamicPricingRuleDto ruleDto; // Will be null if no matching rule
    private PriceOverrideDto overrideDto; // Will be null if no matching override
    private ItemBasePriceEvent basePriceEvent; // Will be null if no matching base price info

    // Convenience method to extract base price, providing a default or handling null
    public Optional<BigDecimal> getBasePrice() {
        return Optional.ofNullable(basePriceEvent).map(ItemBasePriceEvent::getBasePrice);
    }

    // Fluent "with" methods for builder-like construction in streams, returning a new instance
    public EnrichedAggregatedMetric withRule(DynamicPricingRuleDto ruleDto) {
        return EnrichedAggregatedMetric.builder()
                .aggregatedMetric(this.aggregatedMetric)
                .ruleDto(ruleDto)
                .overrideDto(this.overrideDto)
                .basePriceEvent(this.basePriceEvent)
                .build();
    }

    public EnrichedAggregatedMetric withOverride(PriceOverrideDto overrideDto) {
        return EnrichedAggregatedMetric.builder()
                .aggregatedMetric(this.aggregatedMetric)
                .ruleDto(this.ruleDto)
                .overrideDto(overrideDto)
                .basePriceEvent(this.basePriceEvent)
                .build();
    }

    public EnrichedAggregatedMetric withBasePriceEvent(ItemBasePriceEvent basePriceEvent) {
        return EnrichedAggregatedMetric.builder()
                .aggregatedMetric(this.aggregatedMetric)
                .ruleDto(this.ruleDto)
                .overrideDto(this.overrideDto)
                .basePriceEvent(basePriceEvent)
                .build();
    }
}
