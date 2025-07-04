package com.mysillydreams.pricingengine.service;

import com.mysillydreams.pricingengine.domain.DynamicPricingRuleEntity;
import com.mysillydreams.pricingengine.domain.PriceOverrideEntity;
import com.mysillydreams.pricingengine.dto.MetricEvent;
import com.fasterxml.jackson.databind.ObjectMapper; // Added for converting Map to DTO
import com.mysillydreams.pricingengine.dto.AggregatedMetric;
import com.mysillydreams.pricingengine.dto.PriceUpdatedEvent;
import com.mysillydreams.pricingengine.dto.PricingComponent;
import com.mysillydreams.pricingengine.dto.rules.FlatAmountOffParams; // Added
import com.mysillydreams.pricingengine.dto.rules.ViewCountThresholdParams; // Added
// Repositories no longer needed directly
// import com.mysillydreams.pricingengine.repository.DynamicPricingRuleRepository;
// import com.mysillydreams.pricingengine.repository.PriceOverrideRepository;
// import jakarta.annotation.PostConstruct; // No longer needed
// import lombok.RequiredArgsConstructor; // Custom constructor now
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
import java.util.Optional; // For lastPublishedFinalPrice
import java.util.UUID;
// Removed unused imports for ConcurrentHashMap, Function, Collectors, PostConstruct

@Service
@Slf4j
public class DefaultPricingEngineService implements PricingEngineService {

    private final KafkaTemplate<String, PriceUpdatedEvent> priceUpdatedEventKafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${topics.priceUpdated:catalog.price.updated}")
    private String priceUpdatedTopic;

    @Value("${pricing.engine.update.threshold.percentage:0.01}") // Default 1%
    private double priceUpdateThresholdPercentage;

    // @Value("${pricing.engine.update.threshold.amount:0.00}") // Example if using amount threshold too
    // private BigDecimal priceUpdateThresholdAmount;

    // KafkaTemplate and priceUpdatedTopic removed, as publishing is now handled by the stream
    // private final KafkaTemplate<String, PriceUpdatedEvent> priceUpdatedEventKafkaTemplate;
    // @Value("${topics.priceUpdated:catalog.price.updated}")
    // private String priceUpdatedTopic;

    public DefaultPricingEngineService(
            // KafkaTemplate<String, PriceUpdatedEvent> kafkaTemplate, // Removed
            ObjectMapper objectMapper) {
        // this.priceUpdatedEventKafkaTemplate = kafkaTemplate; // Removed
        this.objectMapper = objectMapper;
    }

    @Override
    public void updateRules(List<DynamicPricingRuleDto> rules) {
        if (rules != null && !rules.isEmpty()) {
            log.info("DefaultPricingEngineService: updateRules called with {} DTOs. Updates handled by GKT.", rules.size());
        }
    }

    @Override
    public void updateOverrides(List<PriceOverrideDto> overrides) {
        if (overrides != null && !overrides.isEmpty()) {
            log.info("DefaultPricingEngineService: updateOverrides called with {} DTOs. Updates handled by GKT.", overrides.size());
        }
    }

