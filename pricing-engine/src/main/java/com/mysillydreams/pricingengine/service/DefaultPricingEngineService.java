package com.mysillydreams.pricingengine.service;

import com.mysillydreams.pricingengine.domain.DynamicPricingRuleEntity;
import com.mysillydreams.pricingengine.domain.PriceOverrideEntity;
import com.mysillydreams.pricingengine.dto.MetricEvent;
import com.mysillydreams.pricingengine.dto.AggregatedMetric;
import com.mysillydreams.pricingengine.dto.PriceUpdatedEvent;
import com.mysillydreams.pricingengine.dto.PricingComponent;
import com.mysillydreams.pricingengine.repository.DynamicPricingRuleRepository;
import com.mysillydreams.pricingengine.repository.PriceOverrideRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor // For injecting repositories
public class DefaultPricingEngineService implements PricingEngineService {

    private final DynamicPricingRuleRepository ruleRepository;
    private final PriceOverrideRepository overrideRepository;
    private final KafkaTemplate<String, PriceUpdatedEvent> priceUpdatedEventKafkaTemplate;

    @Value("${topics.priceUpdated:catalog.price.updated}") // Default topic name if not in config
    private String priceUpdatedTopic;

    // In-memory caches
    // Using ConcurrentHashMap for thread-safety during reads and individual writes.
    // For bulk updates (like initial load or full refresh), consider defensive copying or synchronization.
    private final Map<UUID, DynamicPricingRuleEntity> rulesCache = new ConcurrentHashMap<>();
    private final Map<UUID, PriceOverrideEntity> overridesCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void initializeCaches() {
        log.info("Initializing caches for PricingEngineService...");
        loadAllRulesIntoCache();
        loadAllOverridesIntoCache();
        log.info("Caches initialized. Rules: {}, Overrides: {}", rulesCache.size(), overridesCache.size());
    }

    private void loadAllRulesIntoCache() {
        List<DynamicPricingRuleEntity> allRules = ruleRepository.findAll(); // Consider fetching only enabled rules
        // For a full refresh, clear and putAll. For simplicity, this assumes cache is empty or being overwritten.
        // rulesCache.clear(); // If full refresh logic is desired
        // rulesCache.putAll(allRules.stream().filter(DynamicPricingRuleEntity::isEnabled).collect(Collectors.toMap(DynamicPricingRuleEntity::getId, Function.identity())));
        // For now, just add all, assuming listener will update/remove if status changes.
        // A more robust cache would handle enabled status changes directly.
        Map<UUID, DynamicPricingRuleEntity> activeRules = allRules.stream()
            .filter(DynamicPricingRuleEntity::isEnabled) // Only cache active rules initially
            .collect(Collectors.toConcurrentMap(DynamicPricingRuleEntity::getId, Function.identity()));
        rulesCache.putAll(activeRules); // Use putAll for bulk efficiency if map is empty
        log.info("Loaded {} active dynamic pricing rules into cache.", rulesCache.size());
    }

    private void loadAllOverridesIntoCache() {
        List<PriceOverrideEntity> allOverrides = overrideRepository.findAll();
        // Similar logic for overrides, focusing on enabled and potentially active (within time window)
        // overridesCache.clear();
        // overridesCache.putAll(allOverrides.stream().filter(PriceOverrideEntity::isEnabled).collect(Collectors.toMap(PriceOverrideEntity::getId, Function.identity())));
        Map<UUID, PriceOverrideEntity> activeOverrides = allOverrides.stream()
            .filter(PriceOverrideEntity::isEnabled) // Consider time window as well for "active"
            .collect(Collectors.toConcurrentMap(PriceOverrideEntity::getId, Function.identity()));
        overridesCache.putAll(activeOverrides);
        log.info("Loaded {} active price overrides into cache.", overridesCache.size());
    }


