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
    @Mock private KafkaProducerService kafkaProducerService;
    @Mock private DynamicPricingEngine dynamicPricingEngine; // Mock the new engine

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
        verify(kafkaProducerService).sendMessage(eq("bulk.rules"), eq(savedRule.getId().toString()), any(BulkPricingRuleEvent.class));
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
        verify(kafkaProducerService).sendMessage(eq("bulk.rules"), eq(ruleId.toString()), any(BulkPricingRuleEvent.class));
    }
}
