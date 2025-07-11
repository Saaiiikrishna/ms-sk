package com.mysillydreams.catalogservice.service;

import com.mysillydreams.catalogservice.domain.model.BulkPricingRuleEntity;
import com.mysillydreams.catalogservice.domain.model.CatalogItemEntity;
import com.mysillydreams.catalogservice.domain.model.ItemType;
import com.mysillydreams.catalogservice.domain.repository.BulkPricingRuleRepository;
import com.mysillydreams.catalogservice.domain.repository.CatalogItemRepository;
import com.mysillydreams.catalogservice.dto.BulkPricingRuleDto;
import com.mysillydreams.catalogservice.dto.*; // Import all DTOs
import com.mysillydreams.catalogservice.exception.InvalidRequestException;
import com.mysillydreams.catalogservice.exception.ResourceNotFoundException;
import com.mysillydreams.catalogservice.kafka.event.BulkPricingRuleEvent;
import com.mysillydreams.catalogservice.kafka.producer.KafkaProducerService;
import com.mysillydreams.catalogservice.service.pricing.DynamicPricingEngine; // Import new interface

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.ArrayList; // For new tests
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PricingServiceTest {

    @Mock private BulkPricingRuleRepository bulkPricingRuleRepository;
    @Mock private CatalogItemRepository catalogItemRepository;
    // @Mock private KafkaProducerService kafkaProducerService; // No longer used directly by PricingService for bulk rule events
    @Mock private OutboxEventService outboxEventService;
    @Mock private DynamicPricingEngine dynamicPricingEngine;
    @Mock private com.mysillydreams.catalogservice.domain.repository.PriceOverrideRepository priceOverrideRepository; // Added mock

    @InjectMocks private PricingService pricingService;

    private UUID itemId;
    private CatalogItemEntity item;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(pricingService, "bulkRuleEventTopic", "bulk.rules");
        itemId = UUID.randomUUID();
        item = CatalogItemEntity.builder()
                .id(itemId).sku("ITEM01").name("Test Item")
                .itemType(ItemType.PRODUCT).basePrice(new BigDecimal("100.00")) // Base price 100
                .active(true)
                .build();

        // Default behavior for dynamic pricing engine (returns no components)
        when(dynamicPricingEngine.evaluate(any(UUID.class), anyInt(), any(BigDecimal.class)))
            .thenReturn(Collections.emptyList());
    }

    @Test
    void createBulkPricingRule_success() {
        CreateBulkPricingRuleRequest request = CreateBulkPricingRuleRequest.builder()
                .itemId(itemId).minQuantity(10).discountPercentage(new BigDecimal("5.00")).active(true).build();

        when(catalogItemRepository.findById(itemId)).thenReturn(Optional.of(item));
        BulkPricingRuleEntity savedRule = BulkPricingRuleEntity.builder()
            .id(UUID.randomUUID()).catalogItem(item).minQuantity(10).discountPercentage(new BigDecimal("5.00"))
            .active(true).validFrom(Instant.now()).createdAt(Instant.now()).updatedAt(Instant.now())
            .build();
        when(bulkPricingRuleRepository.save(any(BulkPricingRuleEntity.class))).thenReturn(savedRule);

        BulkPricingRuleDto result = pricingService.createBulkPricingRule(request);

        assertThat(result).isNotNull();
        assertThat(result.getDiscountPercentage()).isEqualByComparingTo("5.00");
        verify(outboxEventService).saveOutboxEvent(eq("BulkPricingRule"), eq(savedRule.getId()), eq("bulk.rules"), eq("bulk.pricing.rule.added"), any(BulkPricingRuleEvent.class));
    }

    @Test
    void createBulkPricingRule_itemNotFound_throwsException() {
        CreateBulkPricingRuleRequest request = CreateBulkPricingRuleRequest.builder().itemId(UUID.randomUUID()).minQuantity(10).discountPercentage(BigDecimal.TEN).build();
        when(catalogItemRepository.findById(any(UUID.class))).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> pricingService.createBulkPricingRule(request));
    }

    @Test
    void getPriceDetail_noRulesOrDynamicAdjustments_returnsBasePriceAsFinal() {
        when(catalogItemRepository.findById(itemId)).thenReturn(Optional.of(item)); // Base price 100
        when(bulkPricingRuleRepository.findActiveApplicableRules(eq(itemId), eq(5), any(Instant.class)))
                .thenReturn(Collections.emptyList());
        // dynamicPricingEngine mock already set to return empty list in setUp

        PriceDetailDto result = pricingService.getPriceDetail(itemId, 5);

        assertThat(result.getItemId()).isEqualTo(itemId);
        assertThat(result.getQuantity()).isEqualTo(5);
        assertThat(result.getBasePrice()).isEqualByComparingTo("100.00");
        assertThat(result.getOverridePrice()).isNull();
        assertThat(result.getFinalUnitPrice()).isEqualByComparingTo("100.00");
        assertThat(result.getTotalPrice()).isEqualByComparingTo("500.00")); // 5 * 100.00

        assertThat(result.getComponents()).hasSize(1);
        assertThat(result.getComponents().get(0).getCode()).isEqualTo("CATALOG_BASE_PRICE");
        assertThat(result.getComponents().get(0).getAmount()).isEqualByComparingTo("100.00");
    }

    @Test
    void getPriceDetail_withBulkDiscount_appliesAndListsComponent() {
        BulkPricingRuleEntity rule = BulkPricingRuleEntity.builder()
                .minQuantity(5).discountPercentage(new BigDecimal("10.00")) // 10%
                .build();

        when(catalogItemRepository.findById(itemId)).thenReturn(Optional.of(item)); // basePrice 100.00
        when(bulkPricingRuleRepository.findActiveApplicableRules(eq(itemId), eq(10), any(Instant.class)))
                .thenReturn(List.of(rule));

        PriceDetailDto result = pricingService.getPriceDetail(itemId, 10);

        assertThat(result.getBasePrice()).isEqualByComparingTo("100.00");
        assertThat(result.getFinalUnitPrice()).isEqualByComparingTo("90.00"); // 100 * (1 - 0.10)
        assertThat(result.getTotalPrice()).isEqualByComparingTo("900.00")); // 10 * 90.00

        assertThat(result.getComponents()).hasSize(2); // BASE_PRICE + BULK_DISCOUNT
        PricingComponent bulkDiscountComp = result.getComponents().stream()
            .filter(c -> "BULK_DISCOUNT".equals(c.getCode())).findFirst().orElseThrow();
        assertThat(bulkDiscountComp.getAmount()).isEqualByComparingTo("-10.00"); // 10% of 100 is 10
    }

    @Test
    void getPriceDetail_withDynamicAdjustment_appliesAndListsComponent() {
        BigDecimal dynamicSurchargeAmount = new BigDecimal("5.00");
        PricingComponent dynamicSurcharge = PricingComponent.builder()
            .code("DEMAND_SURGE")
            .description("High demand surcharge")
            .amount(dynamicSurchargeAmount) // +5.00
            .build();

        when(catalogItemRepository.findById(itemId)).thenReturn(Optional.of(item)); // basePrice 100.00
        when(bulkPricingRuleRepository.findActiveApplicableRules(any(), anyInt(), any())).thenReturn(Collections.emptyList());
        when(dynamicPricingEngine.evaluate(eq(itemId), eq(1), eq(new BigDecimal("100.00")))) // Evaluated on price after bulk (which is base here)
            .thenReturn(List.of(dynamicSurcharge));

        PriceDetailDto result = pricingService.getPriceDetail(itemId, 1);

        assertThat(result.getFinalUnitPrice()).isEqualByComparingTo("105.00"); // 100 (base) + 5 (surcharge)
        assertThat(result.getTotalPrice()).isEqualByComparingTo("105.00"));
        assertThat(result.getComponents()).hasSize(2); // BASE_PRICE + DEMAND_SURGE
        assertThat(result.getComponents().stream().anyMatch(c -> "DEMAND_SURGE".equals(c.getCode()) && c.getAmount().compareTo(dynamicSurchargeAmount) == 0)).isTrue();
    }

    @Test
    void getPriceDetail_finalPriceClampedToZero_ifDiscountsExceedPrice() {
        // Base price 10.00
        item.setBasePrice(new BigDecimal("10.00"));
        when(catalogItemRepository.findById(itemId)).thenReturn(Optional.of(item));

        // Bulk discount of 120% (effectively -12.00)
        BulkPricingRuleEntity excessiveDiscountRule = BulkPricingRuleEntity.builder()
            .minQuantity(1).discountPercentage(new BigDecimal("120.00")).build();
        when(bulkPricingRuleRepository.findActiveApplicableRules(eq(itemId), eq(1), any(Instant.class)))
            .thenReturn(List.of(excessiveDiscountRule));

        PriceDetailDto result = pricingService.getPriceDetail(itemId, 1);

        assertThat(result.getFinalUnitPrice()).isEqualByComparingTo("0.00"); // Clamped from -2.00
        assertThat(result.getTotalPrice()).isEqualByComparingTo("0.00"));

        PricingComponent bulkDiscountComp = result.getComponents().stream()
            .filter(c -> "BULK_DISCOUNT".equals(c.getCode())).findFirst().orElseThrow();
        assertThat(bulkDiscountComp.getAmount()).isEqualByComparingTo("-12.00"); // Discount amount is still calculated fully
    }

    // TODO: Add test for manual override when that logic is implemented
    // TODO: Add test for interaction of override, bulk, and dynamic components

    @Test
    void getPriceDetail_itemNotActive_throwsException() {
        item.setActive(false);
        when(catalogItemRepository.findById(itemId)).thenReturn(Optional.of(item));
        assertThrows(InvalidRequestException.class, () -> pricingService.getPriceDetail(itemId, 1));
    }

    @Test
    void getPriceDetail_quantityZeroOrNegative_throwsException() {
        when(catalogItemRepository.findById(itemId)).thenReturn(Optional.of(item));
        assertThrows(InvalidRequestException.class, () -> pricingService.getPriceDetail(itemId, 0));
        assertThrows(InvalidRequestException.class, () -> pricingService.getPriceDetail(itemId, -1));
    }

    @Test
    void deleteBulkPricingRule_success() {
        UUID ruleId = UUID.randomUUID();
        BulkPricingRuleEntity rule = BulkPricingRuleEntity.builder().id(ruleId).catalogItem(item).minQuantity(5).discountPercentage(BigDecimal.ONE).build();
        when(bulkPricingRuleRepository.findById(ruleId)).thenReturn(Optional.of(rule));
        doNothing().when(bulkPricingRuleRepository).delete(rule);

        pricingService.deleteBulkPricingRule(ruleId);

        verify(bulkPricingRuleRepository).delete(rule);
        verify(outboxEventService).saveOutboxEvent(eq("BulkPricingRule"), eq(ruleId), eq("bulk.rules"), eq("bulk.pricing.rule.deleted"), any(BulkPricingRuleEvent.class));
    }

    @Test
    void getPriceDetail_withDynamicPrice_usesDynamicPriceAsEffectiveBase() {
        item.setDynamicPrice(new BigDecimal("90.00")); // Dynamic price is 90
        when(catalogItemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(bulkPricingRuleRepository.findActiveApplicableRules(eq(itemId), eq(1), any(Instant.class)))
                .thenReturn(Collections.emptyList());
        // dynamicPricingEngine mock (from setUp) returns empty list, so no further adjustments

        PriceDetailDto result = pricingService.getPriceDetail(itemId, 1);

        assertThat(result.getFinalUnitPrice()).isEqualByComparingTo("90.00");
        assertThat(result.getDynamicPrice()).isEqualByComparingTo("90.00"); // DTO field should reflect this
        assertThat(result.getBasePrice()).isEqualByComparingTo("100.00"); // Original base still shown
        assertThat(result.getComponents()).hasSize(1);
        assertThat(result.getComponents().get(0).getCode()).isEqualTo("DYNAMIC_PRICE_FROM_ENGINE");
        assertThat(result.getComponents().get(0).getAmount()).isEqualByComparingTo("90.00");
    }

    @Test
    void getPriceDetail_withDynamicPriceAndBulkDiscount_bulkAppliesToDynamicPrice() {
        item.setDynamicPrice(new BigDecimal("90.00")); // Dynamic price is 90
        BulkPricingRuleEntity rule = BulkPricingRuleEntity.builder()
                .minQuantity(1).discountPercentage(new BigDecimal("10.00")) // 10% off
                .build();

        when(catalogItemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(bulkPricingRuleRepository.findActiveApplicableRules(eq(itemId), eq(1), any(Instant.class)))
                .thenReturn(List.of(rule));
        // dynamicPricingEngine mock (from setUp) returns empty list

        PriceDetailDto result = pricingService.getPriceDetail(itemId, 1);

        // Effective base = 90.00. Bulk discount = 10% of 90 = 9.00. Final = 90 - 9 = 81.00
        assertThat(result.getFinalUnitPrice()).isEqualByComparingTo("81.00");
        assertThat(result.getDynamicPrice()).isEqualByComparingTo("90.00");
        assertThat(result.getBasePrice()).isEqualByComparingTo("100.00");

        assertThat(result.getComponents()).hasSize(2); // DYNAMIC_PRICE_FROM_ENGINE, BULK_DISCOUNT
        assertThat(result.getComponents().stream().anyMatch(c -> "DYNAMIC_PRICE_FROM_ENGINE".equals(c.getCode()) && c.getAmount().compareTo(new BigDecimal("90.00")) == 0)).isTrue();
        assertThat(result.getComponents().stream().anyMatch(c -> "BULK_DISCOUNT".equals(c.getCode()) && c.getAmount().compareTo(new BigDecimal("-9.00")) == 0)).isTrue();
    }

    @Test
    void getPriceDetail_noDynamicPrice_usesCatalogBasePrice() {
        // item.getDynamicPrice() is null by default
        when(catalogItemRepository.findById(itemId)).thenReturn(Optional.of(item)); // Base price 100
        when(bulkPricingRuleRepository.findActiveApplicableRules(eq(itemId), eq(1), any(Instant.class)))
                .thenReturn(Collections.emptyList());

        PriceDetailDto result = pricingService.getPriceDetail(itemId, 1);

        assertThat(result.getFinalUnitPrice()).isEqualByComparingTo("100.00");
        assertThat(result.getDynamicPrice()).isNull(); // DTO field should be null
        assertThat(result.getBasePrice()).isEqualByComparingTo("100.00");
        assertThat(result.getComponents()).hasSize(1);
        assertThat(result.getComponents().get(0).getCode()).isEqualTo("CATALOG_BASE_PRICE");
    }

    @Test
    // This test replaces the previous @Disabled one.
    void getPriceDetail_activeOverride_takesPrecedenceOverDynamicAndBase() {
        // Arrange: item with base $100, dynamic $80. Active override $90.
        item.setBasePrice(new BigDecimal("100.00"));
        item.setDynamicPrice(new BigDecimal("80.00")); // Dynamic is lower than override
        when(catalogItemRepository.findById(itemId)).thenReturn(Optional.of(item));

        com.mysillydreams.catalogservice.domain.model.PriceOverrideEntity activeOverride =
                com.mysillydreams.catalogservice.domain.model.PriceOverrideEntity.builder()
                        .id(UUID.randomUUID())
                        .catalogItem(item)
                        .overridePrice(new BigDecimal("90.00"))
                        .startTime(Instant.now().minusSeconds(3600)) // Started an hour ago
                        .endTime(Instant.now().plusSeconds(3600))   // Ends in an hour
                        .enabled(true)
                        .build();
        when(priceOverrideRepository.findCurrentActiveOverrideForItem(itemId))
                .thenReturn(Optional.of(activeOverride));

        // Ensure no other adjustments for this specific test
        when(bulkPricingRuleRepository.findActiveApplicableRules(any(), anyInt(), any()))
                .thenReturn(Collections.emptyList());
        // dynamicPricingEngine mock is already set to return empty list in setUp.

        // Act
        PriceDetailDto result = pricingService.getPriceDetail(itemId, 1);

        // Assert
        assertThat(result.getFinalUnitPrice()).isEqualByComparingTo("90.00");
        assertThat(result.getPriceSource()).isEqualTo("OVERRIDE");
        assertThat(result.getOverridePrice()).isEqualByComparingTo("90.00");
        assertThat(result.getDynamicPrice()).isEqualByComparingTo("80.00"); // DTO shows original dynamic price
        assertThat(result.getBasePrice()).isEqualByComparingTo("100.00");  // DTO shows original base price
        assertThat(result.getComponents()).hasSize(1);
        assertThat(result.getComponents().get(0).getCode()).isEqualTo("OVERRIDE");
        assertThat(result.getComponents().get(0).getAmount()).isEqualByComparingTo("90.00");
    }

    @Test
    void getPriceDetail_overrideLowerThanDynamic_overrideWins() {
        // Arrange: item with base $100, dynamic $80. Active override $70.
        item.setBasePrice(new BigDecimal("100.00"));
        item.setDynamicPrice(new BigDecimal("80.00"));
        when(catalogItemRepository.findById(itemId)).thenReturn(Optional.of(item));

        com.mysillydreams.catalogservice.domain.model.PriceOverrideEntity activeOverride =
                com.mysillydreams.catalogservice.domain.model.PriceOverrideEntity.builder()
                        .overridePrice(new BigDecimal("70.00")) // Override is lower
                        .startTime(null) // Effective immediately
                        .endTime(null)   // No expiry
                        .enabled(true)
                        .build();
        when(priceOverrideRepository.findCurrentActiveOverrideForItem(itemId))
                .thenReturn(Optional.of(activeOverride));
        when(bulkPricingRuleRepository.findActiveApplicableRules(any(), anyInt(), any())).thenReturn(Collections.emptyList());

        // Act
        PriceDetailDto result = pricingService.getPriceDetail(itemId, 1);

        // Assert
        assertThat(result.getFinalUnitPrice()).isEqualByComparingTo("70.00");
        assertThat(result.getPriceSource()).isEqualTo("OVERRIDE");
        assertThat(result.getOverridePrice()).isEqualByComparingTo("70.00");
    }

    @Test
    void getPriceDetail_overrideHigherThanDynamic_overrideWins() {
        // Arrange: item with base $100, dynamic $80. Active override $95.
        item.setBasePrice(new BigDecimal("100.00"));
        item.setDynamicPrice(new BigDecimal("80.00"));
        when(catalogItemRepository.findById(itemId)).thenReturn(Optional.of(item));

        com.mysillydreams.catalogservice.domain.model.PriceOverrideEntity activeOverride =
                com.mysillydreams.catalogservice.domain.model.PriceOverrideEntity.builder()
                        .overridePrice(new BigDecimal("95.00")) // Override is higher
                        .enabled(true)
                        .build();
        when(priceOverrideRepository.findCurrentActiveOverrideForItem(itemId))
                .thenReturn(Optional.of(activeOverride));
        when(bulkPricingRuleRepository.findActiveApplicableRules(any(), anyInt(), any())).thenReturn(Collections.emptyList());

        // Act
        PriceDetailDto result = pricingService.getPriceDetail(itemId, 1);

        // Assert
        assertThat(result.getFinalUnitPrice()).isEqualByComparingTo("95.00");
        assertThat(result.getPriceSource()).isEqualTo("OVERRIDE");
        assertThat(result.getOverridePrice()).isEqualByComparingTo("95.00");
    }

    @Test
    void getPriceDetail_expiredOverride_fallsBackToDynamicPrice() {
        // Arrange: item with base $100, dynamic $80. Expired override $70.
        item.setBasePrice(new BigDecimal("100.00"));
        item.setDynamicPrice(new BigDecimal("80.00"));
        when(catalogItemRepository.findById(itemId)).thenReturn(Optional.of(item));

        // priceOverrideRepository.findCurrentActiveOverrideForItem will return Optional.empty()
        // because the service calls it with Instant.now(), and an expired override won't be found.
        when(priceOverrideRepository.findCurrentActiveOverrideForItem(itemId))
                .thenReturn(Optional.empty()); // Simulate that no *active* override is found

        when(bulkPricingRuleRepository.findActiveApplicableRules(any(), anyInt(), any())).thenReturn(Collections.emptyList());

        // Act
        PriceDetailDto result = pricingService.getPriceDetail(itemId, 1);

        // Assert
        assertThat(result.getFinalUnitPrice()).isEqualByComparingTo("80.00");
        assertThat(result.getPriceSource()).isEqualTo("DYNAMIC");
        assertThat(result.getOverridePrice()).isNull();
        assertThat(result.getDynamicPrice()).isEqualByComparingTo("80.00");
    }

    @Test
    void getPriceDetail_futureDatedOverride_fallsBackToDynamicPrice() {
        // Arrange: item with base $100, dynamic $80. Future override $70.
        item.setBasePrice(new BigDecimal("100.00"));
        item.setDynamicPrice(new BigDecimal("80.00"));
        when(catalogItemRepository.findById(itemId)).thenReturn(Optional.of(item));

        // priceOverrideRepository.findCurrentActiveOverrideForItem will return Optional.empty()
        when(priceOverrideRepository.findCurrentActiveOverrideForItem(itemId))
                .thenReturn(Optional.empty()); // Simulate no *currently active* override

        when(bulkPricingRuleRepository.findActiveApplicableRules(any(), anyInt(), any())).thenReturn(Collections.emptyList());

        // Act
        PriceDetailDto result = pricingService.getPriceDetail(itemId, 1);

        // Assert
        assertThat(result.getFinalUnitPrice()).isEqualByComparingTo("80.00");
        assertThat(result.getPriceSource()).isEqualTo("DYNAMIC");
        assertThat(result.getOverridePrice()).isNull();
    }

    @Test
    void getPriceDetail_expiredOverride_noDynamicPrice_fallsBackToBasePrice() {
        item.setBasePrice(new BigDecimal("100.00"));
        item.setDynamicPrice(null); // No dynamic price
        when(catalogItemRepository.findById(itemId)).thenReturn(Optional.of(item));

        when(priceOverrideRepository.findCurrentActiveOverrideForItem(itemId))
                .thenReturn(Optional.empty()); // Simulate no active override (e.g., it's expired)

        when(bulkPricingRuleRepository.findActiveApplicableRules(any(), anyInt(), any())).thenReturn(Collections.emptyList());

        PriceDetailDto result = pricingService.getPriceDetail(itemId, 1);

        assertThat(result.getFinalUnitPrice()).isEqualByComparingTo("100.00");
        assertThat(result.getPriceSource()).isEqualTo("BASE");
        assertThat(result.getOverridePrice()).isNull();
        assertThat(result.getDynamicPrice()).isNull();
    }


    // Test for "No Override, No Dynamic" is effectively covered by:
    // getPriceDetail_noRulesOrDynamicAdjustments_returnsBasePriceAsFinal()
    // and getPriceDetail_noDynamicPrice_usesCatalogBasePrice()
    // We can add one more explicit one for clarity if desired.
    @Test
    void getPriceDetail_noOverride_noDynamic_fallsBackToBasePrice() {
        item.setBasePrice(new BigDecimal("100.00"));
        item.setDynamicPrice(null);
        when(catalogItemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(priceOverrideRepository.findCurrentActiveOverrideForItem(itemId)).thenReturn(Optional.empty());
        when(bulkPricingRuleRepository.findActiveApplicableRules(any(), anyInt(), any())).thenReturn(Collections.emptyList());

        PriceDetailDto result = pricingService.getPriceDetail(itemId, 1);

        assertThat(result.getFinalUnitPrice()).isEqualByComparingTo("100.00");
        assertThat(result.getPriceSource()).isEqualTo("BASE");
    }
}
