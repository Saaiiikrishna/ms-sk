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
    // @Test
    // void processMetric_callsCalculateAndPublishPrice() { ... }


    private EnrichedAggregatedMetric buildEnrichedData(AggregatedMetric aggMetric,
                                                       DynamicPricingRuleDto ruleDto,
                                                       PriceOverrideDto overrideDto,
                                                       ItemBasePriceEvent basePriceEvent) {
        return EnrichedAggregatedMetric.builder()
                .aggregatedMetric(aggMetric)
                .ruleDto(ruleDto)
                .overrideDto(overrideDto)
                .basePriceEvent(basePriceEvent)
                .build();
    }

    @Test
    void calculatePrice_noRulesNoOverrides_noLastPrice_returnsBasePriceEvent() { // Renamed, verify return
        AggregatedMetric metrics = createSampleAggregatedMetric(testItemId, 50L);
        ItemBasePriceEvent basePriceEvent = ItemBasePriceEvent.builder().itemId(testItemId).basePrice(basePrice).build();
        EnrichedAggregatedMetric enrichedData = buildEnrichedData(metrics, null, null, basePriceEvent);

        Optional<PriceUpdatedEvent> resultOpt = pricingEngineService.calculatePrice(enrichedData, Optional.empty());

        assertThat(resultOpt).isPresent();
        PriceUpdatedEvent resultEvent = resultOpt.get();
        assertThat(resultEvent.getItemId()).isEqualTo(testItemId);
        assertThat(resultEvent.getBasePrice()).isEqualByComparingTo(basePrice);
        assertThat(resultEvent.getFinalPrice()).isEqualByComparingTo(basePrice);
        assertThat(resultEvent.getComponents()).hasSize(1);
        assertThat(resultEvent.getComponents().get(0).getComponentName()).isEqualTo("BASE_PRICE");
        verify(priceUpdatedEventKafkaTemplate, never()).send(anyString(),anyString(),any()); // Service no longer sends
    }

    @Test
    void calculatePrice_withActiveOverride_returnsOverridePriceEvent() { // Renamed
        PriceOverrideDto activeOverrideDto = PriceOverrideDto.builder()
                .id(UUID.randomUUID()).itemId(testItemId).overridePrice(BigDecimal.valueOf(80.00))
                .enabled(true).startTime(Instant.now().minusSeconds(60)).endTime(Instant.now().plusSeconds(60))
                .build();
        AggregatedMetric metrics = createSampleAggregatedMetric(testItemId, 50L);
        ItemBasePriceEvent basePriceEvent = ItemBasePriceEvent.builder().itemId(testItemId).basePrice(basePrice).build();
        EnrichedAggregatedMetric enrichedData = buildEnrichedData(metrics, null, activeOverrideDto, basePriceEvent);

        Optional<PriceUpdatedEvent> resultOpt = pricingEngineService.calculatePrice(enrichedData, Optional.empty());

        assertThat(resultOpt).isPresent();
        PriceUpdatedEvent resultEvent = resultOpt.get();
        assertThat(resultEvent.getFinalPrice()).isEqualByComparingTo("80.00");
        assertThat(resultEvent.getComponents()).anyMatch(c -> "MANUAL_OVERRIDE".equals(c.getComponentName()));
        verify(priceUpdatedEventKafkaTemplate, never()).send(anyString(),anyString(),any());
    }

    @Test
    void calculatePrice_withApplicableRule_returnsAdjustedPriceEvent() { // Renamed
        Map<String, Object> params = new HashMap<>();
        params.put("threshold", 50L);
        params.put("adjustmentPercentage", 0.10);
        DynamicPricingRuleDto ruleDto = DynamicPricingRuleDto.builder()
                .id(UUID.randomUUID()).itemId(testItemId).ruleType("VIEW_COUNT_THRESHOLD")
                .parameters(params).enabled(true)
                .build();
        AggregatedMetric metrics = createSampleAggregatedMetric(testItemId, 100L);
        ItemBasePriceEvent basePriceEvent = ItemBasePriceEvent.builder().itemId(testItemId).basePrice(basePrice).build();
        EnrichedAggregatedMetric enrichedData = buildEnrichedData(metrics, ruleDto, null, basePriceEvent);

        Optional<PriceUpdatedEvent> resultOpt = pricingEngineService.calculatePrice(enrichedData, Optional.empty());

        assertThat(resultOpt).isPresent();
        PriceUpdatedEvent resultEvent = resultOpt.get();
        assertThat(resultEvent.getFinalPrice()).isEqualByComparingTo("110.00");
        assertThat(resultEvent.getComponents()).anyMatch(c -> "VIEW_COUNT_THRESHOLD".equals(c.getComponentName()));
        verify(priceUpdatedEventKafkaTemplate, never()).send(anyString(),anyString(),any());
    }

    @Test
    void calculatePrice_withFlatAmountOffRule_returnsAdjustedPriceEvent() { // Renamed
        Map<String, Object> params = new HashMap<>();
        params.put("amountOff", "20.00");
        DynamicPricingRuleDto ruleDto = DynamicPricingRuleDto.builder()
                .id(UUID.randomUUID()).itemId(testItemId).ruleType("FLAT_AMOUNT_OFF")
                .parameters(params).enabled(true)
                .build();
        AggregatedMetric metrics = createSampleAggregatedMetric(testItemId, 10L);
        ItemBasePriceEvent basePriceEvent = ItemBasePriceEvent.builder().itemId(testItemId).basePrice(basePrice).build();
        EnrichedAggregatedMetric enrichedData = buildEnrichedData(metrics, ruleDto, null, basePriceEvent);

        Optional<PriceUpdatedEvent> resultOpt = pricingEngineService.calculatePrice(enrichedData, Optional.empty());

        assertThat(resultOpt).isPresent();
        PriceUpdatedEvent resultEvent = resultOpt.get();
        assertThat(resultEvent.getFinalPrice()).isEqualByComparingTo("80.00");
        assertThat(resultEvent.getComponents()).anyMatch(c -> "FLAT_AMOUNT_OFF".equals(c.getComponentName()));
        assertThat(resultEvent.getComponents().stream().filter(c-> "FLAT_AMOUNT_OFF".equals(c.getComponentName())).findFirst().get().getValue()).isEqualByComparingTo("-20.00");
        verify(priceUpdatedEventKafkaTemplate, never()).send(anyString(),anyString(),any());
    }

    @Test
    void calculatePrice_priceChangeBelowThreshold_returnsEmptyOptional() { // Renamed
        BigDecimal lastPublishedPrice = new BigDecimal("100.00");
        Map<String, Object> params = new HashMap<>();
        params.put("threshold", 1L);
        params.put("adjustmentPercentage", 0.001);
        DynamicPricingRuleDto ruleDto = DynamicPricingRuleDto.builder()
                .id(UUID.randomUUID()).itemId(testItemId).ruleType("VIEW_COUNT_THRESHOLD")
                .parameters(params).enabled(true)
                .build();
        AggregatedMetric metrics = createSampleAggregatedMetric(testItemId, 10L);
        ItemBasePriceEvent basePriceEvent = ItemBasePriceEvent.builder().itemId(testItemId).basePrice(basePrice).build();
        EnrichedAggregatedMetric enrichedData = buildEnrichedData(metrics, ruleDto, null, basePriceEvent);

        pricingEngineService.calculateAndPublishPrice(enrichedData, Optional.of(lastPublishedPrice));

        verify(priceUpdatedEventKafkaTemplate, never()).send(anyString(), anyString(), any(PriceUpdatedEvent.class));
    }

    @Test
    void calculateAndPublishPrice_priceChangeAboveThreshold_publishes() {
        BigDecimal lastPublishedPrice = new BigDecimal("100.00");
        Map<String, Object> params = new HashMap<>();
        params.put("threshold", 1L);
        params.put("adjustmentPercentage", 0.05);
        DynamicPricingRuleDto ruleDto = DynamicPricingRuleDto.builder()
                .id(UUID.randomUUID()).itemId(testItemId).ruleType("VIEW_COUNT_THRESHOLD")
                .parameters(params).enabled(true)
                .build();
        AggregatedMetric metrics = createSampleAggregatedMetric(testItemId, 10L);
        ItemBasePriceEvent basePriceEvent = ItemBasePriceEvent.builder().itemId(testItemId).basePrice(basePrice).build();
        EnrichedAggregatedMetric enrichedData = buildEnrichedData(metrics, ruleDto, null, basePriceEvent);

        pricingEngineService.calculateAndPublishPrice(enrichedData, Optional.of(lastPublishedPrice));

        verify(priceUpdatedEventKafkaTemplate).send(anyString(), anyString(), any(PriceUpdatedEvent.class));
    }

    @Test
    void calculateAndPublishPrice_noLastPrice_publishesIfPriceDifferentFromBase() {
        Map<String, Object> params = new HashMap<>();
        params.put("threshold", 1L);
        params.put("adjustmentPercentage", 0.001);
        DynamicPricingRuleDto ruleDto = DynamicPricingRuleDto.builder()
                .id(UUID.randomUUID()).itemId(testItemId).ruleType("VIEW_COUNT_THRESHOLD")
                .parameters(params).enabled(true)
                .build();
        AggregatedMetric metrics = createSampleAggregatedMetric(testItemId, 10L);
        ItemBasePriceEvent basePriceEvent = ItemBasePriceEvent.builder().itemId(testItemId).basePrice(basePrice).build();
        EnrichedAggregatedMetric enrichedData = buildEnrichedData(metrics, ruleDto, null, basePriceEvent);

        pricingEngineService.calculateAndPublishPrice(enrichedData, Optional.empty());

        verify(priceUpdatedEventKafkaTemplate).send(anyString(), anyString(), any(PriceUpdatedEvent.class));
    }


    @Test
    void calculateAndPublishPrice_negativePriceClampedToZero() {
        Map<String, Object> params = new HashMap<>();
        params.put("amountOff", "150.00");
        DynamicPricingRuleDto ruleDto = DynamicPricingRuleDto.builder()
                .id(UUID.randomUUID()).itemId(testItemId).ruleType("FLAT_AMOUNT_OFF")
                .parameters(params).enabled(true)
                .build();
        AggregatedMetric metrics = createSampleAggregatedMetric(testItemId, 10L);
        ItemBasePriceEvent basePriceEvent = ItemBasePriceEvent.builder().itemId(testItemId).basePrice(basePrice).build();
        EnrichedAggregatedMetric enrichedData = buildEnrichedData(metrics, ruleDto, null, basePriceEvent);

        pricingEngineService.calculateAndPublishPrice(enrichedData, Optional.empty());

        ArgumentCaptor<PriceUpdatedEvent> eventCaptor = ArgumentCaptor.forClass(PriceUpdatedEvent.class);
        verify(priceUpdatedEventKafkaTemplate).send(eq(priceUpdatedTopic), eq(testItemId.toString()), eventCaptor.capture());
        PriceUpdatedEvent publishedEvent = eventCaptor.getValue();
        assertThat(publishedEvent.getFinalPrice()).isEqualByComparingTo("0.00");
    }
}
