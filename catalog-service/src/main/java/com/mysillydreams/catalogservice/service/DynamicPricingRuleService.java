package com.mysillydreams.catalogservice.service;

import com.mysillydreams.catalogservice.domain.model.CatalogItemEntity;
import com.mysillydreams.catalogservice.domain.model.DynamicPricingRuleEntity;
import com.mysillydreams.catalogservice.domain.repository.CatalogItemRepository;
import com.mysillydreams.catalogservice.domain.repository.DynamicPricingRuleRepository;
import com.mysillydreams.catalogservice.dto.CreateDynamicPricingRuleRequest;
import com.mysillydreams.catalogservice.dto.DynamicPricingRuleDto;
import com.mysillydreams.catalogservice.dto.UpdateDynamicPricingRuleRequest;
import com.mysillydreams.catalogservice.exception.ResourceNotFoundException;
// TODO: Add specific validation for rule parameters based on ruleType if needed
// import com.mysillydreams.catalogservice.exception.InvalidRequestException;
import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DynamicPricingRuleService {

    private final DynamicPricingRuleRepository ruleRepository;
    private final CatalogItemRepository itemRepository;
    private final OutboxEventService outboxEventService;

    @Value("${kafka.topics.dynamicPricingRule}")
    private String dynamicRuleEventsTopic;

    // Aggregate type for outbox events related to dynamic pricing rules
    private static final String AGGREGATE_TYPE_DYNAMIC_RULE = "DynamicPricingRule";

    @Transactional
    public DynamicPricingRuleDto createRule(CreateDynamicPricingRuleRequest request, String createdBy) {
        log.info("Creating dynamic pricing rule for item ID: {}, type: {}", request.getItemId(), request.getRuleType());
        CatalogItemEntity item = itemRepository.findById(request.getItemId())
                .orElseThrow(() -> new ResourceNotFoundException("CatalogItem", "id", request.getItemId()));

        validateParameters(request.getRuleType(), request.getParameters());

        DynamicPricingRuleEntity rule = DynamicPricingRuleEntity.builder()
                .catalogItem(item)
                .ruleType(request.getRuleType())
                .parameters(request.getParameters())
                .enabled(request.isEnabled())
                .createdBy(createdBy) // Set from authenticated principal or system user
                .build();

        DynamicPricingRuleEntity savedRule = ruleRepository.save(rule);
        log.info("Dynamic pricing rule created with ID: {}", savedRule.getId());

        DynamicPricingRuleDto ruleDto = convertToDto(savedRule);
        outboxEventService.saveOutboxEvent(
                AGGREGATE_TYPE_DYNAMIC_RULE,
                savedRule.getId(),
                "dynamic.pricing.rule.created",
                dynamicRuleEventsTopic,
                ruleDto
        );
        return ruleDto;
    }

    @Transactional(readOnly = true)
    public DynamicPricingRuleDto getRuleById(UUID ruleId) {
        return ruleRepository.findById(ruleId)
                .map(this::convertToDto)
                .orElseThrow(() -> new ResourceNotFoundException("DynamicPricingRule", "id", ruleId));
    }

    @Transactional(readOnly = true)
    public List<DynamicPricingRuleDto> findAllRules() {
        return ruleRepository.findAll().stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<DynamicPricingRuleDto> findRulesByItemId(UUID itemId) {
        if (!itemRepository.existsById(itemId)) {
             throw new ResourceNotFoundException("CatalogItem", "id", itemId);
        }
        return ruleRepository.findByCatalogItemId(itemId).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Transactional
    @Retryable(value = {OptimisticLockException.class, CannotAcquireLockException.class}, maxAttempts = 3, backoff = @Backoff(delay = 100, multiplier = 2))
    public DynamicPricingRuleDto updateRule(UUID ruleId, UpdateDynamicPricingRuleRequest request, String updatedBy) {
        log.info("Updating dynamic pricing rule with ID: {}", ruleId);
        DynamicPricingRuleEntity rule = ruleRepository.findById(ruleId)
                .orElseThrow(() -> new ResourceNotFoundException("DynamicPricingRule", "id", ruleId));

        // Validate item ID consistency if provided and different (typically itemId and ruleType are not changed)
        if (!rule.getCatalogItem().getId().equals(request.getItemId())) {
            log.warn("Attempt to change item ID for dynamic pricing rule {} from {} to {}. This is usually not allowed.",
                     ruleId, rule.getCatalogItem().getId(), request.getItemId());
            // For now, let's assume it is NOT allowed to change itemID. If it is, fetch new item.
             throw new UnsupportedOperationException("Changing the item ID of an existing dynamic pricing rule is not supported.");
        }
        // Similarly for ruleType
        if (!rule.getRuleType().equals(request.getRuleType())) {
            throw new UnsupportedOperationException("Changing the rule type of an existing dynamic pricing rule is not supported.");
        }

        validateParameters(rule.getRuleType(), request.getParameters());
        rule.setParameters(request.getParameters());
        rule.setEnabled(request.getEnabled());
        rule.setCreatedBy(updatedBy); // Or have a separate updatedBy field
        rule.setUpdatedAt(Instant.now()); // Ensure updatedAt is manually set if not relying solely on @UpdateTimestamp for all cases

        DynamicPricingRuleEntity updatedRule = ruleRepository.save(rule);
        log.info("Dynamic pricing rule updated with ID: {}", updatedRule.getId());

        DynamicPricingRuleDto ruleDto = convertToDto(updatedRule);
        outboxEventService.saveOutboxEvent(
                AGGREGATE_TYPE_DYNAMIC_RULE,
                updatedRule.getId(),
                "dynamic.pricing.rule.updated",
                dynamicRuleEventsTopic,
                ruleDto
        );
        return ruleDto;
    }

    @Transactional
    public void deleteRule(UUID ruleId) {
        log.info("Deleting dynamic pricing rule with ID: {}", ruleId);
        DynamicPricingRuleEntity rule = ruleRepository.findById(ruleId)
                .orElseThrow(() -> new ResourceNotFoundException("DynamicPricingRule", "id", ruleId));

        // Convert to DTO for the payload before deleting the entity
        DynamicPricingRuleDto ruleDto = convertToDto(rule);

        outboxEventService.saveOutboxEvent(
                AGGREGATE_TYPE_DYNAMIC_RULE,
                rule.getId(),
                "dynamic.pricing.rule.deleted",
                dynamicRuleEventsTopic,
                // For delete, payload can be the DTO or just the ID map if preferred
                // Using DTO for consistency, consumer can decide what to use
                ruleDto
        );

        ruleRepository.delete(rule);
        log.info("Dynamic pricing rule deleted with ID: {}", ruleId);
    }

    private void validateParameters(String ruleType, Map<String, Object> parameters) {
        if (parameters == null) {
            throw new IllegalArgumentException("Parameters map cannot be null");
        }
        if ("PERCENT_OFF".equalsIgnoreCase(ruleType)) {
            Object pct = parameters.get("discountPercentage");
            if (!(pct instanceof Number)) {
                throw new IllegalArgumentException("discountPercentage parameter is required for PERCENT_OFF rule");
            }
            double val = ((Number) pct).doubleValue();
            if (val <= 0 || val > 100) {
                throw new IllegalArgumentException("discountPercentage must be between 0 and 100");
            }
        } else if ("TIME_OF_DAY".equalsIgnoreCase(ruleType)) {
            Object start = parameters.get("startHour");
            Object end = parameters.get("endHour");
            if (!(start instanceof Number) || !(end instanceof Number)) {
                throw new IllegalArgumentException("startHour and endHour parameters are required for TIME_OF_DAY rule");
            }
            int s = ((Number) start).intValue();
            int e = ((Number) end).intValue();
            if (s < 0 || s > 23 || e < 0 || e > 23) {
                throw new IllegalArgumentException("startHour and endHour must be between 0 and 23");
            }
        }
    }

    private DynamicPricingRuleDto convertToDto(DynamicPricingRuleEntity entity) {
        return DynamicPricingRuleDto.builder()
                .id(entity.getId())
                .itemId(entity.getCatalogItem().getId())
                .itemSku(entity.getCatalogItem().getSku()) // Denormalized
                .ruleType(entity.getRuleType())
                .parameters(entity.getParameters())
                .enabled(entity.isEnabled())
                .createdBy(entity.getCreatedBy())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .version(entity.getVersion())
                .build();
    }
}
