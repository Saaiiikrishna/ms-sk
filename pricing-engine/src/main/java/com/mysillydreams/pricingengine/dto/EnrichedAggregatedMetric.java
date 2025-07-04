package com.mysillydreams.pricingengine.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List; // Added for list of rules
import java.util.Optional;

// This DTO will hold the aggregated metric and the joined rule(s), override, and base price information.
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EnrichedAggregatedMetric {
    private AggregatedMetric aggregatedMetric;
    private List<DynamicPricingRuleDto> ruleDtos; // Changed to List
    private PriceOverrideDto overrideDto;
    private ItemBasePriceEvent basePriceEvent;

    public Optional<BigDecimal> getBasePrice() {
        return Optional.ofNullable(basePriceEvent).map(ItemBasePriceEvent::getBasePrice);
    }

    // Fluent "with" methods for builder-like construction in streams, returning a new instance
    public EnrichedAggregatedMetric withRules(List<DynamicPricingRuleDto> ruleDtos) { // Renamed and takes List
        return EnrichedAggregatedMetric.builder()
                .aggregatedMetric(this.aggregatedMetric)
                .ruleDtos(ruleDtos) // Changed to List
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