    @Override
    public void processMetric(MetricEvent event) {
        // This method's responsibility changes. The Kafka Streams topology will perform aggregation
        // and then call a method like calculateAndPublishPrice with the item's rules, overrides,
        // and aggregated metrics directly.
        // So, this specific processMetric might become a simple pass-through or be refactored.
        // For now, let's assume the stream will call a more specific method.
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
        // For now, let's assume the stream will call a more specific method.
        if (event == null) {
            log.warn("DefaultPricingEngineService: processMetric called with null event.");
            return;
        }
        log.info("DefaultPricingEngineService: processMetric called for event ID: {}, Item ID: {}, Type: {}. This method will likely be refactored or removed as Streams directly calls calculateAndPublishPrice.",
                event.getEventId(), event.getItemId(), event.getMetricType());
        // The actual call to calculateAndPublishPrice will originate from the Kafka Streams topology
        // with aggregated metrics and joined rule/override data.
    }

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
    @Override
    public Optional<PriceUpdatedEvent> calculatePrice(EnrichedAggregatedMetric enrichedData, // Renamed and changed return type
                                                      Optional<BigDecimal> lastPublishedFinalPrice) {

        AggregatedMetric aggregatedMetrics = enrichedData.getAggregatedMetric();
        UUID itemId = aggregatedMetrics.getItemId();
        // Base price comes from ItemBasePriceEvent within enrichedData
        BigDecimal basePrice = enrichedData.getBasePrice().orElse(null);
        // Rule DTO is directly in enrichedData (assuming at most one for now for simplicity from GKT join)
        // If multiple rules, enrichedData.getRuleDto() would need to be List<DynamicPricingRuleDto>
        // For now, to match the previous structure of this method, we'll wrap it in a list if present.
        // Corrected: enrichedData now directly contains ruleDtos as a List
        List<DynamicPricingRuleDto> applicableRuleDtos = enrichedData.getRuleDtos() != null ?
                                                          enrichedData.getRuleDtos() :
                                                          Collections.emptyList();
        PriceOverrideDto activeOverrideDto = enrichedData.getOverrideDto();


        log.info("Calculating price for item ID: {} with basePrice: {}, aggregated metrics: {}, {} applicable rules, activeOverride: {}, lastPublishedPrice: {}",
                itemId, basePrice, aggregatedMetrics,
                applicableRuleDtos.size(), // This will now correctly reflect the size of the list
                activeOverrideDto != null ? activeOverrideDto.getId() : "none",
                lastPublishedFinalPrice.map(BigDecimal::toPlainString).orElse("N/A"));

        List<PricingComponent> pricingComponents = new ArrayList<>();
        Instant calculationTime = Instant.now();

        if (basePrice == null) {
            log.error("Base price is null for item ID: {}. Cannot calculate dynamic price.", itemId);
            return Optional.empty(); // Return empty if no base price
        }
        pricingComponents.add(PricingComponent.builder().componentName("BASE_PRICE").value(basePrice).description("Standard base price").build());
        BigDecimal currentCalculatedPrice = basePrice;

        // Check for active manual override (using DTO)
        if (activeOverrideDto != null && activeOverrideDto.isEnabled() &&
            (activeOverrideDto.getStartTime() == null || !activeOverrideDto.getStartTime().isAfter(calculationTime)) &&
            (activeOverrideDto.getEndTime() == null || activeOverrideDto.getEndTime().isAfter(calculationTime))) {

            currentCalculatedPrice = activeOverrideDto.getOverridePrice();
            pricingComponents.add(PricingComponent.builder()
                    .componentName("MANUAL_OVERRIDE")
                    .value(currentCalculatedPrice)
                    .ruleId(activeOverrideDto.getId().toString())
                    .description("Manual override applied.")
                    .build());
            log.info("Applied manual override ID {} for item {}. New price: {}", activeOverrideDto.getId(), itemId, currentCalculatedPrice);
        } else {
            // Evaluate dynamic pricing rules (using DTOs)
            BigDecimal totalAdjustmentFactor = BigDecimal.ZERO;
            // Note: enrichedData.getRuleDto() currently provides one rule. If an item can have multiple active rules,
            // the EnrichedAggregatedMetric.ruleDto field should be a List, and this loop would iterate it.
            // For now, using the (potentially single) rule from enrichedData.
            if (!applicableRuleDtos.isEmpty()) {
                for (DynamicPricingRuleDto ruleDto : applicableRuleDtos) { // Loop will run once if ruleDto is not null
                    if (ruleDto.isEnabled()) { // This check might be redundant if GKT only stores enabled ones
                        BigDecimal ruleAdjustment = calculateRuleAdjustment(ruleDto, aggregatedMetrics, basePrice);
                        totalAdjustmentFactor = totalAdjustmentFactor.add(ruleAdjustment);
                        pricingComponents.add(PricingComponent.builder()
                                .componentName(ruleDto.getRuleType())
                                .value(ruleAdjustment.multiply(basePrice).setScale(2, RoundingMode.HALF_UP))
                                .ruleId(ruleDto.getId().toString())
                                .description("Dynamic rule applied: " + ruleDto.getRuleType())
                                .build());
                    }
                }
            }

            if (totalAdjustmentFactor.compareTo(BigDecimal.ZERO) != 0) {
                currentCalculatedPrice = basePrice.multiply(BigDecimal.ONE.add(totalAdjustmentFactor)).setScale(2, RoundingMode.HALF_UP);
                log.info("Applied dynamic rules for item {}. Total adjustment factor: {}, New price: {}", itemId, totalAdjustmentFactor, currentCalculatedPrice);
            }
        }

        if (currentCalculatedPrice.compareTo(BigDecimal.ZERO) < 0) {
            log.warn("Calculated final price for item {} is negative ({}). Clamping to zero.", itemId, currentCalculatedPrice);
            currentCalculatedPrice = BigDecimal.ZERO;
        }

        // Price Change Threshold Check
        if (lastPublishedFinalPrice.isPresent()) {
            BigDecimal previousPrice = lastPublishedFinalPrice.get();
            BigDecimal priceChange = currentCalculatedPrice.subtract(previousPrice).abs();
            BigDecimal percentageChangeDivisor = previousPrice.compareTo(BigDecimal.ZERO) == 0 ?
                                                 (currentCalculatedPrice.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ONE : currentCalculatedPrice.abs())
                                                 : previousPrice.abs(); // Avoid division by zero if previous price was zero

            BigDecimal percentageChange = BigDecimal.ZERO;
            if (percentageChangeDivisor.compareTo(BigDecimal.ZERO) != 0) {
                 percentageChange = priceChange.divide(percentageChangeDivisor, 4, RoundingMode.HALF_UP);
            }


            log.debug("Price change check for item {}: CurrentCalcPrice={}, LastPublishedPrice={}, PriceChange={}, PercentageChange={}",
                itemId, currentCalculatedPrice, previousPrice, priceChange, percentageChange);

            // Example: Only publish if change is > 1%
            if (percentageChange.compareTo(BigDecimal.valueOf(priceUpdateThresholdPercentage)) <= 0) {
                 // AND potentially check fixed amount threshold: priceChange.compareTo(priceUpdateThresholdAmount) <= 0
                log.info("Price change for item {} ({}) is below threshold ({}%). Not publishing update.",
                        itemId, percentageChange.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP),
                        BigDecimal.valueOf(priceUpdateThresholdPercentage).multiply(BigDecimal.valueOf(100)));
                return Optional.empty(); // Skip publishing by returning empty Optional
            }
        } else {
            // No last published price.
            // Publish if the newly calculated price is different from the base price,
            // OR if there were any components applied (override or rules), indicating a calculation happened.
            // This avoids publishing a "no-change" event if calculated price is same as base and no rules/overrides applied.
            if (currentCalculatedPrice.compareTo(basePrice) == 0 && pricingComponents.size() == 1 && "BASE_PRICE".equals(pricingComponents.get(0).getComponentName())) {
                log.info("No last published price for item {} and calculated price is same as base price with no adjustments. Not publishing.", itemId);
                return Optional.empty();
            }
            log.info("No last published price for item {}. Proceeding to create PriceUpdatedEvent.", itemId);
        }

