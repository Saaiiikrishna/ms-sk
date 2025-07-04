package com.mysillydreams.pricingengine.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mysillydreams.pricingengine.domain.DynamicPricingRuleEntity;
import com.mysillydreams.pricingengine.domain.PriceOverrideEntity;
import com.mysillydreams.pricingengine.dto.DynamicPricingRuleDto;
import com.mysillydreams.pricingengine.dto.PriceOverrideDto;
import com.mysillydreams.pricingengine.repository.DynamicPricingRuleRepository;
import com.mysillydreams.pricingengine.repository.PriceOverrideRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List; // Import for List.of

@Service
@RequiredArgsConstructor
@Slf4j
public class RuleOverrideEventListener {

    private final DynamicPricingRuleRepository ruleRepository;
    private final PriceOverrideRepository overrideRepository;
    private final ObjectMapper objectMapper; // For deserializing JSON string payloads
    private final PricingEngineService pricingEngineService;
    private final MeterRegistry meterRegistry;

    private final Counter rulesConsumedCounter;
    private final Counter overridesConsumedCounter;
    // It's common to initialize counters in the constructor after MeterRegistry is injected.
    public RuleOverrideEventListener(
            DynamicPricingRuleRepository ruleRepository,
            PriceOverrideRepository overrideRepository,
            ObjectMapper objectMapper,
            PricingEngineService pricingEngineService,
            MeterRegistry meterRegistry) {
        this.ruleRepository = ruleRepository;
        this.overrideRepository = overrideRepository;
        this.objectMapper = objectMapper;
        this.pricingEngineService = pricingEngineService;
        this.meterRegistry = meterRegistry;

        this.rulesConsumedCounter = Counter.builder("pricing.engine.rules.consumed")
                .description("Number of dynamic pricing rule events consumed")
                .register(meterRegistry);
        this.overridesConsumedCounter = Counter.builder("pricing.engine.overrides.consumed")
                .description("Number of price override events consumed")
                .register(meterRegistry);
    }

    // Listener for Dynamic Pricing Rule Events
    @KafkaListener(
            topics = "${topics.dynamicRule}",
            groupId = "${kafka.consumer.group-id}", // Reuse group-id from application.yml
            containerFactory = "kafkaListenerContainerFactory" // Assuming a default factory, or specify one if defined in KafkaConfig
    )
    @Transactional // Each message processed in its own transaction
    public void onRuleEvent(@Payload String payload) {
        log.debug("Received dynamic pricing rule event payload: {}", payload);
        try {
            DynamicPricingRuleDto ruleDto = objectMapper.readValue(payload, DynamicPricingRuleDto.class);
            log.info("Deserialized dynamic pricing rule event for rule ID: {}, Item ID: {}, Type: {}", ruleDto.getId(), ruleDto.getItemId(), ruleDto.getRuleType());
            rulesConsumedCounter.increment();

            // Map DTO to Entity for upsert
            // For an "upsert" operation, we fetch by ID. If it exists, we update. If not, we create.
            // JPA's save() method behaves as an upsert if the entity has an ID and is detached,
            // or if you fetch, modify, and save.
            // Since versioning is important and comes from catalog-service, we should honor it.
            // If the event is a delete, we should delete. The DTO doesn't explicitly state delete.
            // We need to infer from eventType if it were available, or assume all events are create/update.
            // For now, assuming events imply create or update based on presence of ID.
            // A more robust solution would involve checking the event type if it's part of the message (e.g., header).

            // Let's assume for now that catalog-service sends the full DTO on create/update
            // and for delete, it might send a specific "deleted" event type or just the ID.
            // The current plan has the listener take a String payload, implying the DTO is the payload.
            // If it's a 'deleted' event, the handling would be different.
            // The DTO structure is for create/update.

            // Simple upsert logic:
            DynamicPricingRuleEntity ruleEntity = ruleRepository.findById(ruleDto.getId())
                    .orElse(new DynamicPricingRuleEntity()); // Create new if not found

            mapDtoToRuleEntity(ruleDto, ruleEntity);

            DynamicPricingRuleEntity savedEntity = ruleRepository.save(ruleEntity);
            log.info("Upserted DynamicPricingRuleEntity with ID: {}", savedEntity.getId());

            // Call pricingEngineService
            if (pricingEngineService != null) { // Defensive check, though it should be injected
                pricingEngineService.updateRules(List.of(savedEntity));
            }

        } catch (JsonProcessingException e) {
            log.error("Error deserializing dynamic pricing rule event: {}", payload, e);
            // Consider sending to a Dead Letter Topic (DLT) or other error handling
        } catch (Exception e) {
            log.error("Error processing dynamic pricing rule event for payload {}:", payload, e);
        }
    }

    // Listener for Price Override Events
    @KafkaListener(
            topics = "${topics.priceOverride}",
            groupId = "${kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void onOverrideEvent(@Payload String payload) {
        log.debug("Received price override event payload: {}", payload);
        try {
            PriceOverrideDto overrideDto = objectMapper.readValue(payload, PriceOverrideDto.class);
            log.info("Deserialized price override event for override ID: {}, Item ID: {}", overrideDto.getId(), overrideDto.getItemId());
            overridesConsumedCounter.increment();

            PriceOverrideEntity overrideEntity = overrideRepository.findById(overrideDto.getId())
                    .orElse(new PriceOverrideEntity());

            mapDtoToOverrideEntity(overrideDto, overrideEntity);

            PriceOverrideEntity savedEntity = overrideRepository.save(overrideEntity);
            log.info("Upserted PriceOverrideEntity with ID: {}", savedEntity.getId());

            // Call pricingEngineService
            if (pricingEngineService != null) {
                pricingEngineService.updateOverrides(List.of(savedEntity));
            }

        } catch (JsonProcessingException e) {
            log.error("Error deserializing price override event: {}", payload, e);
        } catch (Exception e) {
            log.error("Error processing price override event for payload {}:", payload, e);
        }
    }

    // Helper method to map DTO to DynamicPricingRuleEntity
    private void mapDtoToRuleEntity(DynamicPricingRuleDto dto, DynamicPricingRuleEntity entity) {
        entity.setId(dto.getId());
        entity.setItemId(dto.getItemId());
        entity.setRuleType(dto.getRuleType());
        entity.setParameters(dto.getParameters());
        entity.setEnabled(dto.isEnabled());
        entity.setCreatedBy(dto.getCreatedBy());
        // Timestamps (createdAt, updatedAt) and version should be managed by the source (catalog-service)
        // and just mirrored here. The DTO carries these values.
        entity.setCreatedAt(dto.getCreatedAt());
        entity.setUpdatedAt(dto.getUpdatedAt());
        entity.setVersion(dto.getVersion());
    }

    // Helper method to map DTO to PriceOverrideEntity
    private void mapDtoToOverrideEntity(PriceOverrideDto dto, PriceOverrideEntity entity) {
        entity.setId(dto.getId());
        entity.setItemId(dto.getItemId());
        entity.setOverridePrice(dto.getOverridePrice());
        entity.setStartTime(dto.getStartTime());
        entity.setEndTime(dto.getEndTime());
        entity.setEnabled(dto.isEnabled());
        entity.setCreatedByUserId(dto.getCreatedByUserId());
        entity.setCreatedByRole(dto.getCreatedByRole());
        entity.setCreatedAt(dto.getCreatedAt());
        entity.setUpdatedAt(dto.getUpdatedAt());
        entity.setVersion(dto.getVersion());
    }
}
