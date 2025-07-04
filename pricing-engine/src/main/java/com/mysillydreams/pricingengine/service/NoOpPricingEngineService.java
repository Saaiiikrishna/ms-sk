package com.mysillydreams.pricingengine.service;

import com.mysillydreams.pricingengine.domain.DynamicPricingRuleEntity;
import com.mysillydreams.pricingengine.domain.PriceOverrideEntity;
import com.mysillydreams.pricingengine.dto.MetricEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class NoOpPricingEngineService implements PricingEngineService {

    @Override
    public void updateRules(List<DynamicPricingRuleEntity> rules) {
        if (rules == null || rules.isEmpty()) {
            log.info("NoOpPricingEngineService: updateRules called with no rules.");
            return;
        }
        log.info("NoOpPricingEngineService: updateRules called for {} rule(s). First rule ID: {}",
                rules.size(), rules.get(0).getId());
        // TODO: Implement caching or actual processing logic in a real implementation
    }

    @Override
    public void updateOverrides(List<PriceOverrideEntity> overrides) {
        if (overrides == null || overrides.isEmpty()) {
            log.info("NoOpPricingEngineService: updateOverrides called with no overrides.");
            return;
        }
        log.info("NoOpPricingEngineService: updateOverrides called for {} override(s). First override ID: {}",
                overrides.size(), overrides.get(0).getId());
        // TODO: Implement caching or actual processing logic
    }

    @Override
    public void processMetric(MetricEvent event) {
        if (event == null) {
            log.info("NoOpPricingEngineService: processMetric called with null event.");
            return;
        }
        log.info("NoOpPricingEngineService: processMetric called for event ID: {}, Item ID: {}, Type: {}",
                event.getEventId(), event.getItemId(), event.getMetricType());
        // TODO: Implement metric aggregation and price adjustment logic
    }
}
