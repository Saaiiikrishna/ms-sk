package com.mysillydreams.catalogservice.service;

import com.mysillydreams.catalogservice.domain.model.BulkPricingRuleEntity;
import com.mysillydreams.catalogservice.domain.model.CatalogItemEntity;
import com.mysillydreams.catalogservice.domain.model.ItemType;
import com.mysillydreams.catalogservice.domain.repository.BulkPricingRuleRepository;
import com.mysillydreams.catalogservice.domain.repository.CatalogItemRepository;
import com.mysillydreams.catalogservice.dto.BulkPricingRuleDto;
import com.mysillydreams.catalogservice.dto.CreateBulkPricingRuleRequest;
import com.mysillydreams.catalogservice.dto.PriceDetailDto;
import com.mysillydreams.catalogservice.exception.InvalidRequestException;
import com.mysillydreams.catalogservice.exception.ResourceNotFoundException;
import com.mysillydreams.catalogservice.kafka.event.BulkPricingRuleEvent;
import com.mysillydreams.catalogservice.kafka.producer.KafkaProducerService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

    @InjectMocks private PricingService pricingService;

    private UUID itemId;
    private CatalogItemEntity item;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(pricingService, "bulkRuleEventTopic", "bulk.rules");
        itemId = UUID.randomUUID();
        item = CatalogItemEntity.builder()
                .id(itemId).sku("ITEM01").name("Test Item")
                .itemType(ItemType.PRODUCT).basePrice(new BigDecimal("100.00"))
                .active(true)
                .build();
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
        assertThat(result.getDiscountPercentage()).isEqualTo(new BigDecimal("5.00"));
        verify(kafkaProducerService).sendMessage(eq("bulk.rules"), eq(savedRule.getId().toString()), any(BulkPricingRuleEvent.class));
    }

    @Test
    void createBulkPricingRule_itemNotFound_throwsException() {
        CreateBulkPricingRuleRequest request = CreateBulkPricingRuleRequest.builder().itemId(UUID.randomUUID()).minQuantity(10).discountPercentage(BigDecimal.TEN).build();
        when(catalogItemRepository.findById(any(UUID.class))).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> pricingService.createBulkPricingRule(request));
    }

    @Test
    void getPriceDetail_noApplicableRules_returnsBasePrice() {
        when(catalogItemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(bulkPricingRuleRepository.findActiveApplicableRules(eq(itemId), eq(5), any(Instant.class)))
                .thenReturn(Collections.emptyList());

        PriceDetailDto result = pricingService.getPriceDetail(itemId, 5);

        assertThat(result.getBasePrice()).isEqualTo(new BigDecimal("100.00"));
        assertThat(result.getApplicableDiscountPercentage()).isEqualTo(BigDecimal.ZERO);
        assertThat(result.getDiscountedUnitPrice()).isEqualTo(new BigDecimal("100.00"));
        assertThat(result.getTotalPrice()).isEqualTo(new BigDecimal("500.00")); // 5 * 100.00
    }

    @Test
    void getPriceDetail_withApplicableRule_returnsDiscountedPrice() {
        BulkPricingRuleEntity rule = BulkPricingRuleEntity.builder()
                .minQuantity(5).discountPercentage(new BigDecimal("10.00")) // 10%
                .build();

        when(catalogItemRepository.findById(itemId)).thenReturn(Optional.of(item)); // basePrice 100.00
        when(bulkPricingRuleRepository.findActiveApplicableRules(eq(itemId), eq(10), any(Instant.class)))
                .thenReturn(List.of(rule));

        PriceDetailDto result = pricingService.getPriceDetail(itemId, 10);

        assertThat(result.getBasePrice()).isEqualTo(new BigDecimal("100.00"));
        assertThat(result.getApplicableDiscountPercentage()).isEqualTo(new BigDecimal("10.00"));
        assertThat(result.getDiscountedUnitPrice()).isEqualTo(new BigDecimal("90.00")); // 100 * (1 - 0.10)
        assertThat(result.getTotalPrice()).isEqualTo(new BigDecimal("900.00")); // 10 * 90.00
    }

    @Test
    void getPriceDetail_multipleApplicableRules_picksBestDiscount() {
        BulkPricingRuleEntity rule1_lessDiscountMoreSpecificQty = BulkPricingRuleEntity.builder() // Should not be picked if a better discount exists for same applicability
                .minQuantity(5).discountPercentage(new BigDecimal("5.00")).build(); // 5% for >=5
        BulkPricingRuleEntity rule2_moreDiscountLessSpecificQty = BulkPricingRuleEntity.builder() //This one has higher discount %
                .minQuantity(2).discountPercentage(new BigDecimal("10.00")).build(); // 10% for >=2

        // The query findActiveApplicableRules already filters by quantity and sorts by minQuantity DESC.
        // The service logic then picks the one with max discount percentage from the list.
        // If query returns rules that are ALL applicable (minQty <= requestedQty), then max discount is chosen.

        when(catalogItemRepository.findById(itemId)).thenReturn(Optional.of(item)); // basePrice 100.00
        // Assume findActiveApplicableRules correctly returns rules that meet minQty criteria
        // For quantity 5, both rules are applicable. The service should pick the one with 10% discount.
        when(bulkPricingRuleRepository.findActiveApplicableRules(eq(itemId), eq(5), any(Instant.class)))
                .thenReturn(List.of(rule1_lessDiscountMoreSpecificQty, rule2_moreDiscountLessSpecificQty));


        PriceDetailDto result = pricingService.getPriceDetail(itemId, 5);

        assertThat(result.getApplicableDiscountPercentage()).isEqualTo(new BigDecimal("10.00"));
        assertThat(result.getDiscountedUnitPrice()).isEqualTo(new BigDecimal("90.00"));
        assertThat(result.getTotalPrice()).isEqualTo(new BigDecimal("450.00")); // 5 * 90.00
    }


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
