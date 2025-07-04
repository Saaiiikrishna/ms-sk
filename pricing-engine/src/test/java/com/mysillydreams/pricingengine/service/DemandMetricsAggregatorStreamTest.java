package com.mysillydreams.pricingengine.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mysillydreams.pricingengine.dto.DynamicPricingRuleDto;
import com.mysillydreams.pricingengine.dto.ItemBasePriceEvent;
import com.mysillydreams.pricingengine.dto.MetricEvent;
import com.mysillydreams.pricingengine.dto.PriceOverrideDto;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.*;
import org.apache.kafka.streams.kstream.Windowed;
import org.apache.kafka.streams.test.TestRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.serializer.JsonSerde;

import java.time.Instant;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
// import static org.mockito.Mockito.*; // If interactions with PricingEngineService were tested here

@ExtendWith(MockitoExtension.class)
class DemandMetricsAggregatorStreamTest {

    private TopologyTestDriver testDriver;
    private TestInputTopic<String, MetricEvent> inputTopic;
    // The KTable output is internal to the stream's logic in the current implementation,
    // and it logs via .foreach. For testing the aggregation result directly,
    // we would typically sink the KTable's toStream() output to another topic.
    // Or, if the .foreach calls a mockable service, we could verify that.
    // For this test, we'll focus on building the topology and ensuring it runs.
    // A more thorough test would require an output topic for the aggregated counts.

    private final String DEMAND_METRICS_TOPIC = "demand.metrics.test";
    private final String DEMAND_METRICS_DLT_TOPIC = "demand.metrics.dlt.test";
    // Internal topics for GKTs - names should match those used in DemandMetricsAggregatorStream constructor for @Value
    private final String INTERNAL_RULES_TOPIC = "internal.rules.v1.test";
    private final String INTERNAL_OVERRIDES_TOPIC = "internal.overrides.v1.test";
    private final String INTERNAL_BASE_PRICES_TOPIC = "internal.item.baseprices.v1.test";


    @Mock
    private PricingEngineService pricingEngineService;
    @Mock
    private KafkaTemplate<String, String> dltKafkaTemplate; // Mock for verifying DLT sends

    private DemandMetricsAggregatorStream demandMetricsAggregatorStream;

    private Serde<String> stringSerde = Serdes.String();
    private Serde<UUID> uuidSerde = Serdes.UUID(); // Ensure this is available
    private JsonSerde<MetricEvent> metricEventSerde;
    private JsonSerde<DynamicPricingRuleDto> ruleDtoSerde;
    private JsonSerde<PriceOverrideDto> overrideDtoSerde;
    private JsonSerde<ItemBasePriceEvent> basePriceEventSerde;

