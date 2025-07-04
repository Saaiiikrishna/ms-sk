package com.mysillydreams.pricingengine.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mysillydreams.pricingengine.dto.MetricEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

@Service
// @RequiredArgsConstructor // Remove if using custom constructor for counters
@Slf4j
public class DemandMetricsListener {

    private final PricingEngineService pricingEngineService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    private final Counter metricsConsumedCounter;

    // Custom constructor to initialize MeterRegistry and Counter
    public DemandMetricsListener(
            PricingEngineService pricingEngineService,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        this.pricingEngineService = pricingEngineService;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.metricsConsumedCounter = Counter.builder("pricing.engine.metrics.consumed")
                .description("Number of demand metric events consumed")
                .register(meterRegistry);
    }

    @KafkaListener(
            topics = "${topics.demandMetrics}",
            groupId = "${kafka.consumer.group-id}", // Reuse group-id
            containerFactory = "kafkaListenerContainerFactory" // Assuming default or custom factory
    )
    public void onMetricsEvent(@Payload String payload) {
        // Stub: For now, just log the received metrics event.
        // In the next phase, this will involve deserializing to a MetricEvent DTO,
        // aggregating metrics, and potentially triggering pricing logic.
        log.debug("Received demand metrics event payload: {}", payload);

        try {
            MetricEvent event = objectMapper.readValue(payload, MetricEvent.class);
            log.info("Deserialized metric event: {}", event.getEventId());
            metricsConsumedCounter.increment(); // Increment counter
            if (pricingEngineService != null) {
                pricingEngineService.processMetric(event);
            }
        } catch (Exception e) { // Catch broader exception for now, JsonProcessingException for specific
            log.error("Error deserializing or processing metric event: {}", payload, e);
            // Consider DLT or other error handling
        }
    }
}
