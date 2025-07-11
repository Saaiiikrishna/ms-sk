package com.mysillydreams.catalogservice.service;

import com.mysillydreams.catalogservice.domain.model.BulkPricingRuleEntity;
import com.mysillydreams.catalogservice.domain.model.CatalogItemEntity;
import com.mysillydreams.catalogservice.domain.repository.BulkPricingRuleRepository;
import com.mysillydreams.catalogservice.domain.repository.CatalogItemRepository;
// TODO: Add PriceOverrideRepository if/when manual overrides are implemented
import com.mysillydreams.catalogservice.dto.BulkPricingRuleDto;
import com.mysillydreams.catalogservice.dto.CreateBulkPricingRuleRequest;
import com.mysillydreams.catalogservice.dto.PriceDetailDto;
import com.mysillydreams.catalogservice.dto.PricingComponent;
import com.mysillydreams.catalogservice.exception.InvalidRequestException;
import com.mysillydreams.catalogservice.exception.ResourceNotFoundException;
import com.mysillydreams.catalogservice.kafka.event.BulkPricingRuleEvent;
// import com.mysillydreams.catalogservice.kafka.producer.KafkaProducerService; // No longer direct use
import com.mysillydreams.catalogservice.service.pricing.DynamicPricingEngine;
import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.CannotAcquireLockException; // Import for Retryable
import org.springframework.retry.annotation.Backoff; // Import for Retryable
import org.springframework.retry.annotation.Retryable; // Import for Retryable
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PricingService {

    private final BulkPricingRuleRepository bulkPricingRuleRepository;
    private final CatalogItemRepository catalogItemRepository;
    private final com.mysillydreams.catalogservice.domain.repository.PriceOverrideRepository priceOverrideRepository; // Injected
    private final DynamicPricingEngine dynamicPricingEngine;
    private final OutboxEventService outboxEventService;


    @Value("${app.kafka.topic.bulk-rule-added}") // This might need to be a more generic topic if eventType field is used, or keep specific.
    private String bulkRuleEventTopic;

    @Transactional
    public BulkPricingRuleDto createBulkPricingRule(CreateBulkPricingRuleRequest request) {
        log.info("Creating bulk pricing rule for item ID: {} with minQty: {}", request.getItemId(), request.getMinQuantity());
        CatalogItemEntity item = catalogItemRepository.findById(request.getItemId())
                .orElseThrow(() -> new ResourceNotFoundException("CatalogItem", "id", request.getItemId()));

        // Optional: Validate no overlapping rules for the exact same minQuantity and validity period, or define behavior.
        // For now, allowing multiple rules, the getPriceDetail will pick the "best" one.

        BulkPricingRuleEntity rule = BulkPricingRuleEntity.builder()
                .catalogItem(item)
                .minQuantity(request.getMinQuantity())
                .discountPercentage(request.getDiscountPercentage())
                .validFrom(request.getValidFrom() != null ? request.getValidFrom() : Instant.now()) // Default validFrom to now
                .validTo(request.getValidTo()) // validTo can be null for indefinite
                .active(request.isActive())
                .build();

        BulkPricingRuleEntity savedRule = bulkPricingRuleRepository.save(rule);
        publishBulkPricingRuleEventViaOutbox("BulkPricingRule", savedRule.getId(), bulkRuleEventTopic, "bulk.pricing.rule.added", savedRule);
        log.info("Bulk pricing rule created successfully with ID: {}", savedRule.getId());
        return convertToDto(savedRule);
    }

    @Transactional(readOnly = true)
    public BulkPricingRuleDto getBulkPricingRuleById(UUID ruleId) {
        log.debug("Fetching bulk pricing rule by ID: {}", ruleId);
        return bulkPricingRuleRepository.findById(ruleId)
                .map(this::convertToDto)
                .orElseThrow(() -> new ResourceNotFoundException("BulkPricingRule", "id", ruleId));
    }

    @Transactional(readOnly = true)
    public List<BulkPricingRuleDto> getBulkPricingRulesForItem(UUID itemId) {
        log.debug("Fetching bulk pricing rules for item ID: {}", itemId);
        if (!catalogItemRepository.existsById(itemId)) {
            throw new ResourceNotFoundException("CatalogItem", "id", itemId);
        }
        return bulkPricingRuleRepository.findByCatalogItemId(itemId).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Transactional
    @Retryable(
        value = { OptimisticLockException.class, CannotAcquireLockException.class },
        maxAttempts = 3,
        backoff = @Backoff(delay = 100, multiplier = 2)
    )
    public BulkPricingRuleDto updateBulkPricingRule(UUID ruleId, CreateBulkPricingRuleRequest request) {
        log.info("Updating bulk pricing rule with ID: {}", ruleId);
        // Re-fetch inside retryable method
        BulkPricingRuleEntity rule = bulkPricingRuleRepository.findById(ruleId)
                .orElseThrow(() -> new ResourceNotFoundException("BulkPricingRule", "id", ruleId));

        // Item ID change is typically not allowed for an existing rule. If it is, fetch new item.
        if (!rule.getCatalogItem().getId().equals(request.getItemId())) {
            // throw new InvalidRequestException("Cannot change the item ID of an existing bulk pricing rule.");
            // Or, if allowed:
            CatalogItemEntity newItem = catalogItemRepository.findById(request.getItemId())
                 .orElseThrow(() -> new ResourceNotFoundException("CatalogItem", "id", request.getItemId()));
            rule.setCatalogItem(newItem);
        }

        rule.setMinQuantity(request.getMinQuantity());
        rule.setDiscountPercentage(request.getDiscountPercentage());
        rule.setValidFrom(request.getValidFrom()); // Allow null to clear
        rule.setValidTo(request.getValidTo());     // Allow null to clear
        rule.setActive(request.isActive());

        BulkPricingRuleEntity updatedRule = bulkPricingRuleRepository.save(rule);
        publishBulkPricingRuleEventViaOutbox("BulkPricingRule", updatedRule.getId(), bulkRuleEventTopic, "bulk.pricing.rule.updated", updatedRule);
        log.info("Bulk pricing rule updated successfully with ID: {}", updatedRule.getId());
        return convertToDto(updatedRule);
    }

    @Transactional
    public void deleteBulkPricingRule(UUID ruleId) {
        log.info("Deleting bulk pricing rule with ID: {}", ruleId);
        BulkPricingRuleEntity rule = bulkPricingRuleRepository.findById(ruleId)
                .orElseThrow(() -> new ResourceNotFoundException("BulkPricingRule", "id", ruleId));

        // Publish event before actual deletion to capture state
        publishBulkPricingRuleEventViaOutbox("BulkPricingRule", rule.getId(), bulkRuleEventTopic, "bulk.pricing.rule.deleted", rule);
        bulkPricingRuleRepository.delete(rule);
        log.info("Bulk pricing rule deleted successfully with ID: {}", ruleId);
    }


    @Transactional(readOnly = true)
    // CacheKeyConstants.PRICE_DETAIL_CACHE_NAME = "priceDetail"
    // Using CacheKeyConstants helper method via SpEL:
    @org.springframework.cache.annotation.Cacheable(
            cacheNames = com.mysillydreams.catalogservice.config.CacheKeyConstants.PRICE_DETAIL_CACHE_NAME,
            key = "T(com.mysillydreams.catalogservice.config.CacheKeyConstants).getPriceDetailCacheKey(#itemId, #quantity)")
    public PriceDetailDto getPriceDetail(UUID itemId, int quantity) {
        log.info("Calculating price detail from DB/rules for item ID: {} and quantity: {}", itemId, quantity); // Log DB/rules hit
        if (quantity <= 0) {
            throw new InvalidRequestException("Quantity must be positive.");
        }

        CatalogItemEntity item = catalogItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("CatalogItem", "id", itemId));

        if (!item.isActive()) {
            // Or return a specific PriceDetailDto indicating unavailability
            throw new InvalidRequestException("Item " + itemId + " is not active and cannot be priced.");
        }

        List<PricingComponent> finalComponents = new ArrayList<>();
        BigDecimal effectiveBasePrice;
        String priceSource;
        BigDecimal actualOverridePrice = null; // To store the override price if used

        // 1. Check for active manual overrides (highest precedence)
        Optional<com.mysillydreams.catalogservice.domain.model.PriceOverrideEntity> activeManualOverrideOpt =
                priceOverrideRepository.findCurrentActiveOverrideForItem(itemId);

        if (activeManualOverrideOpt.isPresent()) {
            com.mysillydreams.catalogservice.domain.model.PriceOverrideEntity activeManualOverride = activeManualOverrideOpt.get();
            effectiveBasePrice = activeManualOverride.getOverridePrice();
            priceSource = "OVERRIDE";
            actualOverridePrice = effectiveBasePrice; // Store for DTO
            finalComponents.add(PricingComponent.builder()
                .code(priceSource)
                .description("Manual Override Applied")
                .amount(effectiveBasePrice.setScale(2, RoundingMode.HALF_UP))
                .build());
            log.debug("Using manual override price {} for item {}", effectiveBasePrice, itemId);
        } else if (item.getDynamicPrice() != null) {
            // 2. Check for dynamic price from pricing engine
            effectiveBasePrice = item.getDynamicPrice();
            priceSource = "DYNAMIC";
            finalComponents.add(PricingComponent.builder()
                .code(priceSource)
                .description("Dynamic Price Applied from Pricing Engine")
                .amount(effectiveBasePrice.setScale(2, RoundingMode.HALF_UP))
                .build());
            log.debug("Using dynamic price {} for item {}", effectiveBasePrice, itemId);
        } else {
            // 3. Fallback to catalog base price
            effectiveBasePrice = item.getBasePrice();
            priceSource = "BASE";
            finalComponents.add(PricingComponent.builder()
                .code(priceSource)
                .description("Catalog Base Price")
                .amount(effectiveBasePrice.setScale(2, RoundingMode.HALF_UP))
                .build());
            log.debug("Using catalog base price {} for item {}", effectiveBasePrice, itemId);
        }

        BigDecimal currentCalculatedPrice = effectiveBasePrice; // This is the price before further adjustments like bulk.

        // Apply Bulk Pricing Rules (calculated based on the effectiveBasePrice determined above)
        List<BulkPricingRuleEntity> applicableRules = bulkPricingRuleRepository.findActiveApplicableRules(itemId, quantity, Instant.now());
        Optional<BulkPricingRuleEntity> bestBulkRule = applicableRules.stream()
                .max(Comparator.comparing(BulkPricingRuleEntity::getDiscountPercentage));

        if (bestBulkRule.isPresent()) {
            BulkPricingRuleEntity rule = bestBulkRule.get();
            BigDecimal discountPercentage = rule.getDiscountPercentage();
            // Discount is applied on the currentCalculatedPrice (base or override)
            BigDecimal discountAmount = currentCalculatedPrice.multiply(discountPercentage)
                    .divide(new BigDecimal("100.00"), 2, RoundingMode.HALF_UP);

            finalComponents.add(PricingComponent.builder()
                    .code("BULK_DISCOUNT")
                    .description(String.format("Bulk discount: %s%% off for %d+ items", discountPercentage.stripTrailingZeros().toPlainString(), rule.getMinQuantity()))
                    .amount(discountAmount.negate().setScale(2, RoundingMode.HALF_UP)) // Negative for discount
                    .build());
            currentCalculatedPrice = currentCalculatedPrice.subtract(discountAmount); // Apply discount
            log.debug("Applied bulk discount: {} for item {} based on rule ID {}", discountAmount.negate(), itemId, rule.getId());
        }

        // Apply Dynamic Pricing Components (calculated based on currentCalculatedPrice after bulk discounts)
        List<PricingComponent> dynamicAdjustmentComponents = dynamicPricingEngine.evaluate(itemId, quantity, currentCalculatedPrice.setScale(2, RoundingMode.HALF_UP));
        if (dynamicAdjustmentComponents != null && !dynamicAdjustmentComponents.isEmpty()) {
            for(PricingComponent dynamicComp : dynamicAdjustmentComponents) {
                 finalComponents.add(dynamicComp); // Assume amount is already correctly signed
                 currentCalculatedPrice = currentCalculatedPrice.add(dynamicComp.getAmount()); // Apply dynamic adjustment
            }
            log.debug("Applied {} dynamic pricing components for item {}", dynamicAdjustmentComponents.size(), itemId);
        }

        BigDecimal finalUnitPrice = currentCalculatedPrice.setScale(2, RoundingMode.HALF_UP);

        // Ensure final unit price is not negative
        if (finalUnitPrice.compareTo(BigDecimal.ZERO) < 0) {
            log.warn("Calculated final unit price for item {} is negative ({}). Clamping to zero.", itemId, finalUnitPrice);
            finalUnitPrice = BigDecimal.ZERO;
            // If price becomes zero due to clamping, we might want to add a component indicating this.
            // Or adjust the last applied discount so total is not negative. For now, just clamp.
        }

        // Recalculate totalPrice based on the final, potentially clamped, unit price
        BigDecimal totalPrice = finalUnitPrice.multiply(new BigDecimal(quantity)).setScale(2, RoundingMode.HALF_UP);

        return PriceDetailDto.builder()
                .itemId(itemId)
                .quantity(quantity)
                .basePrice(item.getBasePrice().setScale(2, RoundingMode.HALF_UP)) // Original catalog base price
                .overridePrice(actualOverridePrice != null ? actualOverridePrice.setScale(2, RoundingMode.HALF_UP) : null)
                .dynamicPrice(priceSource.equals("DYNAMIC") ? effectiveBasePrice.setScale(2, RoundingMode.HALF_UP) : (item.getDynamicPrice() != null ? item.getDynamicPrice().setScale(2, RoundingMode.HALF_UP) : null) ) // show original dynamic if override took place
                .priceSource(priceSource)
                .components(finalComponents)
                .finalUnitPrice(finalUnitPrice)
                .totalPrice(totalPrice)
                .build();
    }

    private void publishBulkPricingRuleEventViaOutbox(String aggregateType, UUID aggregateId, String topic, String eventType, BulkPricingRuleEntity rule) {
        BulkPricingRuleEvent event = BulkPricingRuleEvent.builder()
                .eventType(eventType) // DTO's eventType field
                .ruleId(rule.getId())
                .itemId(rule.getCatalogItem().getId())
                .itemSku(rule.getCatalogItem().getSku())
                .minQuantity(rule.getMinQuantity())
                .discountPercentage(rule.getDiscountPercentage())
                .validFrom(rule.getValidFrom())
                .validTo(rule.getValidTo())
                .active(rule.isActive())
                .timestamp(Instant.now())
                .build();
        // The OutboxEventEntity.eventType will be the canonical event type string like "bulk.pricing.rule.added".
        outboxEventService.saveOutboxEvent(aggregateType, aggregateId, eventType, topic, event);
    }

    private BulkPricingRuleDto convertToDto(BulkPricingRuleEntity entity) {
        if (entity == null) return null;
        return BulkPricingRuleDto.builder()
                .id(entity.getId())
                .itemId(entity.getCatalogItem().getId())
                .itemSku(entity.getCatalogItem().getSku()) // Denormalized
                .minQuantity(entity.getMinQuantity())
                .discountPercentage(entity.getDiscountPercentage())
                .validFrom(entity.getValidFrom())
                .validTo(entity.getValidTo())
                .active(entity.isActive())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