    @Override
    public void updateRules(List<DynamicPricingRuleEntity> rules) {
        if (rules == null || rules.isEmpty()) {
            log.debug("DefaultPricingEngineService: updateRules called with no rules.");
            return;
        }
        log.info("DefaultPricingEngineService: Updating {} rule(s) in cache.", rules.size());
        for (DynamicPricingRuleEntity rule : rules) {
            if (rule.isEnabled()) { // Assuming DTO/Entity from event reflects current state
                rulesCache.put(rule.getId(), rule);
                log.debug("Cached/Updated rule: {}", rule.getId());
            } else {
                // If rule is disabled or should be considered deleted based on event
                rulesCache.remove(rule.getId());
                log.debug("Removed rule from cache due to disabled status or deletion: {}", rule.getId());
            }
        }
        // TODO: Further logic if a rule is deleted (how to detect from event if not just 'enabled=false'?)
        // For now, relies on the event source to send an update with enabled=false for deletions/disabling.
    }

    @Override
    public void updateOverrides(List<PriceOverrideEntity> overrides) {
        if (overrides == null || overrides.isEmpty()) {
            log.debug("DefaultPricingEngineService: updateOverrides called with no overrides.");
            return;
        }
        log.info("DefaultPricingEngineService: Updating {} override(s) in cache.", overrides.size());
        for (PriceOverrideEntity override : overrides) {
            // Similar logic: update if enabled, remove if not.
            // Also consider time validity for overrides if they should be actively removed from cache when expired.
            if (override.isEnabled()) { // And potentially check if override.getEndTime().isAfter(Instant.now())
                overridesCache.put(override.getId(), override);
                log.debug("Cached/Updated override: {}", override.getId());
            } else {
                overridesCache.remove(override.getId());
                log.debug("Removed override from cache due to disabled status or deletion: {}", override.getId());
            }
        }
    }

    @Override
    public void processMetric(MetricEvent event) {
        if (event == null) {
            log.warn("DefaultPricingEngineService: processMetric called with null event.");
            return;
        }
        log.info("DefaultPricingEngineService: Processing metric for event ID: {}, Item ID: {}, Type: {}",
                event.getEventId(), event.getItemId(), event.getMetricType());
        // TODO: Implement metric aggregation and price adjustment logic using caches
        // This is where Step 3.3 (Implement Pricing Logic) will primarily occur.
        // For now, this method is a placeholder for that logic.

        // This method would be called by the Kafka Streams processor (or a scheduled task)
        // after metrics for an item have been aggregated for a window.
        // For now, we'll call it directly from processMetric for simplicity of stubbing.
        if (event != null && event.getItemId() != null) {
            AggregatedMetric simpleAggregatedMetric = AggregatedMetric.builder()
                    .itemId(event.getItemId())
                    .metricCount(1L) // Placeholder: actual aggregation would provide a meaningful count
                    .windowStartTimestamp(event.getTimestamp() != null ? event.getTimestamp().toEpochMilli() : Instant.now().toEpochMilli())
                    .windowEndTimestamp(Instant.now().toEpochMilli())
                    .build();
            calculateAndPublishPrice(event.getItemId(), simpleAggregatedMetric);
        }
    }

