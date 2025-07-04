package com.mysillydreams.pricingengine.service;

import com.mysillydreams.pricingengine.domain.DynamicPricingRuleEntity;
import com.mysillydreams.pricingengine.domain.PriceOverrideEntity;
import com.mysillydreams.pricingengine.dto.AggregatedMetric;
import com.mysillydreams.pricingengine.dto.MetricEvent;
import com.mysillydreams.pricingengine.dto.PriceUpdatedEvent;
import com.mysillydreams.pricingengine.repository.DynamicPricingRuleRepository;
import com.mysillydreams.pricingengine.repository.PriceOverrideRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

    @Mock
    private DynamicPricingRuleRepository ruleRepository;

    @Mock
    private PriceOverrideRepository overrideRepository;

    @Mock
    private KafkaTemplate<String, PriceUpdatedEvent> priceUpdatedEventKafkaTemplate;

    // Using @Spy and @InjectMocks together can be tricky.
    // It's often better to inject mocks into the spy manually or test Collaborators.
    // However, for testing the calculateAndPublishPrice directly and mocking its internal calls to caches,
    // or for mocking fetchBasePrice, this can work.
    // An alternative is to make fetchBasePrice, findActiveOverride etc. protected and override them in a test subclass.
    @Spy
    @InjectMocks
    private DefaultPricingEngineService pricingEngineService;

    private final String priceUpdatedTopic = "catalog.price.updated.test";
    private final UUID testItemId = UUID.randomUUID();
    private final Map<UUID, DynamicPricingRuleEntity> rulesCache = new ConcurrentHashMap<>();
    private final Map<UUID, PriceOverrideEntity> overridesCache = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(pricingEngineService, "priceUpdatedTopic", priceUpdatedTopic);
        ReflectionTestUtils.setField(pricingEngineService, "rulesCache", rulesCache);
        ReflectionTestUtils.setField(pricingEngineService, "overridesCache", overridesCache);
        rulesCache.clear();
        overridesCache.clear();

        // Mock the base price fetching for these unit tests
        // We are spying on pricingEngineService, so we can doReturn().when(spy).methodToMock()
        doReturn(BigDecimal.valueOf(100.00)).when(pricingEngineService).fetchBasePrice(any(UUID.class));
    }

    private AggregatedMetric createSampleAggregatedMetric(UUID itemId, long count) {
        return AggregatedMetric.builder()
                .itemId(itemId)
                .metricCount(count)
                .windowStartTimestamp(Instant.now().minusSeconds(300).toEpochMilli())
                .windowEndTimestamp(Instant.now().toEpochMilli())
                .build();
    }

    @Test
    void processMetric_callsCalculateAndPublishPrice() {
        MetricEvent metricEvent = MetricEvent.builder().eventId(UUID.randomUUID()).itemId(testItemId).metricType("VIEW").timestamp(Instant.now()).build();
        AggregatedMetric expectedAggregatedMetric = AggregatedMetric.builder()
            .itemId(testItemId).metricCount(1L) // processMetric currently creates this simple agg
            .windowStartTimestamp(metricEvent.getTimestamp().toEpochMilli())
            .windowEndTimestamp(Instant.now().toEpochMilli()) // This will be slightly different, need to capture arg
            .build();

        // We spy on the service, so we can verify calculateAndPublishPrice is called by processMetric
        doNothing().when(pricingEngineService).calculateAndPublishPrice(any(UUID.class), any(AggregatedMetric.class));

        pricingEngineService.processMetric(metricEvent);

        ArgumentCaptor<AggregatedMetric> aggMetricCaptor = ArgumentCaptor.forClass(AggregatedMetric.class);
        verify(pricingEngineService).calculateAndPublishPrice(eq(testItemId), aggMetricCaptor.capture());

        assertThat(aggMetricCaptor.getValue().getItemId()).isEqualTo(testItemId);
        assertThat(aggMetricCaptor.getValue().getMetricCount()).isEqualTo(1L); // As per current stub in processMetric
    }


    @Test
    void calculateAndPublishPrice_noRulesNoOverrides_publishesBasePrice() {
        AggregatedMetric metrics = createSampleAggregatedMetric(testItemId, 50L);

        pricingEngineService.calculateAndPublishPrice(testItemId, metrics);

        ArgumentCaptor<PriceUpdatedEvent> eventCaptor = ArgumentCaptor.forClass(PriceUpdatedEvent.class);
        verify(priceUpdatedEventKafkaTemplate).send(eq(priceUpdatedTopic), eq(testItemId.toString()), eventCaptor.capture());

        PriceUpdatedEvent publishedEvent = eventCaptor.getValue();
        assertThat(publishedEvent.getItemId()).isEqualTo(testItemId);
        assertThat(publishedEvent.getBasePrice()).isEqualByComparingTo("100.00");
        assertThat(publishedEvent.getFinalPrice()).isEqualByComparingTo("100.00");
        assertThat(publishedEvent.getComponents()).hasSize(1);
        assertThat(publishedEvent.getComponents().get(0).getComponentName()).isEqualTo("BASE_PRICE");
    }

    @Test
    void calculateAndPublishPrice_withActiveOverride_publishesOverridePrice() {
        PriceOverrideEntity override = PriceOverrideEntity.builder()
                .id(UUID.randomUUID()).itemId(testItemId).overridePrice(BigDecimal.valueOf(80.00))
                .enabled(true).startTime(Instant.now().minusSeconds(60)).endTime(Instant.now().plusSeconds(60))
                .build();
        overridesCache.put(override.getId(), override);

        AggregatedMetric metrics = createSampleAggregatedMetric(testItemId, 50L);
        pricingEngineService.calculateAndPublishPrice(testItemId, metrics);

        ArgumentCaptor<PriceUpdatedEvent> eventCaptor = ArgumentCaptor.forClass(PriceUpdatedEvent.class);
        verify(priceUpdatedEventKafkaTemplate).send(eq(priceUpdatedTopic), eq(testItemId.toString()), eventCaptor.capture());

        PriceUpdatedEvent publishedEvent = eventCaptor.getValue();
        assertThat(publishedEvent.getFinalPrice()).isEqualByComparingTo("80.00");
        assertThat(publishedEvent.getComponents()).anyMatch(c -> "MANUAL_OVERRIDE".equals(c.getComponentName()));
    }

    @Test
    void calculateAndPublishPrice_withApplicableRule_publishesAdjustedPrice() {
        // Rule: VIEW_COUNT_THRESHOLD, threshold 50, adjustment +10%
        Map<String, Object> params = new HashMap<>();
        params.put("threshold", 50L); // Long for threshold
        params.put("adjustmentPercentage", 0.10); // Double for percentage

        DynamicPricingRuleEntity rule = DynamicPricingRuleEntity.builder()
                .id(UUID.randomUUID()).itemId(testItemId).ruleType("VIEW_COUNT_THRESHOLD")
                .parameters(params).enabled(true)
                .build();
        rulesCache.put(rule.getId(), rule);

        AggregatedMetric metrics = createSampleAggregatedMetric(testItemId, 100L); // metricCount > threshold

        pricingEngineService.calculateAndPublishPrice(testItemId, metrics);

        ArgumentCaptor<PriceUpdatedEvent> eventCaptor = ArgumentCaptor.forClass(PriceUpdatedEvent.class);
        verify(priceUpdatedEventKafkaTemplate).send(eq(priceUpdatedTopic), eq(testItemId.toString()), eventCaptor.capture());

        PriceUpdatedEvent publishedEvent = eventCaptor.getValue();
        // Base price 100.00, +10% -> 110.00
        assertThat(publishedEvent.getFinalPrice()).isEqualByComparingTo("110.00");
        assertThat(publishedEvent.getComponents()).anyMatch(c -> "VIEW_COUNT_THRESHOLD".equals(c.getComponentName()));
    }

    @Test
    void calculateAndPublishPrice_withFlatAmountOffRule_publishesAdjustedPrice() {
        Map<String, Object> params = new HashMap<>();
        params.put("amountOff", 20.0); // 20.0 amount off

        DynamicPricingRuleEntity rule = DynamicPricingRuleEntity.builder()
                .id(UUID.randomUUID()).itemId(testItemId).ruleType("FLAT_AMOUNT_OFF")
                .parameters(params).enabled(true)
                .build();
        rulesCache.put(rule.getId(), rule);

        AggregatedMetric metrics = createSampleAggregatedMetric(testItemId, 10L); // metrics don't matter for this rule type

        pricingEngineService.calculateAndPublishPrice(testItemId, metrics);

        ArgumentCaptor<PriceUpdatedEvent> eventCaptor = ArgumentCaptor.forClass(PriceUpdatedEvent.class);
        verify(priceUpdatedEventKafkaTemplate).send(eq(priceUpdatedTopic), eq(testItemId.toString()), eventCaptor.capture());

        PriceUpdatedEvent publishedEvent = eventCaptor.getValue();
        // Base price 100.00, -20.00 -> 80.00
        assertThat(publishedEvent.getFinalPrice()).isEqualByComparingTo("80.00");
        assertThat(publishedEvent.getComponents()).anyMatch(c -> "FLAT_AMOUNT_OFF".equals(c.getComponentName()));
         assertThat(publishedEvent.getComponents().stream().filter(c-> "FLAT_AMOUNT_OFF".equals(c.getComponentName())).findFirst().get().getValue()).isEqualByComparingTo("-20.00");
    }

    @Test
    void calculateAndPublishPrice_ruleNotTriggered_publishesBasePrice() {
        Map<String, Object> params = new HashMap<>();
        params.put("threshold", 200L);
        params.put("adjustmentPercentage", 0.10);
        DynamicPricingRuleEntity rule = DynamicPricingRuleEntity.builder()
                .id(UUID.randomUUID()).itemId(testItemId).ruleType("VIEW_COUNT_THRESHOLD")
                .parameters(params).enabled(true)
                .build();
        rulesCache.put(rule.getId(), rule);

        AggregatedMetric metrics = createSampleAggregatedMetric(testItemId, 100L); // metricCount < threshold

        pricingEngineService.calculateAndPublishPrice(testItemId, metrics);

        ArgumentCaptor<PriceUpdatedEvent> eventCaptor = ArgumentCaptor.forClass(PriceUpdatedEvent.class);
        verify(priceUpdatedEventKafkaTemplate).send(eq(priceUpdatedTopic), eq(testItemId.toString()), eventCaptor.capture());

        PriceUpdatedEvent publishedEvent = eventCaptor.getValue();
        assertThat(publishedEvent.getFinalPrice()).isEqualByComparingTo("100.00"); // No adjustment
        assertThat(publishedEvent.getComponents()).noneMatch(c -> "VIEW_COUNT_THRESHOLD".equals(c.getComponentName()));
    }

    @Test
    void calculateAndPublishPrice_negativePriceClampedToZero() {
        // Rule for large discount
        Map<String, Object> params = new HashMap<>();
        params.put("amountOff", 150.0); // More than base price

        DynamicPricingRuleEntity rule = DynamicPricingRuleEntity.builder()
                .id(UUID.randomUUID()).itemId(testItemId).ruleType("FLAT_AMOUNT_OFF")
                .parameters(params).enabled(true)
                .build();
        rulesCache.put(rule.getId(), rule);

        AggregatedMetric metrics = createSampleAggregatedMetric(testItemId, 10L);

        pricingEngineService.calculateAndPublishPrice(testItemId, metrics);

        ArgumentCaptor<PriceUpdatedEvent> eventCaptor = ArgumentCaptor.forClass(PriceUpdatedEvent.class);
        verify(priceUpdatedEventKafkaTemplate).send(eq(priceUpdatedTopic), eq(testItemId.toString()), eventCaptor.capture());
        PriceUpdatedEvent publishedEvent = eventCaptor.getValue();
        assertThat(publishedEvent.getFinalPrice()).isEqualByComparingTo("0.00");
    }

    // TODO: Test @PostConstruct initializeCaches - requires more complex setup or making methods package-private for testing.
    // TODO: Test updateRules and updateOverrides cache updates.
}
