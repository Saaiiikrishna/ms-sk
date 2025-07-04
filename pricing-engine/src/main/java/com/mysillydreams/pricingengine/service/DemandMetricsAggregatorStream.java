package com.mysillydreams.pricingengine.service;

import com.mysillydreams.pricingengine.domain.DynamicPricingRuleEntity; // For mapping DTO to Entity if needed
import com.mysillydreams.pricingengine.domain.PriceOverrideEntity;   // For mapping DTO to Entity if needed
import com.mysillydreams.pricingengine.dto.AggregatedMetric;
import com.mysillydreams.pricingengine.dto.DynamicPricingRuleDto;
import com.mysillydreams.pricingengine.dto.ItemBasePriceEvent;
import com.mysillydreams.pricingengine.dto.MetricEvent;
import com.mysillydreams.pricingengine.dto.PriceOverrideDto;
import com.mysillydreams.pricingengine.dto.PriceUpdatedEvent; // Added for KTable
import com.mysillydreams.pricingengine.dto.EnrichedAggregatedMetric;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate; // Added for DLT
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@Slf4j
// @RequiredArgsConstructor - Manual constructor for Serde injection
public class DemandMetricsAggregatorStream {

    @Value("${topics.demandMetrics}")
    private String demandMetricsTopic;
    // Remove old internal topics, replaced by itemId keyed topics
    // @Value("${topics.internalRules}") private String internalRulesTopic;
    // @Value("${topics.internalOverrides}") private String internalOverridesTopic;
    @Value("${topics.internalRulesByItemId}")
    private String internalRulesByItemIdTopic;
    @Value("${topics.internalOverridesByItemId}")
    private String internalOverridesByItemIdTopic;
    @Value("${topics.internalBasePrices}")
    private String internalBasePricesTopic;
    @Value("${topics.demandMetricsDlt}")
    private String demandMetricsDltTopic;
    @Value("${topics.internalLastPublishedPrices}") // Added
    private String internalLastPublishedPricesTopic;
    @Value("${topics.priceUpdated}") // External topic for final price updates
    private String externalPriceUpdatedTopic;


    private final PricingEngineService pricingEngineService;
    private final KafkaTemplate<String, String> dltKafkaTemplate;
    private final Serde<String> stringSerde;
    private final Serde<MetricEvent> metricEventSerde;
    private final Serde<DynamicPricingRuleDto> dynamicPricingRuleDtoSerde;
    private final Serde<PriceOverrideDto> priceOverrideDtoSerde;
    private final Serde<ItemBasePriceEvent> itemBasePriceEventSerde;
    private final Serde<PriceUpdatedEvent> priceUpdatedEventSerde; // Added


    public DemandMetricsAggregatorStream(
            PricingEngineService pricingEngineService,
            KafkaTemplate<String, String> dltKafkaTemplate,
            Serde<String> stringSerde,
            Serde<MetricEvent> metricEventSerde,
            Serde<DynamicPricingRuleDto> dynamicPricingRuleDtoSerde,
            Serde<PriceOverrideDto> priceOverrideDtoSerde,
            Serde<ItemBasePriceEvent> itemBasePriceEventSerde,
            Serde<PriceUpdatedEvent> priceUpdatedEventSerde) { // Added
        this.pricingEngineService = pricingEngineService;
        this.dltKafkaTemplate = dltKafkaTemplate;
        this.stringSerde = stringSerde;
        this.metricEventSerde = metricEventSerde;
        this.dynamicPricingRuleDtoSerde = dynamicPricingRuleDtoSerde;
        this.priceOverrideDtoSerde = priceOverrideDtoSerde;
        this.itemBasePriceEventSerde = itemBasePriceEventSerde;
        this.priceUpdatedEventSerde = priceUpdatedEventSerde; // Store
    }