    // This is the core pricing logic method
    public void calculateAndPublishPrice(UUID itemId, AggregatedMetric aggregatedMetrics) {
        log.info("Calculating price for item ID: {} with aggregated metrics: {}", itemId, aggregatedMetrics);
        List<PricingComponent> pricingComponents = new ArrayList<>();
        Instant calculationTime = Instant.now();

        // 1. Fetch Base Price (Placeholder)
        BigDecimal basePrice = fetchBasePrice(itemId);
        if (basePrice == null) {
            log.error("Base price not found for item ID: {}. Cannot calculate dynamic price.", itemId);
            return; // Or handle error appropriately
        }
        pricingComponents.add(PricingComponent.builder().componentName("BASE_PRICE").value(basePrice).description("Standard base price").build());
        BigDecimal finalPrice = basePrice;

        // 2. Check for active manual overrides
        PriceOverrideEntity activeOverride = findActiveOverride(itemId, calculationTime);
        if (activeOverride != null) {
            finalPrice = activeOverride.getOverridePrice();
            pricingComponents.add(PricingComponent.builder()
                    .componentName("MANUAL_OVERRIDE")
                    .value(finalPrice)
                    .ruleId(activeOverride.getId().toString())
                    .description("Manual override applied.")
                    .build());
            log.info("Applied manual override for item {}. New price: {}", itemId, finalPrice);
        } else {
            // 3. Evaluate dynamic pricing rules
            BigDecimal totalAdjustmentFactor = BigDecimal.ZERO; // e.g. sum of +0.10, -0.05

            List<DynamicPricingRuleEntity> applicableRules = findApplicableRules(itemId, aggregatedMetrics);
            for (DynamicPricingRuleEntity rule : applicableRules) {
                BigDecimal ruleAdjustment = calculateRuleAdjustment(rule, aggregatedMetrics, basePrice);
                totalAdjustmentFactor = totalAdjustmentFactor.add(ruleAdjustment);
                pricingComponents.add(PricingComponent.builder()
                        .componentName(rule.getRuleType()) // Or a more descriptive name
                        .value(ruleAdjustment.multiply(basePrice).setScale(2, RoundingMode.HALF_UP)) // Value of the adjustment amount
                        .ruleId(rule.getId().toString())
                        .description("Dynamic rule applied: " + rule.getRuleType())
                        .build());
            }

            if (!applicableRules.isEmpty()) {
                 // Apply factor: finalPrice = basePrice * (1 + totalAdjustmentFactor)
                finalPrice = basePrice.multiply(BigDecimal.ONE.add(totalAdjustmentFactor)).setScale(2, RoundingMode.HALF_UP);
                log.info("Applied dynamic rules for item {}. Total adjustment factor: {}, New price: {}", itemId, totalAdjustmentFactor, finalPrice);
            }
        }

        // Ensure price is not negative (basic sanity check)
        if (finalPrice.compareTo(BigDecimal.ZERO) < 0) {
            log.warn("Calculated final price for item {} is negative ({}). Clamping to zero.", itemId, finalPrice);
            finalPrice = BigDecimal.ZERO;
        }

        // 4. Publish PriceUpdatedEvent (Step 3.4)
        PriceUpdatedEvent priceUpdatedEvent = PriceUpdatedEvent.builder()
                .eventId(UUID.randomUUID())
                .itemId(itemId)
                .basePrice(basePrice)
                .finalPrice(finalPrice)
                .currency("USD") // TODO: Make currency configurable or part of item data
                .timestamp(calculationTime)
                .components(pricingComponents)
                .build();

        log.info("Publishing PriceUpdatedEvent: {}", priceUpdatedEvent);
        // This call will be fully implemented in Step 3.4
        priceUpdatedEventKafkaTemplate.send(priceUpdatedTopic, itemId.toString(), priceUpdatedEvent);
    }

    private BigDecimal fetchBasePrice(UUID itemId) {
        // TODO: Implement actual base price fetching mechanism.
        // Options:
        // 1. Cache from a dedicated "item_master_data" topic consumed from CatalogService.
        // 2. REST API call to CatalogService (adds synchronous dependency).
        // 3. Assume base price is part of DynamicPricingRuleEntity or PriceOverrideEntity if enriched by CatalogService.
        log.warn("Fetching base price for item {} using a HARDCODED DEFAULT. THIS MUST BE REPLACED.", itemId);
        return BigDecimal.valueOf(100.00); // Placeholder
    }

