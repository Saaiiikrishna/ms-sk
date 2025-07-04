package com.mysillydreams.catalogservice.service;

import com.mysillydreams.catalogservice.domain.model.BulkPricingRuleEntity;
import com.mysillydreams.catalogservice.domain.model.CatalogItemEntity;
import com.mysillydreams.catalogservice.domain.repository.BulkPricingRuleRepository;
import com.mysillydreams.catalogservice.domain.repository.CatalogItemRepository;
import com.mysillydreams.catalogservice.dto.BulkPricingRuleDto;
import com.mysillydreams.catalogservice.dto.CreateBulkPricingRuleRequest;
import com.mysillydreams.catalogservice.dto.PriceDetailDto;
import com.mysillydreams.catalogservice.exception.InvalidRequestException;
import com.mysillydreams.catalogservice.exception.ResourceNotFoundException;
import com.mysillydreams.catalogservice.kafka.event.BulkPricingRuleEvent;
import com.mysillydreams.catalogservice.kafka.producer.KafkaProducerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
    private final KafkaProducerService kafkaProducerService;

    @Value("${app.kafka.topic.bulk-rule-added}") // Assuming one topic for all rule events, distinguished by eventType
    private String bulkRuleEventTopic; // Example: "bulk.pricing.rule.events"

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
        publishBulkPricingRuleEvent("bulk.pricing.rule.added", savedRule);
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
    public BulkPricingRuleDto updateBulkPricingRule(UUID ruleId, CreateBulkPricingRuleRequest request) {
        log.info("Updating bulk pricing rule with ID: {}", ruleId);
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
        publishBulkPricingRuleEvent("bulk.pricing.rule.updated", updatedRule);
        log.info("Bulk pricing rule updated successfully with ID: {}", updatedRule.getId());
        return convertToDto(updatedRule);
    }

    @Transactional
    public void deleteBulkPricingRule(UUID ruleId) {
        log.info("Deleting bulk pricing rule with ID: {}", ruleId);
        BulkPricingRuleEntity rule = bulkPricingRuleRepository.findById(ruleId)
                .orElseThrow(() -> new ResourceNotFoundException("BulkPricingRule", "id", ruleId));

        bulkPricingRuleRepository.delete(rule);
        publishBulkPricingRuleEvent("bulk.pricing.rule.deleted", rule); // Send event with rule details before it's gone
        log.info("Bulk pricing rule deleted successfully with ID: {}", ruleId);
    }


    @Transactional(readOnly = true)
    public PriceDetailDto getPriceDetail(UUID itemId, int quantity) {
        log.debug("Calculating price detail for item ID: {} and quantity: {}", itemId, quantity);
        if (quantity <= 0) {
            throw new InvalidRequestException("Quantity must be positive.");
        }

        CatalogItemEntity item = catalogItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("CatalogItem", "id", itemId));

        if (!item.isActive()) {
            throw new InvalidRequestException("Item " + itemId + " is not active.");
        }

        BigDecimal basePrice = item.getBasePrice();
        BigDecimal applicableDiscountPct = BigDecimal.ZERO;
        BigDecimal discountedUnitPrice = basePrice;

        // Find the best applicable bulk pricing rule
        List<BulkPricingRuleEntity> rules = bulkPricingRuleRepository.findActiveApplicableRules(itemId, quantity, Instant.now());

        // "Best" rule could be highest discount, or highest minQty that applies.
        // The query sorts by minQuantity DESC, so the first one is the most specific for the quantity.
        // If multiple rules have the same minQuantity, we might need another tie-breaker (e.g., highest discount).
        // For now, let's assume the query's ordering is sufficient or pick highest discount among those with highest min_qty.
        Optional<BulkPricingRuleEntity> bestRule = rules.stream()
            // .filter(r -> r.getMinQuantity() <= quantity) // Already handled by findActiveApplicableRules query
            .max(Comparator.comparing(BulkPricingRuleEntity::getDiscountPercentage)); // Pick highest discount among applicable

        if (bestRule.isPresent()) {
            applicableDiscountPct = bestRule.get().getDiscountPercentage();
            BigDecimal discountFactor = BigDecimal.ONE.subtract(applicableDiscountPct.divide(new BigDecimal("100.00"), 4, RoundingMode.HALF_UP));
            discountedUnitPrice = basePrice.multiply(discountFactor).setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal totalPrice = discountedUnitPrice.multiply(new BigDecimal(quantity)).setScale(2, RoundingMode.HALF_UP);

        return PriceDetailDto.builder()
                .itemId(itemId)
                .quantity(quantity)
                .basePrice(basePrice)
                .applicableDiscountPercentage(applicableDiscountPct)
                .discountedUnitPrice(discountedUnitPrice)
                .totalPrice(totalPrice)
                .build();
    }


    private void publishBulkPricingRuleEvent(String eventType, BulkPricingRuleEntity rule) {
        BulkPricingRuleEvent event = BulkPricingRuleEvent.builder()
                .eventType(eventType)
                .ruleId(rule.getId())
                .itemId(rule.getCatalogItem().getId())
                .itemSku(rule.getCatalogItem().getSku()) // Denormalized
                .minQuantity(rule.getMinQuantity())
                .discountPercentage(rule.getDiscountPercentage())
                .validFrom(rule.getValidFrom())
                .validTo(rule.getValidTo())
                .active(rule.isActive())
                .timestamp(Instant.now())
                .build();
        // Using a generic topic from properties, or could be specific if defined
        kafkaProducerService.sendMessage(bulkRuleEventTopic, rule.getId().toString(), event);
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
