package com.ecommerce.vendorfulfillmentservice.service;

import com.ecommerce.vendorfulfillmentservice.entity.OutboxEvent;
import com.ecommerce.vendorfulfillmentservice.event.avro.VendorOrderAcknowledgedEvent;
import com.ecommerce.vendorfulfillmentservice.event.avro.VendorOrderAssignedEvent;
import com.ecommerce.vendorfulfillmentservice.event.avro.*; // Import all
import com.ecommerce.vendorfulfillmentservice.repository.OutboxEventRepository;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import org.apache.avro.Schema;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.ResolvingDecoder;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.avro.specific.SpecificRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxPollerService {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, SpecificRecord> avroKafkaTemplate;
    private final MetricsService metricsService; // Added MetricsService
    // TODO: Inject Avro schema for each event type or a schema resolver

    @Value("${app.kafka.topic.vendor-order-assigned}")
    private String vendorOrderAssignedTopic;
    @Value("${app.kafka.topic.vendor-order-acknowledged}")
    private String vendorOrderAcknowledgedTopic;
    @Value("${app.kafka.topic.vendor-order-packed}")
    private String vendorOrderPackedTopic;
    @Value("${app.kafka.topic.vendor-order-shipped}")
    private String vendorOrderShippedTopic;
    @Value("${app.kafka.topic.vendor-order-fulfilled}")
    private String vendorOrderFulfilledTopic;
    @Value("${app.kafka.topic.vendor-order-reassigned}")
    private String vendorOrderReassignedTopic;
    // Add other topic values as needed for other events

    @Value("${app.outbox.poller.batch-size:100}")
    private int batchSize;

    // Cache for Avro schemas to avoid repeated parsing
    private final Map<Class<? extends SpecificRecord>, Schema> schemaCache = new ConcurrentHashMap<>();

    // This map is crucial for deserializing the correct event type from the outbox.
    // It maps the string 'eventType' stored in OutboxEvent to the actual Avro class.
    // This needs to be populated as new event types are added.
    private static final Map<String, Class<? extends SpecificRecord>> eventTypeToClassMap = new ConcurrentHashMap<>();
    static {
        eventTypeToClassMap.put(VendorOrderAssignedEvent.class.getSimpleName(), VendorOrderAssignedEvent.class);
        eventTypeToClassMap.put(VendorOrderAcknowledgedEvent.class.getSimpleName(), VendorOrderAcknowledgedEvent.class);
        eventTypeToClassMap.put(VendorOrderPackedEvent.class.getSimpleName(), VendorOrderPackedEvent.class);
        eventTypeToClassMap.put(VendorOrderShippedEvent.class.getSimpleName(), VendorOrderShippedEvent.class);
        eventTypeToClassMap.put(VendorOrderFulfilledEvent.class.getSimpleName(), VendorOrderFulfilledEvent.class);
        eventTypeToClassMap.put(VendorOrderReassignedEvent.class.getSimpleName(), VendorOrderReassignedEvent.class);
        // Add other event types here:
    }


    @Scheduled(fixedDelayString = "${app.outbox.poller.fixed-delay-ms:5000}")
    @Transactional // Each poll run will be one transaction
    public void pollAndPublishOutboxEvents() {
        log.trace("Polling for outbox events...");
        try {
            metricsService.updateOutboxBacklogGauge(); // Update gauge at the start of each poll
        } catch (Exception e) {
            log.warn("Failed to update outbox backlog gauge: {}", e.getMessage());
        }

        List<OutboxEvent> events = outboxEventRepository.findUnprocessedEvents(); // Consider Pageable for large batches

        if (events.isEmpty()) {
            log.trace("No unprocessed outbox events found.");
            // It's possible the gauge was >0 and now 0, so update again for accuracy if desired,
            // or rely on the next scheduled call. For simplicity, update only if events were processed or at start.
            // metricsService.updateOutboxBacklogGauge();
            return;
        }
        log.info("Found {} unprocessed outbox events. Processing...", events.size());

        boolean processedAny = false;
        for (OutboxEvent event : events) {
            try {
                SpecificRecord avroRecord = deserializePayload(event);
                if (avroRecord == null) {
                    log.error("Failed to deserialize payload for outbox event {}. Skipping.", event.getId());
                    // Consider moving to a dead letter table or incrementing a retry counter
                    // For now, we skip and it will be picked up again. A more robust solution is needed for poison pills.
                    continue;
                }

                String topic = getTopicForEventType(event.getEventType());
                if (topic == null) {
                    log.error("No topic configured for event type {} (outbox event {}). Skipping.", event.getEventType(), event.getId());
                    continue;
                }

                log.info("Publishing outbox event ID: {}, Aggregate ID: {}, Type: {}, Topic: {}",
                        event.getId(), event.getAggregateId(), event.getEventType(), topic);

                // Send to Kafka. send() returns a CompletableFuture.
                CompletableFuture<org.springframework.kafka.support.SendResult<String, SpecificRecord>> future =
                        avroKafkaTemplate.send(topic, event.getAggregateId().toString(), avroRecord);

                future.whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.info("Successfully sent event {} to Kafka topic {}. Offset: {}",
                                event.getId(), topic, result.getRecordMetadata().offset());
                        // Mark as processed only after successful send confirmation
                        // This needs to be handled carefully for transactional consistency.
                        // Ideally, the update to processed_at is part of the same transaction
                        // as the business logic that created the outbox event.
                        // Here, we mark it after Kafka send, which is common but has edge cases.
                        // For true atomicity, a 2PC or transactional outbox with a message relay is needed.
                        // For now, we'll update it in a new transaction within the loop or after the loop.
                        // Since the @Scheduled method is @Transactional, this save will be part of it.
                        markEventAsProcessed(event.getId());
                        processedAny = true; // Mark that at least one event was processed

                    } else {
                        log.error("Failed to send event {} to Kafka topic {}: {}",
                                event.getId(), topic, ex.getMessage(), ex);
                        // Do not mark as processed. It will be retried in the next poll.
                        // Consider retry limits and DLQ for persistent failures.
                    }
                });

            } catch (Exception e) {
                log.error("Error processing outbox event {}: {}. Will retry.", event.getId(), e.getMessage(), e);
                // The transaction will roll back for this event if an exception bubbles up.
                // Or if handled per event, ensure non-acknowledgement leads to retry.
            }
        }
    }

    // This method needs to be called in a new transaction or handled carefully
    // if the main pollAndPublishOutboxEvents is not @Transactional itself,
    // or if we want to commit per event.
    // Since pollAndPublishOutboxEvents IS @Transactional, this will be part of that TX.
    // If an error occurs after Kafka send but before this commits, the event might be sent twice.
    // This is a classic distributed transaction problem. Idempotent consumers can help.
    private void markEventAsProcessed(UUID eventId) {
        outboxEventRepository.findById(eventId).ifPresent(oe -> {
            oe.setProcessedAt(OffsetDateTime.now());
            outboxEventRepository.save(oe);
            log.info("Marked outbox event {} as processed.", eventId);
        });
    }


    private SpecificRecord deserializePayload(OutboxEvent event) throws IOException {
        Class<? extends SpecificRecord> clazz = eventTypeToClassMap.get(event.getEventType());
        if (clazz == null) {
            log.error("No class mapping found for event type: {}", event.getEventType());
            return null;
        }

        Schema schema = schemaCache.computeIfAbsent(clazz, key -> {
            try {
                // Avro generated classes have a static SCHEMA$ field
                return (Schema) key.getDeclaredField("SCHEMA$").get(null);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                log.error("Could not get SCHEMA$ for class {}", key.getName(), e);
                throw new RuntimeException("Failed to get Avro schema for class " + key.getName(), e);
            }
        });

        SpecificDatumReader<? extends SpecificRecord> reader = new SpecificDatumReader<>(schema);
        ResolvingDecoder decoder = DecoderFactory.get().resolvingDecoder(
                schema, schema, DecoderFactory.get().binaryDecoder(event.getPayload(), null)
        );

        return reader.read(null, decoder);
    }


    private String getTopicForEventType(String eventType) {
        if (VendorOrderAssignedEvent.class.getSimpleName().equals(eventType)) {
            return vendorOrderAssignedTopic;
        } else if (VendorOrderAcknowledgedEvent.class.getSimpleName().equals(eventType)) {
            return vendorOrderAcknowledgedTopic;
        } else if (VendorOrderPackedEvent.class.getSimpleName().equals(eventType)) {
            return vendorOrderPackedTopic;
        } else if (VendorOrderShippedEvent.class.getSimpleName().equals(eventType)) {
            return vendorOrderShippedTopic;
        } else if (VendorOrderFulfilledEvent.class.getSimpleName().equals(eventType)) {
            return vendorOrderFulfilledTopic;
        } else if (VendorOrderReassignedEvent.class.getSimpleName().equals(eventType)) {
            return vendorOrderReassignedTopic;
        }
        // Add other event type to topic mappings here
        return null;
    }
}