    @Autowired
    public void buildPipeline(StreamsBuilder streamsBuilder) {
        log.info("Building Kafka Streams pipeline for demand metrics aggregation...");

        // GlobalKTables for rules, overrides, and base prices, now keyed by String(itemId)
        GlobalKTable<String, DynamicPricingRuleDto> rulesByItemIdGTable = streamsBuilder.globalTable(
                internalRulesByItemIdTopic, // New topic
                Consumed.with(stringSerde, dynamicPricingRuleDtoSerde), // Key is String(itemId)
                Materialized.as("rules-by-itemid-global-store"));

        GlobalKTable<String, PriceOverrideDto> overridesByItemIdGTable = streamsBuilder.globalTable(
                internalOverridesByItemIdTopic, // New topic
                Consumed.with(stringSerde, priceOverrideDtoSerde), // Key is String(itemId)
                Materialized.as("overrides-by-itemid-global-store"));

        GlobalKTable<String, ItemBasePriceEvent> basePricesGTable = streamsBuilder.globalTable(
                internalBasePricesTopic,
                Consumed.with(stringSerde, itemBasePriceEventSerde),
                Materialized.as("base-prices-global-store"));

        // KTable for last published prices
        KTable<String, PriceUpdatedEvent> lastPublishedPricesKTable = streamsBuilder.table(
                internalLastPublishedPricesTopic,
                Consumed.with(stringSerde, priceUpdatedEventSerde),
                Materialized.as("last-published-prices-store"));


        KStream<String, MetricEvent> metricEventKStream = streamsBuilder
                .stream(demandMetricsTopic, Consumed.with(stringSerde, metricEventSerde));

        // Branching for DLT:
        // Branch 0: Valid events (itemId is not null)
        // Branch 1: Invalid events (itemId is null) -> send to DLT
        @SuppressWarnings("unchecked") // For KStream<String, MetricEvent>[]
        KStream<String, MetricEvent>[] branchedMetricsStream = metricEventKStream
            .branch(
                (key, value) -> value != null && value.getItemId() != null, // Predicate for valid events
                (key, value) -> true // Predicate for invalid events (default branch)
            );

        KStream<String, MetricEvent> validMetricsStream = branchedMetricsStream[0];
        KStream<String, MetricEvent> dltMetricsStream = branchedMetricsStream[1];

        dltMetricsStream.peek((key, value) -> log.warn("Invalid MetricEvent (e.g., null itemId), sending to DLT. Key: {}, Value: {}", key, value))
            .to(demandMetricsDltTopic, Produced.with(stringSerde, metricEventSerde));


        // Rekey valid metrics by String(itemId) for joining with GlobalKTables
        KStream<String, MetricEvent> rekeyedMetricsStream = validMetricsStream
                .selectKey((key, value) -> value.getItemId().toString());

        Duration windowSize = Duration.ofMinutes(5);
        Duration advanceInterval = Duration.ofMinutes(1);
        Duration gracePeriod = Duration.ofSeconds(30);

        TimeWindows hoppingWindow = TimeWindows
                .ofSizeWithNoGrace(windowSize) // No grace on window size itself for hopping
                .advanceBy(advanceInterval);
                // Grace period is applied to the stream time for including late records in a window
                // It's typically configured on the KGroupedStream before aggregation or on the window definition itself if supported.
                // For TimeWindows, grace is typically applied to the window definition directly if the API supports it,
                // or handled by allowing late events up to 'grace' duration after window closes.
                // The ofSizeWithNoGrace explicitly states no grace for window sizing.
                // Let's use ofSizeAndGrace for a fixed window, or manage grace via stream time if needed for hopping.
                // Hopping windows + grace can be complex. A common way is `SessionWindows.with(...).grace(...)`
                // or `TimeWindows.of(...).grace(...)`.
                // With `TimeWindows.ofSizeWithNoGrace`, the grace is effectively zero for including records *after* the window end based on event time.
                // Let's adjust to use `TimeWindows.of(windowSize).advanceBy(advanceInterval).grace(gracePeriod)` - this seems more standard.
                // Correction: The Kafka Streams API for hopping windows is `TimeWindows.of(size).advanceBy(advance).grace(gracePeriod)`
                // However, `TimeWindows.ofSizeWithNoGrace(size).advanceBy(advance)` is also valid if you don't want grace for late records.
                // Let's assume the intention of A.2 is to allow late records.

        TimeWindows hoppingWindowWithGrace = TimeWindows
                .of(windowSize) // Use .of() to allow .grace()
                .advanceBy(advanceInterval)
                .grace(gracePeriod);


        // Aggregate metrics (e.g., count)
        // The rekeyedMetricsStream is KStream<String, MetricEvent>
        KTable<Windowed<String>, Long> aggregatedCountsTable = rekeyedMetricsStream
                .groupByKey(Grouped.with(stringSerde, metricEventSerde)) // Group by String itemId
                .windowedBy(hoppingWindowWithGrace)
                .count(Materialized.as("item-metric-counts-store"));

        KStream<String, AggregatedMetric> aggregatedMetricsStream = aggregatedCountsTable.toStream()
            .map((windowedItemId, count) -> {
                AggregatedMetric am = AggregatedMetric.builder()
                    .itemId(UUID.fromString(windowedItemId.key())) // Convert String key back to UUID for DTO
                    .metricCount(count)
                    .windowStartTimestamp(windowedItemId.window().start())
                    .windowEndTimestamp(windowedItemId.window().end())
                    .build();
                return KeyValue.pair(windowedItemId.key(), am); // Keep key as String for joins
            });

        // ValueJoiner for rules: (AggregatedMetric, DynamicPricingRuleDto) -> EnrichedAggregatedMetric
        ValueJoiner<AggregatedMetric, DynamicPricingRuleDto, EnrichedAggregatedMetric> ruleJoiner =
            (aggMetric, ruleDto) -> new EnrichedAggregatedMetric(aggMetric, ruleDto, null, null);

        // ValueJoiner for overrides: (EnrichedAggregatedMetric, PriceOverrideDto) -> EnrichedAggregatedMetric
        ValueJoiner<EnrichedAggregatedMetric, PriceOverrideDto, EnrichedAggregatedMetric> overrideJoiner =
            (enriched, overrideDto) -> enriched.withOverride(overrideDto); // Using the 'with' method

        // ValueJoiner for base prices: (EnrichedAggregatedMetric, ItemBasePriceEvent) -> EnrichedAggregatedMetric
        ValueJoiner<EnrichedAggregatedMetric, ItemBasePriceEvent, EnrichedAggregatedMetric> basePriceJoiner =
            (enriched, basePriceEvent) -> enriched.withBasePriceEvent(basePriceEvent);

        // Perform joins
        // The key for aggregatedMetricsStream is String(itemId)
        // The GlobalKTables rulesByItemIdGTable, overridesByItemIdGTable, basePricesGTable are also keyed by String(itemId)

        KStream<String, EnrichedAggregatedMetric> enrichedStream = aggregatedMetricsStream
            .leftJoin(rulesByItemIdGTable,
                (key, value) -> key, // KeyExtractor for stream (already String(itemId))
                ruleJoiner)
            .leftJoin(overridesByItemIdGTable,
                (key, value) -> key,
                overrideJoiner)
            .leftJoin(basePricesGTable,
                (key, value) -> key,
                basePriceJoiner);

        // Join with the last published price KTable
        KStream<String, EnrichedAggregatedMetricWithLastPrice> streamWithLastPrice = enrichedStream
            .leftJoin(lastPublishedPricesKTable,
                (enrichedAggMetric, lastPriceEvent) -> new EnrichedAggregatedMetricWithLastPrice(enrichedAggMetric, Optional.ofNullable(lastPriceEvent)),
                Joined.with(stringSerde, null, null)); // Serdes for enrichedAggMetric and PriceUpdatedEvent will be inferred or use defaults


        // Process the stream that now includes the last published price
        streamWithLastPrice.flatMapValues(enrichedWithLastPrice -> {
            EnrichedAggregatedMetric enrichedData = enrichedWithLastPrice.getEnrichedAggregatedMetric();
            Optional<PriceUpdatedEvent> lastPriceEventOptional = enrichedWithLastPrice.getLastPriceEvent();
            Optional<BigDecimal> lastFinalPrice = lastPriceEventOptional.map(PriceUpdatedEvent::getFinalPrice);

            log.info("Processing enriched data for item {}: {} with last price: {}",
                     enrichedData.getAggregatedMetric().getItemId(), enrichedData, lastFinalPrice.orElse(null));

            // pricingEngineService.calculatePrice now returns Optional<PriceUpdatedEvent>
            Optional<PriceUpdatedEvent> potentialNewPriceEventOpt = pricingEngineService.calculatePrice(
                enrichedData,
                lastFinalPrice
            );

            return potentialNewPriceEventOpt.map(List::of).orElse(Collections.emptyList());
        })
        .peek((itemIdString, priceUpdateEvent) -> {
            log.info("Threshold met for item {}. Publishing to external and internal topics: {}", itemIdString, priceUpdateEvent);
            // Publish to external "catalog.price.updated" topic
            // Note: KafkaTemplate is not typically used inside a Streams topology directly for main data flow.
            // Instead, use .to() operator. This requires a KafkaProducer bean for the template.
            // For now, assuming the service still holds the template for the final publish,
            // OR this .peek() is just for logging and a subsequent .to() handles publishing.
            // Based on previous change, DefaultPricingEngineService no longer publishes.
            // So, this stream IS responsible for publishing.
        })
        // Branch to two topics: external and internal for KTable update
        .split()
            .branch((key, value) -> true, // Always send if event is present
                    Branched.withConsumer(kstream -> kstream.to(externalPriceUpdatedTopic, Produced.with(stringSerde, priceUpdatedEventSerde))))
            .branch((key, value) -> true, // Always send to internal topic as well to update KTable
                    Branched.withConsumer(kstream -> kstream.to(internalLastPublishedPricesTopic, Produced.with(stringSerde, priceUpdatedEventSerde))))
            .noDefaultBranch(); // Or handle cases where it might not branch, though 'true' predicate covers all.


        log.info("Kafka Streams pipeline with GlobalKTable joins and last price KTable built.");
    }