        PriceUpdatedEvent priceUpdatedEvent = PriceUpdatedEvent.builder()
                .eventId(UUID.randomUUID())
                .itemId(itemId)
                .basePrice(basePrice)
                .finalPrice(currentCalculatedPrice)
                .currency("USD") // TODO: Make currency configurable
                .timestamp(calculationTime)
                .components(pricingComponents)
                .build();

        log.info("Price calculation complete for item {}. PriceUpdatedEvent to be published by stream: {}", itemId, priceUpdatedEvent);
        return Optional.of(priceUpdatedEvent);
    }


    private BigDecimal calculateRuleAdjustment(DynamicPricingRuleDto ruleDto, AggregatedMetric aggregatedMetrics, BigDecimal basePrice) {
        log.debug("Calculating adjustment for rule ID: {}, type: {}, item ID: {}", ruleDto.getId(), ruleDto.getRuleType(), ruleDto.getItemId());
        BigDecimal adjustmentFactor = BigDecimal.ZERO;

        if ("VIEW_COUNT_THRESHOLD".equalsIgnoreCase(ruleDto.getRuleType())) {
            try {
                ViewCountThresholdParams params = objectMapper.convertValue(ruleDto.getParameters(), ViewCountThresholdParams.class);
                if (params.getThreshold() != null && params.getAdjustmentPercentage() != null &&
                    aggregatedMetrics.getMetricCount() > params.getThreshold()) {
                    adjustmentFactor = BigDecimal.valueOf(params.getAdjustmentPercentage());
                    log.info("Rule '{}' (ID: {}) triggered for item {}: metric count {} > threshold {}. Adjustment Factor: {}",
                            ruleDto.getRuleType(), ruleDto.getId(), ruleDto.getItemId(), aggregatedMetrics.getMetricCount(), params.getThreshold(), adjustmentFactor);
                }
            } catch (Exception e) {
                log.error("Error processing VIEW_COUNT_THRESHOLD rule ID {} with params {}: {}", ruleDto.getId(), ruleDto.getParameters(), e.getMessage());
            }
        } else if ("FLAT_AMOUNT_OFF".equalsIgnoreCase(ruleDto.getRuleType())) {
            try {
                FlatAmountOffParams params = objectMapper.convertValue(ruleDto.getParameters(), FlatAmountOffParams.class);
                if (params.getAmountOff() != null && basePrice.compareTo(BigDecimal.ZERO) > 0) {
                    adjustmentFactor = params.getAmountOff().divide(basePrice, 4, RoundingMode.HALF_UP).negate();
                    log.info("Rule '{}' (ID: {}) triggered for item {}: amountOff {}. Adjustment Factor: {}",
                            ruleDto.getRuleType(), ruleDto.getId(), ruleDto.getItemId(), params.getAmountOff(), adjustmentFactor);
                } else if (basePrice.compareTo(BigDecimal.ZERO) <= 0) {
                    log.warn("Cannot apply FLAT_AMOUNT_OFF rule ID {} for item {} with basePrice <= 0.", ruleDto.getId(), ruleDto.getItemId());
                }
            } catch (Exception e) {
                log.error("Error processing FLAT_AMOUNT_OFF rule ID {} with params {}: {}", ruleDto.getId(), ruleDto.getParameters(), e.getMessage());
            }
        }
        return adjustmentFactor;
    }
}