    private PriceOverrideEntity findActiveOverride(UUID itemId, Instant currentTime) {
        return overridesCache.values().stream()
                .filter(o -> o.getItemId().equals(itemId) && o.isEnabled())
                .filter(o -> (o.getStartTime() == null || !o.getStartTime().isAfter(currentTime)) &&
                             (o.getEndTime() == null || o.getEndTime().isAfter(currentTime)))
                .findFirst()
                .orElse(null);
    }

    private List<DynamicPricingRuleEntity> findApplicableRules(UUID itemId, AggregatedMetric aggregatedMetrics) {
        // Filter rules from cache that apply to this item and are enabled.
        // Further filtering based on ruleType and aggregatedMetrics would happen here or in calculateRuleAdjustment.
        return rulesCache.values().stream()
                .filter(r -> r.getItemId().equals(itemId) && r.isEnabled())
                .collect(Collectors.toList());
    }

    private BigDecimal calculateRuleAdjustment(DynamicPricingRuleEntity rule, AggregatedMetric aggregatedMetrics, BigDecimal basePrice) {
        // This is a placeholder for actual rule logic.
        // Each ruleType would have its own calculation based on rule.getParameters() and aggregatedMetrics.
        log.debug("Calculating adjustment for rule ID: {}, type: {}, item ID: {}", rule.getId(), rule.getRuleType(), rule.getItemId());
        BigDecimal adjustmentFactor = BigDecimal.ZERO; // Default: no change

        // Example: Simple rule based on metric count (e.g., number of views)
        if ("VIEW_COUNT_THRESHOLD".equalsIgnoreCase(rule.getRuleType())) {
            // Assume parameters: {"threshold": 100, "adjustmentPercentage": 0.10} (meaning +10%)
            try {
                Long threshold = ((Number) rule.getParameters().getOrDefault("threshold", Long.MAX_VALUE)).longValue();
                Double adjustmentPercentage = ((Number) rule.getParameters().getOrDefault("adjustmentPercentage", 0.0)).doubleValue();

                if (aggregatedMetrics.getMetricCount() > threshold) {
                    adjustmentFactor = BigDecimal.valueOf(adjustmentPercentage);
                    log.info("Rule '{}' triggered for item {}: metric count {} > threshold {}. Adjustment: {}%",
                            rule.getRuleType(), rule.getItemId(), aggregatedMetrics.getMetricCount(), threshold, adjustmentFactor.multiply(BigDecimal.valueOf(100)));
                }
            } catch (Exception e) {
                log.error("Error parsing parameters for rule ID {}: {}", rule.getId(), rule.getParameters(), e);
            }
        } else if ("FLAT_AMOUNT_OFF".equalsIgnoreCase(rule.getRuleType())){
             try {
                Double amountOff = ((Number) rule.getParameters().getOrDefault("amountOff", 0.0)).doubleValue();
                if (basePrice.compareTo(BigDecimal.ZERO) > 0) { // Avoid division by zero if basePrice is 0
                    adjustmentFactor = BigDecimal.valueOf(amountOff).divide(basePrice, 4, RoundingMode.HALF_UP).negate();
                     log.info("Rule '{}' triggered for item {}: amountOff {}. AdjustmentFactor: {}%",
                            rule.getRuleType(), rule.getItemId(), amountOff, adjustmentFactor.multiply(BigDecimal.valueOf(100)));
                }
            } catch (Exception e) {
                log.error("Error parsing parameters for FLAT_AMOUNT_OFF rule ID {}: {}", rule.getId(), rule.getParameters(), e);
            }
        }
        // Add more rule types here...

        return adjustmentFactor;
    }

    // Methods to access caches (potentially for Kafka Streams processor or other components)
    // These should return copies or unmodifiable views if direct modification outside this service is not desired.
    public Map<UUID, DynamicPricingRuleEntity> getRulesCacheView() {
        return new ConcurrentHashMap<>(rulesCache); // Return a copy for safety
    }

    public Map<UUID, PriceOverrideEntity> getOverridesCacheView() {
        return new ConcurrentHashMap<>(overridesCache); // Return a copy
    }
}
