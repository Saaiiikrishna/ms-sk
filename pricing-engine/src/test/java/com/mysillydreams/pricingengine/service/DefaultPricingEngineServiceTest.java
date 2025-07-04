package com.mysillydreams.pricingengine.service;

import com.mysillydreams.pricingengine.domain.DynamicPricingRuleEntity;
import com.fasterxml.jackson.databind.ObjectMapper; // Added
import com.mysillydreams.pricingengine.domain.DynamicPricingRuleEntity;
import com.mysillydreams.pricingengine.domain.PriceOverrideEntity;
import com.mysillydreams.pricingengine.dto.*; // Wildcard for DTOs
import com.mysillydreams.pricingengine.dto.rules.FlatAmountOffParams;
import com.mysillydreams.pricingengine.dto.rules.ViewCountThresholdParams;
// Repositories no longer needed for these tests as service is decoupled from them
// import com.mysillydreams.pricingengine.repository.DynamicPricingRuleRepository;
// import com.mysillydreams.pricingengine.repository.PriceOverrideRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Optional; // Added for Optional
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DefaultPricingEngineServiceTest {

    // Repositories no longer needed as service is decoupled from direct DB access for cache
    // @Mock private DynamicPricingRuleRepository ruleRepository;
    // @Mock private PriceOverrideRepository overrideRepository;

    @Mock
    private KafkaTemplate<String, PriceUpdatedEvent> priceUpdatedEventKafkaTemplate;

    @Mock
    private ObjectMapper objectMapper; // Mock ObjectMapper since it's now a dependency

    @InjectMocks
    private DefaultPricingEngineService pricingEngineService; // No longer a Spy for this basic setup

    private final String priceUpdatedTopic = "catalog.price.updated.test";
    private final UUID testItemId = UUID.randomUUID();
    private final BigDecimal basePrice = new BigDecimal("100.00");

    // Thresholds for testing price change logic
    private final double thresholdPercentage = 0.01; // 1%
    // private final BigDecimal thresholdAmount = new BigDecimal("0.50"); // 50 cents, if using amount too


    @BeforeEach
    void setUp() {
        // Manually inject mocks if @InjectMocks doesn't cover all constructor args,
        // or ensure constructor matches @InjectMocks capability.
        // DefaultPricingEngineService now takes KafkaTemplate and ObjectMapper.
        pricingEngineService = new DefaultPricingEngineService(priceUpdatedEventKafkaTemplate, objectMapper);

        ReflectionTestUtils.setField(pricingEngineService, "priceUpdatedTopic", priceUpdatedTopic);
        // Inject threshold values
        ReflectionTestUtils.setField(pricingEngineService, "priceUpdateThresholdPercentage", thresholdPercentage);
        // ReflectionTestUtils.setField(pricingEngineService, "priceUpdateThresholdAmount", thresholdAmount);

        // Setup ObjectMapper mock for rule parameter conversion
        // This ensures that when objectMapper.convertValue is called, it returns a valid params object.
        when(objectMapper.convertValue(any(Map.class), eq(ViewCountThresholdParams.class)))
            .thenAnswer(invocation -> {
                Map<String, Object> map = invocation.getArgument(0);
                return new ViewCountThresholdParams(
                    ((Number)map.getOrDefault("threshold", 0L)).longValue(),
                    ((Number)map.getOrDefault("adjustmentPercentage", 0.0)).doubleValue()
                );
            });
        when(objectMapper.convertValue(any(Map.class), eq(FlatAmountOffParams.class)))
            .thenAnswer(invocation -> {
                 Map<String, Object> map = invocation.getArgument(0);
                return new FlatAmountOffParams(
                    new BigDecimal(map.getOrDefault("amountOff", "0.0").toString())
                );
            });
    }

    private AggregatedMetric createSampleAggregatedMetric(UUID itemId, long count) {
        return AggregatedMetric.builder()
                .itemId(itemId)
                .metricCount(count)
                .windowStartTimestamp(Instant.now().minusSeconds(300).toEpochMilli())
                .windowEndTimestamp(Instant.now().toEpochMilli())
                .build();
    }

    // The processMetric method is now very simple and mainly a pass-through or placeholder.
    // Its direct testing might be less valuable than testing calculateAndPublishPrice.
    // However, if it had any logic, it would be tested here.
    // For now, we assume the stream directly calls calculateAndPublishPrice with all necessary data.
    // @Test
    // void processMetric_callsCalculateAndPublishPrice() { ... }


    @Test
    void calculateAndPublishPrice_noRulesNoOverrides_noLastPrice_publishesBasePrice() {
        AggregatedMetric metrics = createSampleAggregatedMetric(testItemId, 50L);

        pricingEngineService.calculateAndPublishPrice(
                testItemId, basePrice, metrics, Collections.emptyList(), null, Optional.empty());

        ArgumentCaptor<PriceUpdatedEvent> eventCaptor = ArgumentCaptor.forClass(PriceUpdatedEvent.class);
        verify(priceUpdatedEventKafkaTemplate).send(eq(priceUpdatedTopic), eq(testItemId.toString()), eventCaptor.capture());

        PriceUpdatedEvent publishedEvent = eventCaptor.getValue();
        assertThat(publishedEvent.getItemId()).isEqualTo(testItemId);
        assertThat(publishedEvent.getBasePrice()).isEqualByComparingTo(basePrice);
        assertThat(publishedEvent.getFinalPrice()).isEqualByComparingTo(basePrice);
        assertThat(publishedEvent.getComponents()).hasSize(1);
        assertThat(publishedEvent.getComponents().get(0).getComponentName()).isEqualTo("BASE_PRICE");
    }

    @Test
    void calculateAndPublishPrice_withActiveOverride_publishesOverridePrice() {
        PriceOverrideEntity activeOverride = PriceOverrideEntity.builder()
                .id(UUID.randomUUID()).itemId(testItemId).overridePrice(BigDecimal.valueOf(80.00))
                .enabled(true).startTime(Instant.now().minusSeconds(60)).endTime(Instant.now().plusSeconds(60))
                .build();

        AggregatedMetric metrics = createSampleAggregatedMetric(testItemId, 50L);
        pricingEngineService.calculateAndPublishPrice(
                testItemId, basePrice, metrics, Collections.emptyList(), activeOverride, Optional.empty());

        ArgumentCaptor<PriceUpdatedEvent> eventCaptor = ArgumentCaptor.forClass(PriceUpdatedEvent.class);
        verify(priceUpdatedEventKafkaTemplate).send(eq(priceUpdatedTopic), eq(testItemId.toString()), eventCaptor.capture());

        PriceUpdatedEvent publishedEvent = eventCaptor.getValue();
        assertThat(publishedEvent.getFinalPrice()).isEqualByComparingTo("80.00");
        assertThat(publishedEvent.getComponents()).anyMatch(c -> "MANUAL_OVERRIDE".equals(c.getComponentName()));
    }

    @Test
    void calculateAndPublishPrice_withApplicableRule_publishesAdjustedPrice() {
        Map<String, Object> params = new HashMap<>();
        params.put("threshold", 50L);
        params.put("adjustmentPercentage", 0.10);
        DynamicPricingRuleEntity rule = DynamicPricingRuleEntity.builder()
                .id(UUID.randomUUID()).itemId(testItemId).ruleType("VIEW_COUNT_THRESHOLD")
                .parameters(params).enabled(true)
                .build();

        AggregatedMetric metrics = createSampleAggregatedMetric(testItemId, 100L); // metricCount > threshold

        pricingEngineService.calculateAndPublishPrice(
                testItemId, basePrice, metrics, Collections.singletonList(rule), null, Optional.empty());

        ArgumentCaptor<PriceUpdatedEvent> eventCaptor = ArgumentCaptor.forClass(PriceUpdatedEvent.class);
        verify(priceUpdatedEventKafkaTemplate).send(eq(priceUpdatedTopic), eq(testItemId.toString()), eventCaptor.capture());

        PriceUpdatedEvent publishedEvent = eventCaptor.getValue();
        assertThat(publishedEvent.getFinalPrice()).isEqualByComparingTo("110.00"); // 100 * (1 + 0.10)
        assertThat(publishedEvent.getComponents()).anyMatch(c -> "VIEW_COUNT_THRESHOLD".equals(c.getComponentName()));
    }

    @Test
    void calculateAndPublishPrice_withFlatAmountOffRule_publishesAdjustedPrice() {
        Map<String, Object> params = new HashMap<>();
        params.put("amountOff", "20.00"); // String to match FlatAmountOffParams if it expects String then converts
         DynamicPricingRuleEntity rule = DynamicPricingRuleEntity.builder()
                .id(UUID.randomUUID()).itemId(testItemId).ruleType("FLAT_AMOUNT_OFF")
                .parameters(params).enabled(true)
                .build();

        AggregatedMetric metrics = createSampleAggregatedMetric(testItemId, 10L);

        pricingEngineService.calculateAndPublishPrice(
            testItemId, basePrice, metrics, Collections.singletonList(rule), null, Optional.empty());

        ArgumentCaptor<PriceUpdatedEvent> eventCaptor = ArgumentCaptor.forClass(PriceUpdatedEvent.class);
        verify(priceUpdatedEventKafkaTemplate).send(eq(priceUpdatedTopic), eq(testItemId.toString()), eventCaptor.capture());
        PriceUpdatedEvent publishedEvent = eventCaptor.getValue();
        assertThat(publishedEvent.getFinalPrice()).isEqualByComparingTo("80.00"); // 100 - 20
        assertThat(publishedEvent.getComponents()).anyMatch(c -> "FLAT_AMOUNT_OFF".equals(c.getComponentName()));
        assertThat(publishedEvent.getComponents().stream().filter(c-> "FLAT_AMOUNT_OFF".equals(c.getComponentName())).findFirst().get().getValue()).isEqualByComparingTo("-20.00");
    }

    @Test
    void calculateAndPublishPrice_priceChangeBelowThreshold_doesNotPublish() {
        BigDecimal lastPublishedPrice = new BigDecimal("100.00");
        // Rule that causes a very small change (0.1%)
        Map<String, Object> params = new HashMap<>();
        params.put("threshold", 1L);
        params.put("adjustmentPercentage", 0.001); // 0.1%
        DynamicPricingRuleEntity rule = DynamicPricingRuleEntity.builder()
                .id(UUID.randomUUID()).itemId(testItemId).ruleType("VIEW_COUNT_THRESHOLD")
                .parameters(params).enabled(true)
                .build();

        AggregatedMetric metrics = createSampleAggregatedMetric(testItemId, 10L); // Trigger rule

        pricingEngineService.calculateAndPublishPrice(
            testItemId, basePrice, metrics, Collections.singletonList(rule), null, Optional.of(lastPublishedPrice));

        // Final price would be 100 * 1.001 = 100.10. Change is 0.10.
        // Base price is 100. Threshold is 1% of 100 = 1.00.
        // 0.10 is less than 1.00, so no publish.
        verify(priceUpdatedEventKafkaTemplate, never()).send(anyString(), anyString(), any(PriceUpdatedEvent.class));
    }

    @Test
    void calculateAndPublishPrice_priceChangeAboveThreshold_publishes() {
        BigDecimal lastPublishedPrice = new BigDecimal("100.00");
        // Rule that causes a significant change (5%)
        Map<String, Object> params = new HashMap<>();
        params.put("threshold", 1L);
        params.put("adjustmentPercentage", 0.05); // 5%
        DynamicPricingRuleEntity rule = DynamicPricingRuleEntity.builder()
                .id(UUID.randomUUID()).itemId(testItemId).ruleType("VIEW_COUNT_THRESHOLD")
                .parameters(params).enabled(true)
                .build();

        AggregatedMetric metrics = createSampleAggregatedMetric(testItemId, 10L);

        pricingEngineService.calculateAndPublishPrice(
            testItemId, basePrice, metrics, Collections.singletonList(rule), null, Optional.of(lastPublishedPrice));

        // Final price 105.00. Change is 5.00. Threshold 1.00. 5.00 > 1.00. Publish.
        verify(priceUpdatedEventKafkaTemplate).send(anyString(), anyString(), any(PriceUpdatedEvent.class));
    }

    @Test
    void calculateAndPublishPrice_noLastPrice_publishesIfPriceDifferentFromBase() {
        // Rule that causes any change
        Map<String, Object> params = new HashMap<>();
        params.put("threshold", 1L);
        params.put("adjustmentPercentage", 0.001); // 0.1% change, normally below threshold
        DynamicPricingRuleEntity rule = DynamicPricingRuleEntity.builder()
                .id(UUID.randomUUID()).itemId(testItemId).ruleType("VIEW_COUNT_THRESHOLD")
                .parameters(params).enabled(true)
                .build();

        AggregatedMetric metrics = createSampleAggregatedMetric(testItemId, 10L);

        // No last published price (Optional.empty())
        pricingEngineService.calculateAndPublishPrice(
            testItemId, basePrice, metrics, Collections.singletonList(rule), null, Optional.empty());

        // Should publish because there's no previous price to compare against for threshold,
        // and the price did change from base.
        verify(priceUpdatedEventKafkaTemplate).send(anyString(), anyString(), any(PriceUpdatedEvent.class));
    }


    @Test
    void calculateAndPublishPrice_negativePriceClampedToZero() {
        Map<String, Object> params = new HashMap<>();
        params.put("amountOff", "150.00");
        DynamicPricingRuleEntity rule = DynamicPricingRuleEntity.builder()
                .id(UUID.randomUUID()).itemId(testItemId).ruleType("FLAT_AMOUNT_OFF")
                .parameters(params).enabled(true)
                .build();

        AggregatedMetric metrics = createSampleAggregatedMetric(testItemId, 10L);

        pricingEngineService.calculateAndPublishPrice(
            testItemId, basePrice, metrics, Collections.singletonList(rule), null, Optional.empty());

        ArgumentCaptor<PriceUpdatedEvent> eventCaptor = ArgumentCaptor.forClass(PriceUpdatedEvent.class);
        verify(priceUpdatedEventKafkaTemplate).send(eq(priceUpdatedTopic), eq(testItemId.toString()), eventCaptor.capture());
        PriceUpdatedEvent publishedEvent = eventCaptor.getValue();
        assertThat(publishedEvent.getFinalPrice()).isEqualByComparingTo("0.00");
    }
}