    // Helper DTO for joining EnrichedAggregatedMetric with Optional<PriceUpdatedEvent>
    private static class EnrichedAggregatedMetricWithLastPrice {
        private final EnrichedAggregatedMetric enrichedAggregatedMetric;
        private final Optional<PriceUpdatedEvent> lastPriceEvent;

        public EnrichedAggregatedMetricWithLastPrice(EnrichedAggregatedMetric enrichedAggregatedMetric, Optional<PriceUpdatedEvent> lastPriceEvent) {
            this.enrichedAggregatedMetric = enrichedAggregatedMetric;
            this.lastPriceEvent = lastPriceEvent;
        }
        public EnrichedAggregatedMetric getEnrichedAggregatedMetric() { return enrichedAggregatedMetric; }
        public Optional<PriceUpdatedEvent> getLastPriceEvent() { return lastPriceEvent; }
    }

    // Helper DTO-to-Entity mapping methods are removed as service layer will use DTOs.
    // private DynamicPricingRuleEntity mapToEntity(DynamicPricingRuleDto dto) { ... }
    // private PriceOverrideEntity mapToEntity(PriceOverrideDto dto) { ... }
}
    }

    // Helper DTO-to-Entity mapping methods are removed as service layer will use DTOs.
    // private DynamicPricingRuleEntity mapToEntity(DynamicPricingRuleDto dto) { ... }
    // private PriceOverrideEntity mapToEntity(PriceOverrideDto dto) { ... }
}
