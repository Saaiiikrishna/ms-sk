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


    public DefaultPricingEngineService(
            KafkaTemplate<String, PriceUpdatedEvent> kafkaTemplate,
            ObjectMapper objectMapper) {
        this.priceUpdatedEventKafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void updateRules(List<DynamicPricingRuleDto> rules) { // Changed to DTO
        if (rules != null && !rules.isEmpty()) {
            log.info("DefaultPricingEngineService: updateRules called with {} DTOs. Updates handled by GKT.", rules.size());
        }
    }

    @Override
    public void updateOverrides(List<PriceOverrideDto> overrides) { // Changed to DTO
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
     * Calculates and publishes the dynamic price for an item based on aggregated metrics,
     * applicable rules, and overrides. This method will be called by the Kafka Streams topology.
     *
     * @param itemId            The ID of the item.
     * @param basePrice         The base price of the item.
     * @param aggregatedMetrics The aggregated metrics for the item from a time window.
     * @param applicableRuleDtos     List of applicable dynamic pricing rule DTOs.
     * @param activeOverrideDto      An active price override DTO, if any.
     * @param lastPublishedFinalPrice Optional last known published final price for threshold checking.
     */
    @Override
    public void calculateAndPublishPrice(UUID itemId,
                                       BigDecimal basePrice,
                                       AggregatedMetric aggregatedMetrics,
                                       List<DynamicPricingRuleDto> applicableRuleDtos, // Changed to DTO
                                       PriceOverrideDto activeOverrideDto,           // Changed to DTO
                                       Optional<BigDecimal> lastPublishedFinalPrice) { // Added for threshold
        log.info("Calculating price for item ID: {} with basePrice: {}, aggregated metrics: {}, {} applicable rules, activeOverride: {}, lastPublishedPrice: {}",
                itemId, basePrice, aggregatedMetrics,
                applicableRuleDtos != null ? applicableRuleDtos.size() : 0,
                activeOverrideDto != null ? activeOverrideDto.getId() : "none",
                lastPublishedFinalPrice.map(BigDecimal::toPlainString).orElse("N/A"));

        List<PricingComponent> pricingComponents = new ArrayList<>();
        Instant calculationTime = Instant.now();

        if (basePrice == null) {
            log.error("Base price is null for item ID: {}. Cannot calculate dynamic price.", itemId);
            return;
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
            if (applicableRuleDtos != null) {
                for (DynamicPricingRuleDto ruleDto : applicableRuleDtos) {
                    if (ruleDto.isEnabled()) {
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
                return; // Skip publishing
            }
        } else {
            // No last published price, so always publish if it's different from base (or always publish first calculated price)
            // Current logic already calculates finalPrice, so if it's different from base, it's a change.
            // If currentCalculatedPrice is the same as basePrice AND no rules/overrides applied, maybe don't publish?
            // For now, if no last price, we publish the newly calculated one.
            log.info("No last published price for item {}. Publishing newly calculated price.", itemId);
        }


        PriceUpdatedEvent priceUpdatedEvent = PriceUpdatedEvent.builder()
                .eventId(UUID.randomUUID())
                .itemId(itemId)
                .basePrice(basePrice)
                .finalPrice(currentCalculatedPrice) // Use the price after threshold check
                .currency("USD") // TODO: Make currency configurable
                .timestamp(calculationTime)
                .components(pricingComponents)
                .build();

        log.info("Publishing PriceUpdatedEvent: {}", priceUpdatedEvent);
        priceUpdatedEventKafkaTemplate.send(priceUpdatedTopic, itemId.toString(), priceUpdatedEvent);
    }


    private BigDecimal calculateRuleAdjustment(DynamicPricingRuleDto ruleDto, AggregatedMetric aggregatedMetrics, BigDecimal basePrice) { // Changed to DTO
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
