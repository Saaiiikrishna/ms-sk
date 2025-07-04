package com.mysillydreams.pricingengine.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mysillydreams.pricingengine.domain.DynamicPricingRuleEntity;
import com.mysillydreams.pricingengine.domain.PriceOverrideEntity;
import com.mysillydreams.pricingengine.dto.DynamicPricingRuleDto;
import com.mysillydreams.pricingengine.dto.PriceOverrideDto;
import com.mysillydreams.pricingengine.dto.ItemBasePriceEvent; // Added
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal; // Added
import java.time.Instant;    // Added
// Removed repository and PricingEngineService direct dependencies
// import com.mysillydreams.pricingengine.repository.DynamicPricingRuleRepository;
// import com.mysillydreams.pricingengine.repository.PriceOverrideRepository;
// import com.mysillydreams.pricingengine.service.PricingEngineService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
// Removed @Transactional as DB operations are removed from here
// import org.springframework.transaction.annotation.Transactional;


@Service
// @RequiredArgsConstructor // Removed as dependencies changed
@Slf4j
public class RuleOverrideEventListener {

    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    // Old topics keyed by ruleId/overrideId - will be removed or repurposed.
    // For now, removing the @Value injection for them if they are no longer primary targets.
    // @Value("${topics.internalRules}")
    // private String internalRulesTopic;
    // @Value("${topics.internalOverrides}")
    // private String internalOverridesTopic;

    @Value("${topics.internalRulesByItemId}")
    private String internalRulesByItemIdTopic;

    @Value("${topics.internalOverridesByItemId}")
    private String internalOverridesByItemIdTopic;

    @Value("${topics.internalBasePrices}")
    private String internalBasePricesTopic;

    private final Counter rulesConsumedCounter;
    private final Counter overridesConsumedCounter;

    // Hardcoded base price for now
    private static final BigDecimal PLACEHOLDER_BASE_PRICE = new BigDecimal("100.00");


    public RuleOverrideEventListener(
            ObjectMapper objectMapper,
            KafkaTemplate<String, Object> kafkaTemplate,
            MeterRegistry meterRegistry) {
        this.objectMapper = objectMapper;
        this.kafkaTemplate = kafkaTemplate;
        this.meterRegistry = meterRegistry;

        this.rulesConsumedCounter = Counter.builder("pricing.engine.rules.consumed")
                .description("Number of dynamic pricing rule events consumed from external topic")
                .register(meterRegistry);
        this.overridesConsumedCounter = Counter.builder("pricing.engine.overrides.consumed")
                .description("Number of price override events consumed from external topic")
                .register(meterRegistry);
    }

    @KafkaListener(
            topics = "${topics.dynamicRule}",
            groupId = "${kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onRuleEvent(@Payload String payload) {
        log.debug("Received dynamic pricing rule event payload: {}", payload);
        try {
            DynamicPricingRuleDto ruleDto = objectMapper.readValue(payload, DynamicPricingRuleDto.class);
            log.info("Deserialized dynamic pricing rule event for rule ID: {}, Item ID: {}", ruleDto.getId(), ruleDto.getItemId());
            rulesConsumedCounter.increment();

            // Publish to internal topic for GlobalKTable consumption
            // Key by rule ID for the GlobalKTable
            // If ruleDto.isEnabled() is false, it can be considered a "delete" or "update" for the GlobalKTable.
            // For GlobalKTable, sending the DTO itself is fine. If it's disabled, the stream processor can filter.
            // To signal a delete for a KTable, a null value (tombstone) is typically sent with the key.
            // Here, we'll send the DTO. The stream can decide if disabled means 'not active'.
            // Or, if the event specifically signals deletion, then send tombstone.
            // Assuming catalog-service events for "delete" mean the rule DTO is sent with enabled=false or similar.
            // If it's a hard delete, then the event might just be an ID, which requires different handling.
            // For now, forward the DTO.
            if (ruleDto.getId() != null) { // Ensure ID is present for keying
                 // If ruleDto.isEnabled() is false, it means the rule is no longer active.
                 // For GlobalKTable, we still publish it. The streams joining logic will filter by enabled status.
                 // To signal a delete for a KTable, a null value (tombstone) is typically sent with the key.
                // If catalog-service sends a "deleted" event type, we'd handle that by sending a tombstone here.
                // For now, assuming updates include enabled status.
                kafkaTemplate.send(internalRulesByItemIdTopic, ruleDto.getItemId().toString(), ruleDto);
                log.info("Published DynamicPricingRuleDto (RuleID: {}, ItemID: {}) to item-keyed topic: {}",
                         ruleDto.getId(), ruleDto.getItemId(), internalRulesByItemIdTopic);

                // Publish placeholder base price event (keyed by itemId)
                publishPlaceholderBasePriceEvent(ruleDto.getItemId());
            } else {
                log.warn("Received rule DTO without an ID or ItemID: {}", ruleDto);
            }

        } catch (JsonProcessingException e) {
            log.error("Error deserializing dynamic pricing rule event: {}", payload, e);
            // TODO: DLT
        } catch (Exception e) {
            log.error("Error processing/republishing dynamic pricing rule event for payload {}:", payload, e);
            // TODO: DLT
        }
    }

    @KafkaListener(
            topics = "${topics.priceOverride}",
            groupId = "${kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onOverrideEvent(@Payload String payload) {
        log.debug("Received price override event payload: {}", payload);
        try {
            PriceOverrideDto overrideDto = objectMapper.readValue(payload, PriceOverrideDto.class);
            log.info("Deserialized price override event for override ID: {}, Item ID: {}", overrideDto.getId(), overrideDto.getItemId());
            overridesConsumedCounter.increment();

            if (overrideDto.getId() != null && overrideDto.getItemId() != null) {
                // Publish to internal topic, keyed by itemId.
                kafkaTemplate.send(internalOverridesByItemIdTopic, overrideDto.getItemId().toString(), overrideDto);
                log.info("Published PriceOverrideDto (OverrideID: {}, ItemID: {}) to item-keyed topic: {}",
                         overrideDto.getId(), overrideDto.getItemId(), internalOverridesByItemIdTopic);

                // Publish placeholder base price event (keyed by itemId)
                publishPlaceholderBasePriceEvent(overrideDto.getItemId());
            } else {
                log.warn("Received override DTO without an ID or ItemID: {}", overrideDto);
            }

        } catch (JsonProcessingException e) {
            log.error("Error deserializing price override event: {}", payload, e);
            // TODO: DLT
        } catch (Exception e) {
            log.error("Error processing/republishing price override event for payload {}:", payload, e);
            // TODO: DLT
        }
    }

    private void publishPlaceholderBasePriceEvent(UUID itemId) {
        if (itemId == null) {
            log.warn("Cannot publish placeholder base price event for null itemId.");
            return;
        }
        ItemBasePriceEvent basePriceEvent = ItemBasePriceEvent.builder()
                .itemId(itemId)
                .basePrice(PLACEHOLDER_BASE_PRICE) // Using the hardcoded value
                .eventTimestamp(Instant.now())
                .build();
        try {
            kafkaTemplate.send(internalBasePricesTopic, itemId.toString(), basePriceEvent);
            log.info("Published placeholder ItemBasePriceEvent for itemId {}: {}", itemId, basePriceEvent.getBasePrice());
        } catch (Exception e) {
            log.error("Error publishing placeholder ItemBasePriceEvent for itemId {}:", itemId, e);
        }
    }

    // Helper methods mapDtoToRuleEntity and mapDtoToOverrideEntity are no longer needed here
    // as this listener now only forwards DTOs.
}
