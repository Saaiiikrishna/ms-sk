package com.mysillydreams.pricingengine.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mysillydreams.pricingengine.dto.*;
import com.mysillydreams.pricingengine.dto.rules.ViewCountThresholdParams; // Assuming this might be used if testing rule logic through service
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.*;
import org.apache.kafka.streams.kstream.Windowed;
import org.apache.kafka.streams.test.TestRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerde;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
class DemandMetricsAggregatorStreamTest {

    private static final Logger log = LoggerFactory.getLogger(DemandMetricsAggregatorStreamTest.class);

    private TopologyTestDriver testDriver;
    private TestOutputTopic<String, MetricEvent> demandMetricsDltOutputTopic;
    private TestOutputTopic<String, PriceUpdatedEvent> priceUpdatedOutputTopic;
    private TestOutputTopic<String, PriceUpdatedEvent> internalLastPriceOutputTopic;
    private TestOutputTopic<String, String> processingErrorsDltOutputTopic;


    private final String DEMAND_METRICS_TOPIC = "demand.metrics.test";
    private final String DEMAND_METRICS_DLT_TOPIC = "demand.metrics.dlt.test";
    private final String INTERNAL_RULES_BY_ITEMID_TOPIC = "internal.rules-by-itemid.v1.test";
    private final String INTERNAL_OVERRIDES_BY_ITEMID_TOPIC = "internal.overrides-by-itemid.v1.test";
    private final String INTERNAL_BASE_PRICES_TOPIC = "internal.item.baseprices.v1.test";
    private final String INTERNAL_LAST_PUBLISHED_PRICES_TOPIC = "internal.last-published-prices.v1.test";
    private final String EXTERNAL_PRICE_UPDATED_TOPIC = "catalog.price.updated.test";
    private final String PROCESSING_ERRORS_DLT_TOPIC = "processing.errors.dlt.test";


    @Mock
    private PricingEngineService pricingEngineService;
    @Mock
    private KafkaTemplate<String, String> dltKafkaTemplate; // Used by the stream for DLT

    private DemandMetricsAggregatorStream demandMetricsAggregatorStream;

    private Serde<String> stringSerde = Serdes.String();
    private JsonSerde<MetricEvent> metricEventSerde;
    private JsonSerde<DynamicPricingRuleDto> ruleDtoSerde;
    private JsonSerde<PriceOverrideDto> overrideDtoSerde;
    private JsonSerde<ItemBasePriceEvent> basePriceEventSerde;
    private JsonSerde<PriceUpdatedEvent> priceUpdatedEventSerde;
    private JsonSerde<List<DynamicPricingRuleDto>> listOfRuleDtoSerde;

    private TestInputTopic<String, MetricEvent> demandMetricsInputTopic;
    private TestInputTopic<String, DynamicPricingRuleDto> internalRulesByItemIdInputTopic;
    private TestInputTopic<String, PriceOverrideDto> internalOverridesByItemIdInputTopic;
    private TestInputTopic<String, ItemBasePriceEvent> internalBasePricesInputTopic;
    private TestInputTopic<String, PriceUpdatedEvent> internalLastPublishedPricesInputTopic;

    private ObjectMapper testObjectMapper; // For deserializing DLT content in tests


