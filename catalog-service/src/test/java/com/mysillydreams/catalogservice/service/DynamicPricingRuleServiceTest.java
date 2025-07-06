package com.mysillydreams.catalogservice.service;

import com.mysillydreams.catalogservice.domain.model.CatalogItemEntity;
import com.mysillydreams.catalogservice.domain.model.DynamicPricingRuleEntity;
import com.mysillydreams.catalogservice.domain.repository.CatalogItemRepository;
import com.mysillydreams.catalogservice.domain.repository.DynamicPricingRuleRepository;
import com.mysillydreams.catalogservice.dto.CreateDynamicPricingRuleRequest;
import com.mysillydreams.catalogservice.dto.DynamicPricingRuleDto;
import com.mysillydreams.catalogservice.dto.UpdateDynamicPricingRuleRequest;
import com.mysillydreams.catalogservice.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DynamicPricingRuleServiceTest {

    @Mock
    private DynamicPricingRuleRepository ruleRepository;

    @Mock
    private CatalogItemRepository itemRepository;

    @Mock
    private OutboxEventService outboxEventService;

    @InjectMocks
    private DynamicPricingRuleService dynamicPricingRuleService;

    private final String testTopic = "test-dynamic-rule-events";
    private CatalogItemEntity catalogItem;
    private DynamicPricingRuleEntity ruleEntity;
    private UUID catalogItemId = UUID.randomUUID();
    private UUID ruleId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        // Set the topic name using ReflectionTestUtils as @Value won't work here
        ReflectionTestUtils.setField(dynamicPricingRuleService, "dynamicRuleEventsTopic", testTopic);

        catalogItem = CatalogItemEntity.builder()
                .id(catalogItemId)
                .sku("ITEM001")
                .basePrice(BigDecimal.TEN)
                .build();

        Map<String, Object> params = new HashMap<>();
        params.put("discount", 10.0);

        ruleEntity = DynamicPricingRuleEntity.builder()
                .id(ruleId)
                .catalogItem(catalogItem)
                .ruleType("FLAT_DISCOUNT")
                .parameters(params)
                .enabled(true)
                .createdBy("user1")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .version(0L)
                .build();
    }

    @Test
    void createRule_whenItemExists_shouldSaveRuleAndOutboxEvent() {
        // Arrange
        Map<String, Object> params = new HashMap<>();
        params.put("discountPercentage", 15.0);
        CreateDynamicPricingRuleRequest request = CreateDynamicPricingRuleRequest.builder()
                .itemId(catalogItemId)
                .ruleType("PERCENT_OFF")
                .parameters(params)
                .enabled(true)
                .build();
        String creator = "test-user";

        when(itemRepository.findById(catalogItemId)).thenReturn(Optional.of(catalogItem));
        when(ruleRepository.save(any(DynamicPricingRuleEntity.class))).thenAnswer(invocation -> {
            DynamicPricingRuleEntity savedRule = invocation.getArgument(0);
            // Simulate JPA behavior: assign ID and version if new, update timestamps
            ReflectionTestUtils.setField(savedRule, "id", ruleId); // Assign a predictable ID
            ReflectionTestUtils.setField(savedRule, "version", 0L);
            ReflectionTestUtils.setField(savedRule, "createdAt", Instant.now());
            ReflectionTestUtils.setField(savedRule, "updatedAt", Instant.now());
            return savedRule;
        });

        // Act
        DynamicPricingRuleDto resultDto = dynamicPricingRuleService.createRule(request, creator);

        // Assert
        assertThat(resultDto).isNotNull();
        assertThat(resultDto.getId()).isEqualTo(ruleId);
        assertThat(resultDto.getItemId()).isEqualTo(catalogItemId);
        assertThat(resultDto.getRuleType()).isEqualTo("PERCENT_OFF");

        verify(itemRepository).findById(catalogItemId);
        verify(ruleRepository).save(any(DynamicPricingRuleEntity.class));

        ArgumentCaptor<DynamicPricingRuleDto> dtoCaptor = ArgumentCaptor.forClass(DynamicPricingRuleDto.class);
        verify(outboxEventService).saveOutboxEvent(
                eq("DynamicPricingRule"),
                eq(ruleId),
                eq("dynamic.pricing.rule.created"),
                eq(testTopic),
                dtoCaptor.capture()
        );
        assertThat(dtoCaptor.getValue().getId()).isEqualTo(ruleId);
        assertThat(dtoCaptor.getValue().getParameters()).isEqualTo(params);
    }

    @Test
    void createRule_whenItemNotFound_shouldThrowResourceNotFound() {
        // Arrange
        CreateDynamicPricingRuleRequest request = CreateDynamicPricingRuleRequest.builder().itemId(catalogItemId).build();
        when(itemRepository.findById(catalogItemId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> dynamicPricingRuleService.createRule(request, "user"));
        verify(ruleRepository, never()).save(any());
        verify(outboxEventService, never()).saveOutboxEvent(any(), any(), any(), any(), any());
    }

    @Test
    void createRule_missingPercentOffParam_throwsIllegalArgumentException() {
        Map<String, Object> params = new HashMap<>();
        CreateDynamicPricingRuleRequest request = CreateDynamicPricingRuleRequest.builder()
                .itemId(catalogItemId)
                .ruleType("PERCENT_OFF")
                .parameters(params)
                .enabled(true)
                .build();

        when(itemRepository.findById(catalogItemId)).thenReturn(Optional.of(catalogItem));

        assertThrows(IllegalArgumentException.class, () -> dynamicPricingRuleService.createRule(request, "user"));
        verify(ruleRepository, never()).save(any());
    }

    @Test
    void updateRule_whenRuleExists_shouldUpdateAndSaveOutboxEvent() {
        // Arrange
        Map<String, Object> updatedParams = new HashMap<>();
        updatedParams.put("discountPercentage", 25.0);
        UpdateDynamicPricingRuleRequest request = UpdateDynamicPricingRuleRequest.builder()
                .itemId(catalogItemId) // Assuming itemId and ruleType cannot be changed by this request
                .ruleType(ruleEntity.getRuleType())
                .parameters(updatedParams)
                .enabled(false)
                .build();
        String updater = "test-updater";

        when(ruleRepository.findById(ruleId)).thenReturn(Optional.of(ruleEntity));
        when(ruleRepository.save(any(DynamicPricingRuleEntity.class))).thenReturn(ruleEntity); // Return the same entity, potentially modified

        // Act
        DynamicPricingRuleDto resultDto = dynamicPricingRuleService.updateRule(ruleId, request, updater);

        // Assert
        assertThat(resultDto).isNotNull();
        assertThat(resultDto.getId()).isEqualTo(ruleId);
        assertThat(resultDto.getParameters()).isEqualTo(updatedParams);
        assertThat(resultDto.isEnabled()).isFalse();

        verify(ruleRepository).findById(ruleId);
        verify(ruleRepository).save(ruleEntity); // ruleEntity should have been modified by the service

        ArgumentCaptor<DynamicPricingRuleDto> dtoCaptor = ArgumentCaptor.forClass(DynamicPricingRuleDto.class);
        verify(outboxEventService).saveOutboxEvent(
                eq("DynamicPricingRule"),
                eq(ruleId),
                eq("dynamic.pricing.rule.updated"),
                eq(testTopic),
                dtoCaptor.capture()
        );
        assertThat(dtoCaptor.getValue().getId()).isEqualTo(ruleId);
        assertThat(dtoCaptor.getValue().getParameters()).isEqualTo(updatedParams);
        assertThat(dtoCaptor.getValue().isEnabled()).isFalse();
    }

    @Test
    void updateRule_whenRuleNotFound_shouldThrowResourceNotFound() {
        // Arrange
        UpdateDynamicPricingRuleRequest request = UpdateDynamicPricingRuleRequest.builder().itemId(catalogItemId).ruleType("TYPE").build();
        when(ruleRepository.findById(ruleId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> dynamicPricingRuleService.updateRule(ruleId, request, "user"));
        verify(ruleRepository, never()).save(any());
        verify(outboxEventService, never()).saveOutboxEvent(any(), any(), any(), any(), any());
    }

    @Test
    void updateRule_invalidParameters_shouldThrowIllegalArgumentException() {
        Map<String, Object> invalid = new HashMap<>();
        UpdateDynamicPricingRuleRequest request = UpdateDynamicPricingRuleRequest.builder()
                .itemId(catalogItemId)
                .ruleType(ruleEntity.getRuleType())
                .parameters(invalid)
                .enabled(true)
                .build();

        when(ruleRepository.findById(ruleId)).thenReturn(Optional.of(ruleEntity));

        assertThrows(IllegalArgumentException.class, () -> dynamicPricingRuleService.updateRule(ruleId, request, "user"));
        verify(ruleRepository, never()).save(any());
    }

    @Test
    void deleteRule_whenRuleExists_shouldDeleteAndSaveOutboxEvent() {
        // Arrange
        when(ruleRepository.findById(ruleId)).thenReturn(Optional.of(ruleEntity));
        doNothing().when(ruleRepository).delete(ruleEntity);

        // Act
        dynamicPricingRuleService.deleteRule(ruleId);

        // Assert
        verify(ruleRepository).findById(ruleId);
        verify(ruleRepository).delete(ruleEntity);

        ArgumentCaptor<DynamicPricingRuleDto> dtoCaptor = ArgumentCaptor.forClass(DynamicPricingRuleDto.class);
        verify(outboxEventService).saveOutboxEvent(
                eq("DynamicPricingRule"),
                eq(ruleId),
                eq("dynamic.pricing.rule.deleted"),
                eq(testTopic),
                dtoCaptor.capture()
        );
        // For delete, the DTO passed to outbox is the state *before* deletion
        assertThat(dtoCaptor.getValue().getId()).isEqualTo(ruleEntity.getId());
        assertThat(dtoCaptor.getValue().getRuleType()).isEqualTo(ruleEntity.getRuleType());
    }

    @Test
    void deleteRule_whenRuleNotFound_shouldThrowResourceNotFound() {
        // Arrange
        when(ruleRepository.findById(ruleId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> dynamicPricingRuleService.deleteRule(ruleId));
        verify(ruleRepository, never()).delete(any());
        verify(outboxEventService, never()).saveOutboxEvent(any(), any(), any(), any(), any());
    }

    // TODO: Add tests for getRuleById, findAllRules, findRulesByItemId (these don't interact with outbox)
}