    private TestInputTopic<String, MetricEvent> demandMetricsInputTopic;
    private TestOutputTopic<String, MetricEvent> demandMetricsDltOutputTopic;
    // Input topics for populating GlobalKTables
    private TestInputTopic<UUID, DynamicPricingRuleDto> internalRulesInputTopic;
    private TestInputTopic<UUID, PriceOverrideDto> internalOverridesInputTopic;
    private TestInputTopic<String, ItemBasePriceEvent> internalBasePricesInputTopic;


    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules(); // For proper Instant/BigDecimal handling
        metricEventSerde = new JsonSerde<>(MetricEvent.class, objectMapper);
        ruleDtoSerde = new JsonSerde<>(DynamicPricingRuleDto.class, objectMapper);
        overrideDtoSerde = new JsonSerde<>(PriceOverrideDto.class, objectMapper);
        basePriceEventSerde = new JsonSerde<>(ItemBasePriceEvent.class, objectMapper);


        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "test-pricing-engine-streams");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:1234");
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        // Default value serde can be JsonSerde, but specific Serdes are better for typed topics
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, JsonSerde.class.getName());
        props.put(JsonSerde.TRUSTED_PACKAGES, "com.mysillydreams.pricingengine.dto");

        demandMetricsAggregatorStream = new DemandMetricsAggregatorStream(
                pricingEngineService,
                dltKafkaTemplate, // Pass the mock
                stringSerde,
                uuidSerde, // Pass the Serde bean
                metricEventSerde,
                ruleDtoSerde, // Pass specific Serdes
                overrideDtoSerde,
                basePriceEventSerde
        );
        // Manually set @Value fields for testing
        ReflectionTestUtils.setField(demandMetricsAggregatorStream, "demandMetricsTopic", DEMAND_METRICS_TOPIC);
        ReflectionTestUtils.setField(demandMetricsAggregatorStream, "internalRulesTopic", INTERNAL_RULES_TOPIC);
        ReflectionTestUtils.setField(demandMetricsAggregatorStream, "internalOverridesTopic", INTERNAL_OVERRIDES_TOPIC);
        ReflectionTestUtils.setField(demandMetricsAggregatorStream, "internalBasePricesTopic", INTERNAL_BASE_PRICES_TOPIC);
        ReflectionTestUtils.setField(demandMetricsAggregatorStream, "demandMetricsDltTopic", DEMAND_METRICS_DLT_TOPIC);


        StreamsBuilder builder = new StreamsBuilder();
        demandMetricsAggregatorStream.buildPipeline(builder);
        Topology topology = builder.build();
        log.info("Topology for test:\n{}", topology.describe());

        testDriver = new TopologyTestDriver(topology, props);

        demandMetricsInputTopic = testDriver.createInputTopic(DEMAND_METRICS_TOPIC, stringSerde.serializer(), metricEventSerde.serializer());
        demandMetricsDltOutputTopic = testDriver.createOutputTopic(DEMAND_METRICS_DLT_TOPIC, stringSerde.deserializer(), metricEventSerde.deserializer());

        internalRulesInputTopic = testDriver.createInputTopic(INTERNAL_RULES_TOPIC, uuidSerde.serializer(), ruleDtoSerde.serializer());
        internalOverridesInputTopic = testDriver.createInputTopic(INTERNAL_OVERRIDES_TOPIC, uuidSerde.serializer(), overrideDtoSerde.serializer());
        internalBasePricesInputTopic = testDriver.createInputTopic(INTERNAL_BASE_PRICES_TOPIC, stringSerde.serializer(), basePriceEventSerde.serializer());
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
        // stringSerde and uuidSerde are from Serdes factory, no close needed.
    }

    @Test
    void testMetricsAggregationWindowingAndDltRouting() { // Renamed for clarity
        UUID itemId1 = UUID.randomUUID();
        UUID itemId2 = UUID.randomUUID();
        Instant baseTime = Instant.parse("2023-01-01T10:00:00Z");

        // Window 1 for item1: 10:00:00 - 10:04:59 (assuming 5 min window)
        inputTopic.pipeInput(itemId1.toString(), MetricEvent.builder().itemId(itemId1).metricType("VIEW").timestamp(baseTime).build(), baseTime.toEpochMilli());
        inputTopic.pipeInput(itemId1.toString(), MetricEvent.builder().itemId(itemId1).metricType("VIEW").timestamp(baseTime.plusSeconds(60)).build(), baseTime.plusSeconds(60).toEpochMilli());

        // Window 1 for item2
        inputTopic.pipeInput(itemId2.toString(), MetricEvent.builder().itemId(itemId2).metricType("VIEW").timestamp(baseTime.plusSeconds(120)).build(), baseTime.plusSeconds(120).toEpochMilli());

        // Advance stream time to trigger window processing if needed by .foreach action.
        // The .foreach in the current implementation logs.
        // To properly test the *output* of the aggregation, the KTable should be materialized
        // or its .toStream() output should be sent to another topic that can be asserted.
        // For now, this test ensures the topology builds and can process messages.
        // A ReadOnlyWindowStore could also be used to query the KTable state.

        testDriver.advanceWallClockTime(Duration.ofMinutes(6)); // Advance time past the first window

        // How to assert the results of .count() which materializes to a KTable and then logs via .foreach?
        // 1. Modify stream to output to a topic: Preferred for black-box testing.
        // 2. Query the state store: testDriver.getKeyValueStore("item-metric-counts") - requires store to be queryable.
        // 3. If .foreach calls a mockable component (it calls log directly now), verify interactions.

        // For this iteration, we are mostly testing that the pipeline can be built and processes messages without error.
        // The log output from the .foreach would be visible in test logs but not programmatically asserted here.

        // Query the state store for item-metric-counts-store
        ReadOnlyWindowStore<UUID, Long> countStore = testDriver.getWindowStore("item-metric-counts-store"); // Corrected store name
        assertThat(countStore).isNotNull();

        // Example: Check count for itemId1 in its first relevant window
        // This part remains complex due to hopping window exact time boundaries,
        // but the principle is to verify the state store contains expected aggregated data.
        // For simplicity, we'll just check if any records are there for now for these items.
        // A more robust test would pinpoint exact window start/end times.
        long itemId1Count = 0;
        try (KeyValueIterator<Windowed<UUID>, Long> iterator = countStore.fetch(itemId1, baseTime.minus(Duration.ofMinutes(1)), baseTime.plus(Duration.ofMinutes(5)))) {
            if(iterator.hasNext()) itemId1Count = iterator.next().value;
        }
        assertThat(itemId1Count).isEqualTo(2L);

        long itemId2Count = 0;
        try (KeyValueIterator<Windowed<UUID>, Long> iterator = countStore.fetch(itemId2, baseTime.minus(Duration.ofMinutes(1)), baseTime.plus(Duration.ofMinutes(5)))) {
             if(iterator.hasNext()) itemId2Count = iterator.next().value;
        }
        assertThat(itemId2Count).isEqualTo(1L);


        // Test DLT routing for an event with null itemId
        MetricEvent invalidEvent = MetricEvent.builder().itemId(null).metricType("INVALID_VIEW").timestamp(baseTime).build();
        demandMetricsInputTopic.pipeInput("somekey", invalidEvent, baseTime.toEpochMilli());

        TestRecord<String, MetricEvent> dltRecord = demandMetricsDltOutputTopic.readRecord();
        assertThat(dltRecord).isNotNull();
        assertThat(dltRecord.key()).isEqualTo("somekey");
        assertThat(dltRecord.value().getMetricType()).isEqualTo("INVALID_VIEW");

        // Verify that dltKafkaTemplate.send was called (if not using .to() directly)
        // In the current stream, .to() is used, so this verification is against the output topic.
        // If a KafkaTemplate was injected and used, then:
        // verify(dltKafkaTemplate).send(eq(DEMAND_METRICS_DLT_TOPIC), eq("somekey"), anyString());

        // Ensure no more records in DLT
        assertThat(demandMetricsDltOutputTopic.isEmpty()).isTrue();
    }
}