    @BeforeEach
    void setUp() {
        testObjectMapper = new ObjectMapper().findAndRegisterModules(); // For test-side deserialization
        metricEventSerde = new JsonSerde<>(MetricEvent.class, testObjectMapper);
        ruleDtoSerde = new JsonSerde<>(DynamicPricingRuleDto.class, testObjectMapper);
        overrideDtoSerde = new JsonSerde<>(PriceOverrideDto.class, testObjectMapper);
        basePriceEventSerde = new JsonSerde<>(ItemBasePriceEvent.class, testObjectMapper);
        priceUpdatedEventSerde = new JsonSerde<>(PriceUpdatedEvent.class, testObjectMapper);
        listOfRuleDtoSerde = new JsonSerde<>(new com.fasterxml.jackson.core.type.TypeReference<List<DynamicPricingRuleDto>>() {}, testObjectMapper);

        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "test-pricing-engine-streams");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:1234");
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, JsonSerde.class.getName());
        props.put(JsonSerde.TRUSTED_PACKAGES, "com.mysillydreams.pricingengine.dto");

        demandMetricsAggregatorStream = new DemandMetricsAggregatorStream(
                pricingEngineService,
                dltKafkaTemplate, // This is for the stream's internal DLT publishing for MetricEvents
                testObjectMapper, // This is for the stream to use for processingErrorDLT if it serializes objects
                stringSerde,
                metricEventSerde,
                ruleDtoSerde,
                overrideDtoSerde,
                basePriceEventSerde,
                priceUpdatedEventSerde,
                listOfRuleDtoSerde
        );

        ReflectionTestUtils.setField(demandMetricsAggregatorStream, "demandMetricsTopic", DEMAND_METRICS_TOPIC);
        ReflectionTestUtils.setField(demandMetricsAggregatorStream, "internalRulesByItemIdTopic", INTERNAL_RULES_BY_ITEMID_TOPIC);
        ReflectionTestUtils.setField(demandMetricsAggregatorStream, "internalOverridesByItemIdTopic", INTERNAL_OVERRIDES_BY_ITEMID_TOPIC);
        ReflectionTestUtils.setField(demandMetricsAggregatorStream, "internalBasePricesTopic", INTERNAL_BASE_PRICES_TOPIC);
        ReflectionTestUtils.setField(demandMetricsAggregatorStream, "demandMetricsDltTopic", DEMAND_METRICS_DLT_TOPIC);
        ReflectionTestUtils.setField(demandMetricsAggregatorStream, "internalLastPublishedPricesTopic", INTERNAL_LAST_PUBLISHED_PRICES_TOPIC);
        ReflectionTestUtils.setField(demandMetricsAggregatorStream, "externalPriceUpdatedTopic", EXTERNAL_PRICE_UPDATED_TOPIC);
        ReflectionTestUtils.setField(demandMetricsAggregatorStream, "processingErrorsDltTopic", PROCESSING_ERRORS_DLT_TOPIC);

        StreamsBuilder builder = new StreamsBuilder();
        demandMetricsAggregatorStream.buildPipeline(builder);
        Topology topology = builder.build();
        log.info("Topology for test:\n{}", topology.describe());

        testDriver = new TopologyTestDriver(topology, props);

        demandMetricsInputTopic = testDriver.createInputTopic(DEMAND_METRICS_TOPIC, stringSerde.serializer(), metricEventSerde.serializer());
        demandMetricsDltOutputTopic = testDriver.createOutputTopic(DEMAND_METRICS_DLT_TOPIC, stringSerde.deserializer(), metricEventSerde.deserializer());
        priceUpdatedOutputTopic = testDriver.createOutputTopic(EXTERNAL_PRICE_UPDATED_TOPIC, stringSerde.deserializer(), priceUpdatedEventSerde.deserializer());
        internalLastPriceOutputTopic = testDriver.createOutputTopic(INTERNAL_LAST_PUBLISHED_PRICES_TOPIC, stringSerde.deserializer(), priceUpdatedEventSerde.deserializer());
        processingErrorsDltOutputTopic = testDriver.createOutputTopic(PROCESSING_ERRORS_DLT_TOPIC, stringSerde.deserializer(), stringSerde.deserializer());

        internalRulesByItemIdInputTopic = testDriver.createInputTopic(INTERNAL_RULES_BY_ITEMID_TOPIC, stringSerde.serializer(), ruleDtoSerde.serializer());
        internalOverridesByItemIdInputTopic = testDriver.createInputTopic(INTERNAL_OVERRIDES_BY_ITEMID_TOPIC, stringSerde.serializer(), overrideDtoSerde.serializer());
        internalBasePricesInputTopic = testDriver.createInputTopic(INTERNAL_BASE_PRICES_TOPIC, stringSerde.serializer(), basePriceEventSerde.serializer());
        internalLastPublishedPricesInputTopic = testDriver.createInputTopic(INTERNAL_LAST_PUBLISHED_PRICES_TOPIC, stringSerde.serializer(), priceUpdatedEventSerde.serializer());
    }

    @AfterEach
    void tearDown() {
        if (testDriver != null) {
            testDriver.close();
        }
        metricEventSerde.close();
        ruleDtoSerde.close();
        overrideDtoSerde.close();
        basePriceEventSerde.close();
        priceUpdatedEventSerde.close();
        listOfRuleDtoSerde.close();
    }

    @Test
    void testMetricToPriceUpdate_And_InvalidMetricToDlt() throws Exception {
        UUID itemId1 = UUID.randomUUID();
        String itemId1Str = itemId1.toString();
        Instant baseTime = Instant.parse("2023-01-01T10:00:00Z");

        // Setup: Base Price, One Rule, No Override, No Last Price
        ItemBasePriceEvent basePrice1 = ItemBasePriceEvent.builder().itemId(itemId1).basePrice(new BigDecimal("100.00")).eventTimestamp(Instant.now()).build();
        internalBasePricesInputTopic.pipeInput(itemId1Str, basePrice1);

        Map<String,Object> ruleParams = new HashMap<>();
        ruleParams.put("threshold", 1L);
        ruleParams.put("adjustmentPercentage", 0.1); // +10%
        DynamicPricingRuleDto rule1 = DynamicPricingRuleDto.builder().id(UUID.randomUUID()).itemId(itemId1).ruleType("VIEW_COUNT_THRESHOLD").parameters(ruleParams).enabled(true).build();
        internalRulesByItemIdInputTopic.pipeInput(itemId1Str, rule1);

        testDriver.advanceWallClockTime(Duration.ofSeconds(1)); // Allow KTables to populate

        // Mock service call for this specific test path
        PriceUpdatedEvent expectedEvent = PriceUpdatedEvent.builder().itemId(itemId1).finalPrice(new BigDecimal("110.00")).basePrice(new BigDecimal("100.00")).build();
        when(pricingEngineService.calculatePrice(any(EnrichedAggregatedMetric.class), eq(Optional.empty())))
            .thenReturn(Optional.of(expectedEvent));

        // Act: Send a metric event
        MetricEvent metric1 = MetricEvent.builder().itemId(itemId1).metricType("VIEW").timestamp(baseTime).details(Map.of("count", 2L)).build();
        demandMetricsInputTopic.pipeInput(itemId1Str, metric1, baseTime.toEpochMilli());
        testDriver.advanceWallClockTime(Duration.ofMinutes(6));

        // Assert: PriceUpdatedEvent published to external and internal topics
        TestRecord<String, PriceUpdatedEvent> update1 = priceUpdatedOutputTopic.readRecord();
        assertThat(update1).isNotNull();
        assertThat(update1.key()).isEqualTo(itemId1Str);
        assertThat(update1.value().getFinalPrice()).isEqualByComparingTo("110.00");

        TestRecord<String, PriceUpdatedEvent> internalUpdate1 = internalLastPriceOutputTopic.readRecord();
        assertThat(internalUpdate1).isNotNull();
        assertThat(internalUpdate1.value().getFinalPrice()).isEqualByComparingTo("110.00");

        // Assert: DLT for invalid metric
        MetricEvent invalidEvent = MetricEvent.builder().itemId(null).metricType("INVALID_NULL_ITEMID").timestamp(baseTime).build();
        demandMetricsInputTopic.pipeInput("invalid_key", invalidEvent, baseTime.toEpochMilli());
        TestRecord<String, MetricEvent> dltRecord = demandMetricsDltOutputTopic.readRecord();
        assertThat(dltRecord).isNotNull();
        assertThat(dltRecord.key()).isEqualTo("invalid_key");
        assertThat(dltRecord.value().getMetricType()).isEqualTo("INVALID_NULL_ITEMID");

        assertThat(demandMetricsDltOutputTopic.isEmpty()).isTrue();
    }

    @Test
    void testRuleListAggregationAndServiceCall() throws Exception {
        UUID itemId = UUID.randomUUID();
        String itemIdStr = itemId.toString();
        Instant now = Instant.now();

        ItemBasePriceEvent basePriceEvent = ItemBasePriceEvent.builder().itemId(itemId).basePrice(new BigDecimal("50.00")).eventTimestamp(now).build();
        internalBasePricesInputTopic.pipeInput(itemIdStr, basePriceEvent);

        DynamicPricingRuleDto rule1 = DynamicPricingRuleDto.builder().id(UUID.randomUUID()).itemId(itemId).ruleType("TYPE_A").parameters(Map.of("p1", "v1")).enabled(true).build();
        DynamicPricingRuleDto rule2 = DynamicPricingRuleDto.builder().id(UUID.randomUUID()).itemId(itemId).ruleType("TYPE_B").parameters(Map.of("p2", "v2")).enabled(true).build();
        internalRulesByItemIdInputTopic.pipeInput(itemIdStr, rule1);
        internalRulesByItemIdInputTopic.pipeInput(itemIdStr, rule2);

        DynamicPricingRuleDto rule1Updated = DynamicPricingRuleDto.builder().id(rule1.getId()).itemId(itemId).ruleType("TYPE_A_UPDATED").parameters(Map.of("p1", "v1_updated")).enabled(true).build();
        internalRulesByItemIdInputTopic.pipeInput(itemIdStr, rule1Updated); // This should replace rule1 in the list

        testDriver.advanceWallClockTime(Duration.ofSeconds(5));

        ArgumentCaptor<EnrichedAggregatedMetric> enrichedCaptor = ArgumentCaptor.forClass(EnrichedAggregatedMetric.class);
        when(pricingEngineService.calculatePrice(enrichedCaptor.capture(), any(Optional.class)))
            .thenReturn(Optional.empty()); // We only care about the captured input here

        MetricEvent metric = MetricEvent.builder().itemId(itemId).metricType("VIEW").timestamp(now).details(Map.of("count", 10L)).build();
        demandMetricsInputTopic.pipeInput(itemIdStr, metric, now.toEpochMilli());
        testDriver.advanceWallClockTime(Duration.ofMinutes(6));

        verify(pricingEngineService, Mockito.timeout(5000).atLeastOnce()).calculatePrice(any(EnrichedAggregatedMetric.class), any(Optional.class));

        EnrichedAggregatedMetric capturedEnriched = enrichedCaptor.getAllValues().stream()
            .filter(e -> e.getAggregatedMetric().getItemId().equals(itemId))
            .findFirst().orElse(null);

        assertThat(capturedEnriched).isNotNull();
        assertThat(capturedEnriched.getRuleDtos()).isNotNull();
        // Current aggregator replaces by ruleId, so only rule1Updated and rule2 should be in list
        assertThat(capturedEnriched.getRuleDtos()).hasSize(2);
        assertThat(capturedEnriched.getRuleDtos()).extracting(DynamicPricingRuleDto::getId).containsExactlyInAnyOrder(rule1.getId(), rule2.getId());
        assertThat(capturedEnriched.getRuleDtos()).filteredOn(r -> r.getId().equals(rule1.getId())).extracting(DynamicPricingRuleDto::getRuleType).containsExactly("TYPE_A_UPDATED");
    }

    @Test
    void testProcessingErrorDLT_SendsContextToDlt() throws Exception {
        UUID itemId = UUID.randomUUID();
        String itemIdStr = itemId.toString();
        Instant now = Instant.now();

        ItemBasePriceEvent basePriceEvent = ItemBasePriceEvent.builder().itemId(itemId).basePrice(new BigDecimal("100.00")).eventTimestamp(now).build();
        internalBasePricesInputTopic.pipeInput(itemIdStr, basePriceEvent);
        testDriver.advanceWallClockTime(Duration.ofSeconds(1));

        when(pricingEngineService.calculatePrice(any(EnrichedAggregatedMetric.class), any(Optional.class)))
               .thenThrow(new RuntimeException("Simulated processing error in service"));

        MetricEvent metric = MetricEvent.builder().itemId(itemId).metricType("VIEW").timestamp(now).details(Map.of("count", 1L)).build();
        demandMetricsInputTopic.pipeInput(itemIdStr, metric, now.toEpochMilli());
        testDriver.advanceWallClockTime(Duration.ofMinutes(6));

        TestRecord<String, String> dltRecord = processingErrorsDltOutputTopic.readRecord();
        assertThat(dltRecord).isNotNull();
        assertThat(dltRecord.key()).isEqualTo(itemIdStr);

        String dltPayload = dltRecord.value();
        assertThat(dltPayload).contains("\"itemId\":\"" + itemId.toString() + "\""); // Check if itemId from AggregatedMetric part
        assertThat(dltPayload).contains("\"metricCount\":1");
        // Check if it's the JSON of EnrichedAggregatedMetricWithLastPrice
        assertThat(dltPayload).contains("\"enrichedAggregatedMetric\"");
        assertThat(dltPayload).contains("\"lastPriceEvent\"");

        EnrichedAggregatedMetric.EnrichedAggregatedMetricWithLastPrice dltdErrorContext =
             testObjectMapper.readValue(dltPayload, EnrichedAggregatedMetric.EnrichedAggregatedMetricWithLastPrice.class);
        assertThat(dltdErrorContext.getEnrichedAggregatedMetric().getAggregatedMetric().getItemId()).isEqualTo(itemId);

        assertThat(priceUpdatedOutputTopic.isEmpty()).isTrue();
        assertThat(internalLastPriceOutputTopic.isEmpty()).isTrue();
    }
}
