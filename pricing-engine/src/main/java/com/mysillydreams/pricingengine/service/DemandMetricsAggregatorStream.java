package com.mysillydreams.pricingengine.service;

import com.mysillydreams.pricingengine.domain.DynamicPricingRuleEntity; // For mapping DTO to Entity if needed
import com.mysillydreams.pricingengine.domain.PriceOverrideEntity;   // For mapping DTO to Entity if needed
import com.mysillydreams.pricingengine.dto.AggregatedMetric;
import com.mysillydreams.pricingengine.dto.DynamicPricingRuleDto;
import com.mysillydreams.pricingengine.dto.ItemBasePriceEvent; // Added
import com.mysillydreams.pricingengine.dto.MetricEvent;
import com.mysillydreams.pricingengine.dto.PriceOverrideDto;
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
    @Value("${topics.internalRules}")
    private String internalRulesTopic;
    @Value("${topics.internalOverrides}")
    private String internalOverridesTopic;
    @Value("${topics.internalBasePrices}")
    private String internalBasePricesTopic;
    @Value("${topics.demandMetricsDlt}") // Added DLT topic
    private String demandMetricsDltTopic;


    private final PricingEngineService pricingEngineService;
    private final KafkaTemplate<String, String> dltKafkaTemplate; // For raw string messages to DLT
    private final Serde<String> stringSerde;
    private final Serde<UUID> uuidSerde;
    private final Serde<MetricEvent> metricEventSerde;
    private final Serde<DynamicPricingRuleDto> dynamicPricingRuleDtoSerde;
    private final Serde<PriceOverrideDto> priceOverrideDtoSerde;
    private final Serde<ItemBasePriceEvent> itemBasePriceEventSerde;


    public DemandMetricsAggregatorStream(
            PricingEngineService pricingEngineService,
            KafkaTemplate<String, String> dltKafkaTemplate, // Injected KafkaTemplate for DLT
            Serde<String> stringSerde,
            @Qualifier("uuidSerde") Serde<UUID> uuidSerde,
            Serde<MetricEvent> metricEventSerde,
            Serde<DynamicPricingRuleDto> dynamicPricingRuleDtoSerde,
            Serde<PriceOverrideDto> priceOverrideDtoSerde,
            Serde<ItemBasePriceEvent> itemBasePriceEventSerde) {
        this.pricingEngineService = pricingEngineService;
        this.dltKafkaTemplate = dltKafkaTemplate;
        this.stringSerde = stringSerde;
        this.uuidSerde = uuidSerde;
        this.metricEventSerde = metricEventSerde;
        this.dynamicPricingRuleDtoSerde = dynamicPricingRuleDtoSerde;
        this.priceOverrideDtoSerde = priceOverrideDtoSerde;
        this.itemBasePriceEventSerde = itemBasePriceEventSerde;
    }


    @Autowired
    public void buildPipeline(StreamsBuilder streamsBuilder) {
        log.info("Building Kafka Streams pipeline for demand metrics aggregation...");

        // GlobalKTables for rules, overrides, and base prices
        // Rules and Overrides are keyed by their own IDs (UUID)
        GlobalKTable<UUID, DynamicPricingRuleDto> rulesGTable = streamsBuilder.globalTable(
                internalRulesTopic,
                Consumed.with(uuidSerde, dynamicPricingRuleDtoSerde),
                Materialized.as("rules-global-store"));

        GlobalKTable<UUID, PriceOverrideDto> overridesGTable = streamsBuilder.globalTable(
                internalOverridesTopic,
                Consumed.with(uuidSerde, priceOverrideDtoSerde),
                Materialized.as("overrides-global-store"));

        // Base prices are keyed by ItemID (String representation of UUID from listener)
        GlobalKTable<String, ItemBasePriceEvent> basePricesGTable = streamsBuilder.globalTable(
                internalBasePricesTopic,
                Consumed.with(stringSerde, itemBasePriceEventSerde), // Key is String(itemId)
                Materialized.as("base-prices-global-store"));


                Consumed.with(stringSerde, itemBasePriceEventSerde), // Key is String(itemId)
                Materialized.as("base-prices-global-store"));


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

        // Send invalid records to DLT (original string payload if possible, or re-serialized MetricEvent)
        // Note: Accessing original raw string message for DLT is tricky here as we've already deserialized.
        // If raw string is needed, DLT handling should happen before deserialization attempt by streams,
        // or use a custom deserializer that wraps the original record.
        // For now, we'll send the key and what we have of the value (potentially null or partial MetricEvent if deserialization failed partially).
        // If deserialization completely fails, the DeserializationExceptionHandler handles it. This DLT is for logical validation.
        dltMetricsStream.peek((key, value) -> log.warn("Invalid MetricEvent (e.g., null itemId), sending to DLT. Key: {}, Value: {}", key, value))
            .to(demandMetricsDltTopic, Produced.with(stringSerde, metricEventSerde)); // Or use stringSerde for value if sending raw


        KStream<UUID, MetricEvent> rekeyedMetricsStream = validMetricsStream
                // .filter((key, value) -> value != null && value.getItemId() != null) // Filter is now part of branch
                .selectKey((key, value) -> value.getItemId());

        Duration windowSize = Duration.ofMinutes(5);
        Duration advanceInterval = Duration.ofMinutes(1);
        Duration gracePeriod = Duration.ofSeconds(30); // Define grace period

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
        KTable<Windowed<UUID>, Long> aggregatedCountsTable = rekeyedMetricsStream
                .groupByKey(Grouped.with(uuidSerde, metricEventSerde))
                .windowedBy(hoppingWindowWithGrace) // Use window with grace
                .count(Materialized.as("item-metric-counts-store"));

        // Stream of aggregated metrics
        KStream<UUID, AggregatedMetric> aggregatedMetricsStream = aggregatedCountsTable.toStream()
            .map((windowedItemId, count) -> {
                AggregatedMetric am = AggregatedMetric.builder()
                    .itemId(windowedItemId.key())
                    .metricCount(count)
                    .windowStartTimestamp(windowedItemId.window().start())
                    .windowEndTimestamp(windowedItemId.window().end())
                    .build();
                return KeyValue.pair(windowedItemId.key(), am);
            });

        // Join aggregated metrics with rules (GlobalKTable)
        // Note: GlobalKTable join key is the *stream's* key (itemId).
        // The GlobalKTable for rules is keyed by ruleId. We need to look up rules by itemId.
        // This requires a different approach than a direct join if GlobalKTable is not keyed by stream's key.
        // A GlobalKTable is a full copy on each node; we can query it.
        // So, after getting aggregatedMetrics, we'd use a KeyValueMapper or ValueTransformer
        // to look up rules for the itemId.

        // For simplicity in this step, let's assume a direct way to get rules/overrides for an itemId.
        // This would typically involve a KStream-to-KTable join if rules/overrides were partitioned by itemId,
        // or a custom processor that queries the GlobalKTable.

        // Simplified approach: process each aggregated metric, then lookup.
        // This is less "stream-native" for joins but works with GlobalKTable's nature.
        aggregatedMetricsStream.process(new KeyValueProcessorSupplier<UUID, AggregatedMetric, KeyValue<UUID, String>>() {
            @Override
            public Processor<UUID, AggregatedMetric, KeyValue<UUID, String>> get() {
                return new Processor<UUID, AggregatedMetric, KeyValue<UUID, String>>() {
                    private ReadOnlyKeyValueStore<UUID, DynamicPricingRuleDto> ruleStore;
                    private ReadOnlyKeyValueStore<UUID, PriceOverrideDto> overrideStore;

                    @Override
                    @SuppressWarnings("unchecked")
                    public void init(ProcessorContext context) {
                        // Accessing GlobalKTables as stores. Note: This is not how GlobalKTables are typically "joined".
                        // They are usually joined via leftJoin/join methods on KStream using a KeyValueMapper
                        // that extracts the foreign key.
                        // This direct store access pattern is more for interactive queries or custom processors.
                        // For now, this illustrates accessing the data. A proper join is more complex.

                        // The "rules-global-store" and "overrides-global-store" are Materialized views of the GlobalKTables.
                        // However, GlobalKTables are not directly queryable by foreign keys (itemId) in this simple way.
                        // A GlobalKTable is keyed by its primary key (ruleId/overrideId).
                        // To find rules/overrides by itemId, we'd need to iterate or have a secondary index (not native to GKT).

                        // Let's adjust: The stream will call pricingEngineService, which *used to* have caches.
                        // Now, pricingEngineService needs the rules/overrides passed to it.
                        // The stream processor (this .process()) is the place to fetch them.
                        // Since GlobalKTables are replicated, we can iterate them here (inefficient for large tables).
                        // A better pattern: KStream (metrics) joins KTable (rules by itemId) joins KTable (overrides by itemId).
                        // This requires rules/overrides topics to be partitioned by itemId.
                        // If we stick to GlobalKTables keyed by their own IDs, the "join" is effectively a lookup.

                        // For this step, let's assume the `pricingEngineService.calculateAndPublishPrice`
                        // will be adapted or a new method created that can perform these lookups
                        // using the GlobalKTable instances if they were directly injectable or queryable.
                        // This part of the plan (A.3) is about setting up GlobalKTables. The actual join
                        // and data flow to pricing logic is the tricky part.

                        // The simplest way for now is to pass the GlobalKTable itself to the processor,
                        // but Kafka Streams doesn't allow injecting stores directly like this in a stateless processor.
                        // We will need to refactor how DefaultPricingEngineService gets this data.
                        // For now, the stream will prepare the aggregated metric and itemId.
                        // The call to pricing service will be modified later to include rule/override data from GKT.
                    }

                    @Override
                    public void process(Record<UUID, AggregatedMetric> record) {
                        UUID itemId = record.key();
                        AggregatedMetric metrics = record.value();
                        log.info("Aggregated metrics for item {}: {}", itemId, metrics);

                        // TODO: In a subsequent step (or as part of refining this):
                        // 1. Fetch basePrice (e.g., from another GlobalKTable).
                        // 2. Lookup applicable rules for this itemId from the 'rulesTable' GlobalKTable.
                        //    This means iterating the GKT's values and filtering by itemId if GKT is keyed by ruleId.
                        //    Or, if rules topic was re-keyed by itemId for a KTable (not GlobalKTable).
                        // 3. Lookup active override for this itemId from the 'overridesTable' GlobalKTable.
                        // 4. Call pricingEngineService.calculateAndPublishPrice with all this data.
                        // For now, we are just setting up the GlobalKTables and the metrics stream.
                        // The actual join logic will be complex and might require a different stream structure
                        // or a custom Transformer/Processor that can access GlobalKTable state.

                        // Placeholder: Call pricing service with what we have. It will use its (now empty) caches.
                        // This will be fixed when basePrice GKT is added and GKT lookups are implemented.
                        // For now, to make it runnable, we pass empty lists/nulls for rules/overrides.
                        // The base price will also be the hardcoded one in the service.
                        // This is an intermediate state.

                        // *** This entire .process block will be replaced by proper joins and then calling the service. ***
                        // *** The following is a conceptual placeholder for what data needs to be gathered. ***

                        // 1. Base Price Lookup (from basePricesGTable)
                        //    - The GKT is keyed by String(itemId). The stream is keyed by UUID.
                        //    - A KeyValueMapper would be needed for a KStream-GlobalKTable join.
                        //    - Or, in a custom processor, query the store:
                        //      ItemBasePriceEvent basePriceEvent = basePriceStore.get(itemId.toString());
                        //      BigDecimal basePrice = (basePriceEvent != null) ? basePriceEvent.getBasePrice() : null;
                        //    For now, we'll pass null and let the service use its hardcoded default (to be removed).
                        BigDecimal basePrice = null; // This will be replaced by GKT lookup.

                        // 2. Rules Lookup (from rulesGTable)
                        //    - rulesGTable is keyed by ruleId. We need rules for this itemId.
                        //    - This requires iterating the GKT (inefficient) or having a secondary index,
                        //      OR the rules topic being re-partitioned by itemId for a KTable join.
                        //    - For now, passing empty list.
                        List<DynamicPricingRuleEntity> applicableRules = Collections.emptyList();

                        // 3. Override Lookup (from overridesGTable)
                        //    - Similar to rules, overridesGTable is keyed by overrideId.
                        //    - For now, passing null.
                        PriceOverrideEntity activeOverride = null;


                        pricingEngineService.calculateAndPublishPrice(
                            itemId,
                            basePrice, // Will be null for now, service will use its hardcoded default
                            metrics,
                            applicableRules,
                            activeOverride
                        );
                    }

                    @Override
                    public void close() { }
                };
            }
        });


        log.info("Kafka Streams pipeline enhancement for GlobalKTables started.");
    }

    // Helper to map DTO to Entity (if service expects entities)
    // These would be used if the stream processor calls the service method that expects entities.
    private DynamicPricingRuleEntity mapToEntity(DynamicPricingRuleDto dto) {
        if (dto == null) return null;
        return DynamicPricingRuleEntity.builder()
            .id(dto.getId()).itemId(dto.getItemId()).ruleType(dto.getRuleType())
            .parameters(dto.getParameters()).enabled(dto.isEnabled())
            .createdBy(dto.getCreatedBy()).createdAt(dto.getCreatedAt())
            .updatedAt(dto.getUpdatedAt()).version(dto.getVersion())
            .build();
    }

    private PriceOverrideEntity mapToEntity(PriceOverrideDto dto) {
        if (dto == null) return null;
        return PriceOverrideEntity.builder()
            .id(dto.getId()).itemId(dto.getItemId()).overridePrice(dto.getOverridePrice())
            .startTime(dto.getStartTime()).endTime(dto.getEndTime())
            .enabled(dto.isEnabled()).createdByUserId(dto.getCreatedByUserId())
            .createdByRole(dto.getCreatedByRole()).createdAt(dto.getCreatedAt())
            .updatedAt(dto.getUpdatedAt()).version(dto.getVersion())
            .build();
    }
}
