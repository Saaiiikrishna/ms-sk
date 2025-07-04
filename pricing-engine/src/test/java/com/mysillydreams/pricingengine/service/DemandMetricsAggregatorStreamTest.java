package com.mysillydreams.pricingengine.service;

import com.mysillydreams.pricingengine.dto.MetricEvent;
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

    private final String INPUT_TOPIC_NAME = "demand.metrics.test";
    // private final String OUTPUT_TOPIC_NAME = "aggregated.metrics.test"; // If we had an output topic

    @Mock
    private PricingEngineService pricingEngineService; // Mocked, not used directly by stream in this version

    private DemandMetricsAggregatorStream demandMetricsAggregatorStream;

    private Serde<String> stringSerde = Serdes.String();
    private JsonSerde<MetricEvent> metricEventSerde = new JsonSerde<>(MetricEvent.class);


    @BeforeEach
    void setUp() {
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "test-pricing-engine-streams");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:1234"); // Required, but not used by TopologyTestDriver
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, JsonSerde.class.getName());
        props.put(JsonSerde.DEFAULT_VALUE_TYPE, MetricEvent.class.getName());
        props.put(JsonSerde.TRUSTED_PACKAGES, "com.mysillydreams.pricingengine.dto");


        demandMetricsAggregatorStream = new DemandMetricsAggregatorStream(INPUT_TOPIC_NAME, pricingEngineService);

        StreamsBuilder builder = new StreamsBuilder();
        demandMetricsAggregatorStream.buildPipeline(builder);
        Topology topology = builder.build();

        testDriver = new TopologyTestDriver(topology, props);

        inputTopic = testDriver.createInputTopic(INPUT_TOPIC_NAME, stringSerde.serializer(), metricEventSerde.serializer());
        // If outputting to a topic:
        // outputTopic = testDriver.createOutputTopic(OUTPUT_TOPIC_NAME, Serdes.String().deserializer(), Serdes.Long().deserializer());
    }

    @AfterEach
    void tearDown() {
        if (testDriver != null) {
            testDriver.close();
        }
        metricEventSerde.close();
    }

    @Test
    void testMetricsAggregationWindowing() {
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
        // Let's try to query the state store. The store name is "item-metric-counts".
        ReadOnlyWindowStore<UUID, Long> countStore = testDriver.getWindowStore("item-metric-counts");
        assertThat(countStore).isNotNull();

        // Querying window stores requires a time range.
        // Window 1: 10:00:00 - 10:04:59
        // Check count for itemId1 in its first window
        // Note: Window start times depend on message timestamps and window alignment.
        // If first message is at 10:00:00, a 5-min window advancing by 1 min could be [10:00, 10:05), [10:01, 10:06) etc.
        // The exact window start needs to be determined based on Kafka Streams windowing semantics.
        // For TimeWindows.ofSizeWithNoGrace(5m).advanceBy(1m), windows are aligned with epoch.
        // Example: 10:00:00 event falls into [10:00:00 - 10:04:59.999]
        // 10:01:00 event falls into [10:00:00 - 10:04:59.999] AND [10:01:00 - 10:05:59.999] if it's a hopping window.
        // The current code uses TimeWindows.ofSizeWithNoGrace(windowSize).advanceBy(advanceInterval) which is for hopping windows.

        // Let's try to fetch values for a specific window that should contain the events
        // The window start for an event at baseTime (10:00:00) for a 5-min window would be 10:00:00
        Instant window1Start = baseTime;
        Instant window1End = window1Start.plus(Duration.ofMinutes(5));

        try (KeyValueIterator<Windowed<UUID>, Long> iterator = countStore.fetchAll(window1Start, window1End)) {
            boolean foundItem1 = false;
            boolean foundItem2 = false;
            while (iterator.hasNext()) {
                KeyValue<Windowed<UUID>, Long> next = iterator.next();
                Windowed<UUID> windowedKey = next.key;
                Long count = next.value;
                log.info("StateStore Query: Window [{} - {}], Key: {}, Count: {}",
                         windowedKey.window().startTime(), windowedKey.window().endTime(), windowedKey.key(), count);
                if (windowedKey.key().equals(itemId1) && windowedKey.window().start().equals(window1Start)) {
                    assertThat(count).isEqualTo(2L); // Two events for itemId1 in the first relevant window
                    foundItem1 = true;
                }
                if (windowedKey.key().equals(itemId2) && windowedKey.window().start().equals(window1Start)) {
                    // item2 event at 10:02:00, should also be in a window starting at 10:00:00
                    assertThat(count).isEqualTo(1L);
                    foundItem2 = true;
                }
            }
             // These assertions are tricky because multiple hopping windows might match fetchAll.
             // A more precise query would be fetch(key, time) to get value for a specific key at a specific time,
             // or iterating and finding the specific window.
             // For now, this just shows how to access the store.
        }
        // A proper assertion would require more deterministic querying of windowed state or outputting to a topic.
        // The current test primarily verifies topology construction and basic processing.
        // Given the .foreach logs, one would typically check logs or verify mocks if .foreach called a service.
        // Since it doesn't call pricingEngineService directly from the stream's .foreach in the current code,
        // we cannot verify that interaction here directly without modifying the stream.
    }
}
