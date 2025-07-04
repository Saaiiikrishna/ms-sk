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
    @Value("${topics.internalLastPublishedPrices}")
    private String internalLastPublishedPricesTopic;
    @Value("${topics.priceUpdated}")
    private String externalPriceUpdatedTopic;
    @Value("${topics.processingErrorsDlt}") // Added for processing errors
    private String processingErrorsDltTopic;


    private final PricingEngineService pricingEngineService;
    private final KafkaTemplate<String, String> dltKafkaTemplate;
    private final ObjectMapper objectMapper; // For serializing context to DLT
    private final Serde<String> stringSerde;
    private final Serde<MetricEvent> metricEventSerde;
    private final Serde<DynamicPricingRuleDto> dynamicPricingRuleDtoSerde;
    private final Serde<PriceOverrideDto> priceOverrideDtoSerde;
    private final Serde<ItemBasePriceEvent> itemBasePriceEventSerde;
    private final Serde<PriceUpdatedEvent> priceUpdatedEventSerde;
    private final Serde<List<DynamicPricingRuleDto>> listOfRuleDtoSerde; // Added


    public DemandMetricsAggregatorStream(
            PricingEngineService pricingEngineService,
            KafkaTemplate<String, String> dltKafkaTemplate,
            ObjectMapper objectMapper, // Added
            Serde<String> stringSerde,
            Serde<MetricEvent> metricEventSerde,
            Serde<DynamicPricingRuleDto> dynamicPricingRuleDtoSerde,
            Serde<PriceOverrideDto> priceOverrideDtoSerde,
            Serde<ItemBasePriceEvent> itemBasePriceEventSerde,
            Serde<PriceUpdatedEvent> priceUpdatedEventSerde,
            Serde<List<DynamicPricingRuleDto>> listOfRuleDtoSerde) {
        this.pricingEngineService = pricingEngineService;
        this.dltKafkaTemplate = dltKafkaTemplate;
        this.objectMapper = objectMapper; // Store
        this.stringSerde = stringSerde;
        this.metricEventSerde = metricEventSerde;
        this.dynamicPricingRuleDtoSerde = dynamicPricingRuleDtoSerde;
        this.priceOverrideDtoSerde = priceOverrideDtoSerde;
        this.itemBasePriceEventSerde = itemBasePriceEventSerde;
        this.priceUpdatedEventSerde = priceUpdatedEventSerde;
        this.listOfRuleDtoSerde = listOfRuleDtoSerde;
    }


    @Autowired
    public void buildPipeline(StreamsBuilder streamsBuilder) {
        log.info("Building Kafka Streams pipeline for demand metrics aggregation...");

        // Consume internal.rules-by-itemid.v1 as a KStream
        KStream<String, DynamicPricingRuleDto> rulesByItemIdStream = streamsBuilder.stream(
                internalRulesByItemIdTopic, Consumed.with(stringSerde, dynamicPricingRuleDtoSerde)
        );

        // Aggregate rules into a KTable<String, List<DynamicPricingRuleDto>>
        KTable<String, List<DynamicPricingRuleDto>> aggregatedRulesKTable = rulesByItemIdStream
            .groupByKey(Grouped.with(stringSerde, dynamicPricingRuleDtoSerde))
            .aggregate(
                ArrayList::new, // Initializer
                (itemId, newRuleDto, aggList) -> { // Adder
                    // Remove existing rule with the same ID, then add new/updated one
                    // This handles create and update. For delete, if newRuleDto.isEnabled() is false,
                    // we could filter it out here, or let the service logic handle disabled rules.
                    // If a tombstone (null DTO) is sent for a hard delete of a specific rule,
                    // this logic would need a corresponding subtractor or different handling.
                    // For now, this simple adder replaces/adds.
                    aggList.removeIf(rule -> rule.getId().equals(newRuleDto.getId()));
                    aggList.add(newRuleDto);
                    return aggList;
                },
                Materialized.<String, List<DynamicPricingRuleDto>, KeyValueStore<Bytes, byte[]>>as("aggregated-rules-by-itemid-store")
                        .withKeySerde(stringSerde)
                        .withValueSerde(listOfRuleDtoSerde)
                        .withCachingEnabled() // Added
            );

        // GlobalKTables do not use .withCachingEnabled() in their Materialized definition in the same way.
        // Caching for GlobalKTables is handled internally by Kafka Streams.
        GlobalKTable<String, PriceOverrideDto> overridesByItemIdGTable = streamsBuilder.globalTable(
                internalOverridesByItemIdTopic,
                Consumed.with(stringSerde, priceOverrideDtoSerde),
                Materialized.as("overrides-by-itemid-global-store")); // No caching config here

        GlobalKTable<String, ItemBasePriceEvent> basePricesGTable = streamsBuilder.globalTable(
                internalBasePricesTopic,
                Consumed.with(stringSerde, itemBasePriceEventSerde),
                Materialized.as("base-prices-global-store")); // No caching config here

        // KTable for last published prices
        KTable<String, PriceUpdatedEvent> lastPublishedPricesKTable = streamsBuilder.table(
                internalLastPublishedPricesTopic,
                Consumed.with(stringSerde, priceUpdatedEventSerde),
                Materialized.<String, PriceUpdatedEvent, KeyValueStore<Bytes, byte[]>>as("last-published-prices-store")
                        .withKeySerde(stringSerde)
                        .withValueSerde(priceUpdatedEventSerde)
                        .withCachingEnabled() // Added
                );


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
                .count(Materialized.<String, Long, WindowStore<Bytes, byte[]>>as("item-metric-counts-store")
                        .withKeySerde(stringSerde)
                        .withValueSerde(Serdes.Long())
                        .withCachingEnabled());

        // Suppress intermediate updates from the KTable, emitting only final results per window
        KStream<String, AggregatedMetric> aggregatedMetricsStream = aggregatedCountsTable
                .suppress(Suppressed.untilWindowCloses(Suppressed.BufferConfig.unbounded()))
                .toStream()
                .map((windowedItemId, count) -> {
                    if (count == null) {
                        return KeyValue.pair(windowedItemId.key(), null);
                    }
                    AggregatedMetric am = AggregatedMetric.builder()
                        .itemId(UUID.fromString(windowedItemId.key()))
                        .metricCount(count)
                        .windowStartTimestamp(windowedItemId.window().start())
                        .windowEndTimestamp(windowedItemId.window().end())
                        .build();
                    return KeyValue.pair(windowedItemId.key(), am);
                })
                .filter((key, value) -> value != null); // Filter out records where AggregatedMetric is null

        // ValueJoiner for rules list: (AggregatedMetric, List<DynamicPricingRuleDto>) -> EnrichedAggregatedMetric
        ValueJoiner<AggregatedMetric, List<DynamicPricingRuleDto>, EnrichedAggregatedMetric> rulesListJoiner =
            (aggMetric, rulesList) -> new EnrichedAggregatedMetric(aggMetric, rulesList, null, null); // rulesList can be null from leftJoin

        ValueJoiner<EnrichedAggregatedMetric, PriceOverrideDto, EnrichedAggregatedMetric> overrideJoiner =
            (enriched, overrideDto) -> enriched.withOverride(overrideDto);

        ValueJoiner<EnrichedAggregatedMetric, ItemBasePriceEvent, EnrichedAggregatedMetric> basePriceJoiner =
            (enriched, basePriceEvent) -> enriched.withBasePriceEvent(basePriceEvent);

        KStream<String, EnrichedAggregatedMetric> enrichedStream = aggregatedMetricsStream
            .leftJoin(aggregatedRulesKTable, // Join with KTable of rule lists
                (key, value) -> key,
                rulesListJoiner)
            .leftJoin(overridesByItemIdGTable, // This remains a GKT as per current plan for overrides
                (key, value) -> key,
                overrideJoiner)
            .leftJoin(basePricesGTable,
                (key, value) -> key,
                basePriceJoiner);

        KStream<String, EnrichedAggregatedMetricWithLastPrice> streamWithLastPrice = enrichedStream
            .leftJoin(lastPublishedPricesKTable,
                (enrichedAggMetric, lastPriceEvent) -> new EnrichedAggregatedMetricWithLastPrice(enrichedAggMetric, Optional.ofNullable(lastPriceEvent)),
                Joined.with(stringSerde, null, null));


        streamWithLastPrice.flatMapValues(enrichedWithLastPrice -> {
            EnrichedAggregatedMetric enrichedData = enrichedWithLastPrice.getEnrichedAggregatedMetric();
            Optional<PriceUpdatedEvent> lastPriceEventOptional = enrichedWithLastPrice.getLastPriceEvent();
            Optional<BigDecimal> lastFinalPrice = lastPriceEventOptional.map(PriceUpdatedEvent::getFinalPrice);
            UUID itemId = enrichedData.getAggregatedMetric().getItemId(); // For logging and DLT key

            log.info("Processing enriched data for item {}: {} with last price: {}",
                     itemId, enrichedData, lastFinalPrice.orElse(null));

            try {
                Optional<PriceUpdatedEvent> potentialNewPriceEventOpt = pricingEngineService.calculatePrice(
                    enrichedData,
                    lastFinalPrice
                );
                return potentialNewPriceEventOpt.map(List::of).orElse(Collections.emptyList());
            } catch (Exception e) {
                log.error("Error during pricing calculation for item {}. Data: {}. Error: {}",
                        itemId, enrichedData, e.getMessage(), e);
                try {
                    // Serialize the context (enrichedData or just relevant parts) to JSON string for DLT
                    // Using enrichedWithLastPrice as it contains all context before the failing call
                    String errorPayload = objectMapper.writeValueAsString(enrichedWithLastPrice);
                    dltKafkaTemplate.send(processingErrorsDltTopic, itemId.toString(), errorPayload);
                    log.info("Sent problematic enriched data for item {} to DLT [{}].", itemId, processingErrorsDltTopic);
                } catch (Exception dltEx) {
                    log.error("Failed to serialize or send message to DLT ({}) for item {}: {}",
                            processingErrorsDltTopic, itemId, dltEx.getMessage(), dltEx);
                }
                return Collections.emptyList(); // Skip this record by returning an empty list
            }
        })
        .peek((itemIdString, priceUpdateEvent) ->
            log.info("Threshold met for item {}. Publishing to external and internal topics: {}", itemIdString, priceUpdateEvent)
        )
        .split()
            .branch((key, value) -> true,
                    Branched.withConsumer(kstream -> kstream.to(externalPriceUpdatedTopic, Produced.with(stringSerde, priceUpdatedEventSerde))))
            .branch((key, value) -> true,
                    Branched.withConsumer(kstream -> kstream.to(internalLastPublishedPricesTopic, Produced.with(stringSerde, priceUpdatedEventSerde))))
            .noDefaultBranch();


        log.info("Kafka Streams pipeline with rule list aggregation and GlobalKTable joins built.");
    }

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
}
    }

    // Helper DTO-to-Entity mapping methods are removed as service layer will use DTOs.
    // private DynamicPricingRuleEntity mapToEntity(DynamicPricingRuleDto dto) { ... }
    // private PriceOverrideEntity mapToEntity(PriceOverrideDto dto) { ... }
}
