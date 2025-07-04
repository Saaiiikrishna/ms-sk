package com.mysillydreams.pricingengine.service;

import com.mysillydreams.pricingengine.dto.MetricEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.support.serializer.JsonSerde;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class DemandMetricsAggregatorStream {

    @Value("${topics.demandMetrics}")
    private String demandMetricsTopic;

    private final PricingEngineService pricingEngineService;
    // ObjectMapper could be injected if complex JsonSerde configuration is needed here,
    // but KafkaConfig sets up a default JsonSerde for MetricEvent.

    // Define SerDes that will be used in this stream if not relying on defaults from KafkaConfig
    private final Serde<String> stringSerde = Serdes.String();
    // MetricEventSerde will be picked up from KafkaStreamsConfiguration if configured as default value serde
    // Or, it can be explicitly created: new JsonSerde<>(MetricEvent.class)

    @Autowired // Autowire StreamsBuilder bean created by @EnableKafkaStreams
    public void buildPipeline(StreamsBuilder streamsBuilder) {
        log.info("Building Kafka Streams pipeline for demand metrics aggregation on topic: {}", demandMetricsTopic);

        // KStream from the demand metrics topic
        // Assumes MetricEvent is the value type from KafkaConfig's default JsonSerde
        KStream<String, MetricEvent> metricEventKStream = streamsBuilder
                .stream(demandMetricsTopic, Consumed.with(stringSerde, null)); // null for valueSerde uses default

        // 1. Re-key by itemId (assuming key might be different or null)
        KStream<UUID, MetricEvent> rekeyedStream = metricEventKStream
                .selectKey((key, value) -> {
                    if (value != null && value.getItemId() != null) {
                        return value.getItemId();
                    }
                    log.warn("MetricEvent with null itemId or value: {}", value);
                    return UUID.randomUUID(); // Fallback key, consider filtering out these records
                })
                .filter((key, value) -> value != null && value.getItemId() != null); // Ensure bad records are filtered

        // 2. Windowed Aggregation
        // Example: Count metric events per item ID over a 5-minute sliding window, advancing every 1 minute.
        // Adjust window size and advance interval as per requirements.
        Duration windowSize = Duration.ofMinutes(5);
        Duration advanceInterval = Duration.ofMinutes(1);

        // Define a simple aggregation: count of events per item in window
        // For more complex aggregations, define an AggregatedMetric DTO and an Initializer/Aggregator
        TimeWindowedKStream<UUID, MetricEvent> windowedStream = rekeyedStream
                .groupByKey(Grouped.with(Serdes.UUID(), null)) // Group by UUID itemId, value serde from default
                .windowedBy(TimeWindows.ofSizeWithNoGrace(windowSize).advanceBy(advanceInterval));

        // For this example, let's count the number of metric events per item ID in each window.
        KTable<Windowed<UUID>, Long> aggregatedCounts = windowedStream
                .count(Materialized.as("item-metric-counts")); // State store name

        // 3. Process aggregated results (e.g., log or call pricing service)
        aggregatedCounts.toStream()
                .foreach((windowedItemId, count) -> {
                    UUID itemId = windowedItemId.key();
                    long windowStart = windowedItemId.window().start();
                    long windowEnd = windowedItemId.window().end();
                    log.info("Window: [{} - {}], Item ID: {}, Aggregated Metric Count: {}",
                            Instant.ofEpochMilli(windowStart), Instant.ofEpochMilli(windowEnd), itemId, count);

                    // TODO: This is where you'd typically call the pricing logic.
                    // The `AggregatedMetric` needs to be more than just a count for real pricing.
                    // For now, this demonstrates the windowing and aggregation.
                    // The `pricingEngineService.calculateAndPublishPrice(itemId, aggregatedMetrics)`
                    // will be called from here or a subsequent processor in a more complete implementation.
                    // For this step, we are just setting up the aggregation.
                    // The call to pricing logic will be part of Step 3.3.

                    // Example of how it *could* be wired (needs proper AggregatedMetric type):
                    // AggregatedMetric metrics = new AggregatedMetric(itemId, count, windowStart, windowEnd);
                    // pricingEngineService.calculateAndPublishPrice(itemId, metrics);
                });

        log.info("Kafka Streams pipeline for demand metrics aggregation built.");
    }
}
