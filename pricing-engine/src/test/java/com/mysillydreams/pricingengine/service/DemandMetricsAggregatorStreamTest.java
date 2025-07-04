package com.mysillydreams.pricingengine.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mysillydreams.pricingengine.dto.DynamicPricingRuleDto;
import com.mysillydreams.pricingengine.dto.ItemBasePriceEvent;
import com.mysillydreams.pricingengine.dto.MetricEvent;
import com.mysillydreams.pricingengine.dto.PriceOverrideDto;
import com.mysillydreams.pricingengine.dto.PriceUpdatedEvent; // Added for output topic
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.*;
import java.util.List; // Added
import java.math.BigDecimal; // Added
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
    // private TestInputTopic<String, MetricEvent> inputTopic; // Replaced by demandMetricsInputTopic
    private TestOutputTopic<String, MetricEvent> demandMetricsDltOutputTopic;
    private TestOutputTopic<String, PriceUpdatedEvent> priceUpdatedOutputTopic; // For final price updates
    private TestOutputTopic<String, PriceUpdatedEvent> internalLastPriceOutputTopic; // For internal state updates

    private final String DEMAND_METRICS_TOPIC = "demand.metrics.test";
    private final String DEMAND_METRICS_DLT_TOPIC = "demand.metrics.dlt.test";
    private final String INTERNAL_RULES_BY_ITEMID_TOPIC = "internal.rules-by-itemid.v1.test"; // Updated name
    private final String INTERNAL_OVERRIDES_BY_ITEMID_TOPIC = "internal.overrides-by-itemid.v1.test"; // Updated name
    private final String INTERNAL_BASE_PRICES_TOPIC = "internal.item.baseprices.v1.test";
    private final String INTERNAL_LAST_PUBLISHED_PRICES_TOPIC = "internal.last-published-prices.v1.test"; // Added
    private final String EXTERNAL_PRICE_UPDATED_TOPIC = "catalog.price.updated.test"; // Added


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
    private JsonSerde<PriceUpdatedEvent> priceUpdatedEventSerde; // Added
    private JsonSerde<List<DynamicPricingRuleDto>> listOfRuleDtoSerde; // Added

    private TestInputTopic<String, MetricEvent> demandMetricsInputTopic;
    // Input topics for populating GlobalKTables / KTables
    private TestInputTopic<String, DynamicPricingRuleDto> internalRulesByItemIdInputTopic; // Changed from UUID key
    private TestInputTopic<String, PriceOverrideDto> internalOverridesByItemIdInputTopic; // Changed from UUID key
    private TestInputTopic<String, ItemBasePriceEvent> internalBasePricesInputTopic;
    private TestInputTopic<String, PriceUpdatedEvent> internalLastPublishedPricesInputTopic; // Added


    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        metricEventSerde = new JsonSerde<>(MetricEvent.class, objectMapper);
        ruleDtoSerde = new JsonSerde<>(DynamicPricingRuleDto.class, objectMapper);
        overrideDtoSerde = new JsonSerde<>(PriceOverrideDto.class, objectMapper);
        basePriceEventSerde = new JsonSerde<>(ItemBasePriceEvent.class, objectMapper);
        priceUpdatedEventSerde = new JsonSerde<>(PriceUpdatedEvent.class, objectMapper); // Init
        listOfRuleDtoSerde = new JsonSerde<>(new com.fasterxml.jackson.core.type.TypeReference<List<DynamicPricingRuleDto>>() {}, objectMapper); // Init


        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "test-pricing-engine-streams");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:1234");
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, JsonSerde.class.getName());
        props.put(JsonSerde.TRUSTED_PACKAGES, "com.mysillydreams.pricingengine.dto");

        demandMetricsAggregatorStream = new DemandMetricsAggregatorStream(
                pricingEngineService,
                dltKafkaTemplate,
                stringSerde,
                // uuidSerde, // Removed as String keys are primarily used for joins now
                metricEventSerde,
                ruleDtoSerde,
                overrideDtoSerde,
                basePriceEventSerde,
                priceUpdatedEventSerde, // Pass the Serde
                listOfRuleDtoSerde   // Pass the Serde
        );

        // Manually set @Value fields for testing topic names
        ReflectionTestUtils.setField(demandMetricsAggregatorStream, "demandMetricsTopic", DEMAND_METRICS_TOPIC);
        ReflectionTestUtils.setField(demandMetricsAggregatorStream, "internalRulesByItemIdTopic", INTERNAL_RULES_BY_ITEMID_TOPIC);
        ReflectionTestUtils.setField(demandMetricsAggregatorStream, "internalOverridesByItemIdTopic", INTERNAL_OVERRIDES_BY_ITEMID_TOPIC);
        ReflectionTestUtils.setField(demandMetricsAggregatorStream, "internalBasePricesTopic", INTERNAL_BASE_PRICES_TOPIC);
        ReflectionTestUtils.setField(demandMetricsAggregatorStream, "demandMetricsDltTopic", DEMAND_METRICS_DLT_TOPIC);
        ReflectionTestUtils.setField(demandMetricsAggregatorStream, "internalLastPublishedPricesTopic", INTERNAL_LAST_PUBLISHED_PRICES_TOPIC);
        ReflectionTestUtils.setField(demandMetricsAggregatorStream, "externalPriceUpdatedTopic", EXTERNAL_PRICE_UPDATED_TOPIC);


        StreamsBuilder builder = new StreamsBuilder();
        demandMetricsAggregatorStream.buildPipeline(builder);
        Topology topology = builder.build();
        log.info("Topology for test:\n{}", topology.describe());

        testDriver = new TopologyTestDriver(topology, props);

        demandMetricsInputTopic = testDriver.createInputTopic(DEMAND_METRICS_TOPIC, stringSerde.serializer(), metricEventSerde.serializer());
        demandMetricsDltOutputTopic = testDriver.createOutputTopic(DEMAND_METRICS_DLT_TOPIC, stringSerde.deserializer(), metricEventSerde.deserializer());
        priceUpdatedOutputTopic = testDriver.createOutputTopic(EXTERNAL_PRICE_UPDATED_TOPIC, stringSerde.deserializer(), priceUpdatedEventSerde.deserializer());
        internalLastPriceOutputTopic = testDriver.createOutputTopic(INTERNAL_LAST_PUBLISHED_PRICES_TOPIC, stringSerde.deserializer(), priceUpdatedEventSerde.deserializer());

        // Topics for populating KTables/GlobalKTables (keyed by String itemId)
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
        priceUpdatedEventSerde.close(); // Close new serde
        listOfRuleDtoSerde.close();   // Close new serde
    }

    @Test
    void testMetricsAggregationWindowingAndDltRouting() {
        UUID itemId1 = UUID.randomUUID();
        UUID itemId2 = UUID.randomUUID();
        Instant baseTime = Instant.parse("2023-01-01T10:00:00Z");

        // Populate GKTs/KTables first
        // Rule for itemId1
        Map<String,Object> ruleParams = new HashMap<>();
        ruleParams.put("threshold", 1L);
        ruleParams.put("adjustmentPercentage", 0.1); // +10%
        DynamicPricingRuleDto rule1 = DynamicPricingRuleDto.builder().id(UUID.randomUUID()).itemId(itemId1).ruleType("VIEW_COUNT_THRESHOLD").parameters(ruleParams).enabled(true).build();
        internalRulesByItemIdInputTopic.pipeInput(itemId1.toString(), rule1);

        // Base price for itemId1
        ItemBasePriceEvent basePrice1 = ItemBasePriceEvent.builder().itemId(itemId1).basePrice(new BigDecimal("100.00")).eventTimestamp(Instant.now()).build();
        internalBasePricesInputTopic.pipeInput(itemId1.toString(), basePrice1);

        // Metric for itemId1 - should trigger rule
        demandMetricsInputTopic.pipeInput(itemId1.toString(), MetricEvent.builder().itemId(itemId1).metricType("VIEW").timestamp(baseTime).details(Map.of("count", 2L)).build(), baseTime.toEpochMilli());
        testDriver.advanceWallClockTime(Duration.ofMinutes(6)); // Advance time to close window

        // Assert PriceUpdatedEvent
        TestRecord<String, PriceUpdatedEvent> priceUpdateRecord = priceUpdatedOutputTopic.readRecord();
        assertThat(priceUpdateRecord).isNotNull();
        assertThat(priceUpdateRecord.key()).isEqualTo(itemId1.toString());
        assertThat(priceUpdateRecord.value().getFinalPrice()).isEqualByComparingTo("110.00"); // 100 * (1 + 0.1)

        // Assert internal last price topic is updated
        TestRecord<String, PriceUpdatedEvent> lastPriceRecord = internalLastPriceOutputTopic.readRecord();
        assertThat(lastPriceRecord).isNotNull();
        assertThat(lastPriceRecord.value().getFinalPrice()).isEqualByComparingTo("110.00");


        // Test DLT routing
        MetricEvent invalidEvent = MetricEvent.builder().itemId(null).metricType("INVALID_VIEW").timestamp(baseTime).build();
        demandMetricsInputTopic.pipeInput("somekey_invalid", invalidEvent, baseTime.toEpochMilli());
        TestRecord<String, MetricEvent> dltRecord = demandMetricsDltOutputTopic.readRecord();
        assertThat(dltRecord).isNotNull();
        assertThat(dltRecord.key()).isEqualTo("somekey_invalid");
        assertThat(dltRecord.value().getMetricType()).isEqualTo("INVALID_VIEW");

        assertThat(demandMetricsDltOutputTopic.isEmpty()).isTrue();
        assertThat(priceUpdatedOutputTopic.isEmpty()).isTrue(); // No more valid price updates from this invalid event
        assertThat(internalLastPriceOutputTopic.isEmpty()).isTrue();
    }
}
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
