package com.mysillydreams.catalogservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
// Import DTOs that will be used in deserialization
import com.mysillydreams.catalogservice.dto.DynamicPricingRuleDto;
import com.mysillydreams.catalogservice.dto.PriceOverrideDto;
import com.mysillydreams.catalogservice.domain.model.OutboxEventEntity;
import com.mysillydreams.catalogservice.domain.repository.OutboxEventRepository;
import com.mysillydreams.catalogservice.kafka.producer.KafkaProducerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaProducerException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxEventPollerService {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaProducerService kafkaProducerService;
    private final ObjectMapper objectMapper; // To deserialize payload if needed, or just pass string

    @Value("${app.outbox.poll.batch-size:100}")
    private int batchSize;

    @Value("${app.outbox.poll.max-attempts:5}")
    private int maxProcessingAttempts;

    @Value("${app.outbox.poll.retry-delay-seconds:300}") // Time before retrying a failed event (5 mins)
    private long retryDelaySeconds;


    @Scheduled(fixedDelayString = "${app.outbox.poll.fixed-delay-ms:10000}", initialDelayString = "${app.outbox.poll.initial-delay-ms:5000}")
    @Transactional(propagation = Propagation.REQUIRES_NEW) // Each poll run in its own transaction
    public void pollAndPublishEvents() {
        log.trace("Polling for unprocessed outbox events...");
        Pageable pageable = PageRequest.of(0, batchSize);

        // More sophisticated retry: only fetch events that haven't been tried recently or have few attempts
        Instant retryThreshold = Instant.now().minusSeconds(retryDelaySeconds);
        List<OutboxEventEntity> eventsToProcess = outboxEventRepository.findUnprocessedEventsForRetry(
            retryThreshold, maxProcessingAttempts, pageable
        );

        if (eventsToProcess.isEmpty()) {
            log.trace("No unprocessed outbox events found to publish.");
            return;
        }

        log.info("Found {} outbox events to process. Batch size: {}", eventsToProcess.size(), batchSize);

        for (OutboxEventEntity eventEntity : eventsToProcess) {
            boolean publishedSuccessfully = false;
            eventEntity.setProcessingAttempts(eventEntity.getProcessingAttempts() + 1);
            eventEntity.setLastAttemptTime(Instant.now());

            try {
                // The payload is already a JSON string. KafkaProducerService is configured for Object payload
                // and uses JsonSerializer. So, we can pass the JSON string directly,
                // or deserialize then let KafkaProducerService re-serialize.
                // Passing string is simpler if producer expects string or can handle it.
                // If KafkaProducerService expects specific DTO object, deserialize here.
                // For now, assuming KafkaProducerService can handle JSON String if valueSerializer is appropriate,
                // or it expects Object and will use its configured JsonSerializer.
                // Let's assume we need to pass the Object for type information if producer relies on it.

                // Dynamically determine the class of the payload
                // This is tricky without storing class name or using a map.
                // For now, let's assume the KafkaProducerService's JsonSerializer can handle it,
                // or we pass the JSON string as is if the producer is set up for string payloads.
                // Given KafkaProducerService takes Object, JsonSerializer will try to serialize it.
                // If payload in outbox IS the final JSON string, producer needs to handle string.
                // Let's assume payload is JSON string and producer can send it.
                // If KafkaTemplate<String, Object> is used with JsonSerializer, passing String might cause it to be "double-serialized"
                // Best to deserialize to a generic Map<String,Object> or specific DTO if known.
                // For simplicity, if KafkaProducerService's value serializer is just a pass-through for strings or specific,
                // this is fine. Otherwise, more robust deserialization is needed.

                // Let's assume kafkaProducerService is configured to handle objects and will serialize them.
                // We need to deserialize the JSON string from outbox to an object first.
                // This requires knowing the type. We stored eventType e.g. "category.created".
                // We need a mapping from eventType to DTO class.

                // Simplified: Pass the JSON string directly. The KafkaTemplate's value serializer
                // should be configured to handle String (e.g. StringSerializer or a JsonSerializer that can take a string).
                // Our KafkaProducerService uses KafkaTemplate<String, Object> with JsonSerializer.
                // JsonSerializer would wrap the string payload in quotes. This is usually not what we want.
                // So, we MUST deserialize to the original DTO type here.

                Object eventPayloadObject = deserializePayload(eventEntity); // Implement this helper

                if (eventPayloadObject != null) {
                    kafkaProducerService.sendMessage(eventEntity.getKafkaTopic(), eventEntity.getAggregateId(), eventPayloadObject);
                    publishedSuccessfully = true;
                    log.info("Successfully published event ID: {} (type: {}) to Kafka topic: {}", eventEntity.getId(), eventEntity.getEventType(), eventEntity.getKafkaTopic());
                } else {
                     log.error("Failed to deserialize payload for event ID: {}. Skipping.", eventEntity.getId());
                     // Consider marking as permanently failed if deserialization is impossible
                }

            } catch (KafkaProducerException kpe) { // Kafka specific exceptions (e.g., broker down, retries exhausted by producer)
                log.warn("Kafka producer error for event ID: {} (type: {}). Attempt {}/{}. Error: {}",
                        eventEntity.getId(), eventEntity.getEventType(), eventEntity.getProcessingAttempts(), maxProcessingAttempts, kpe.getMessage());
                // Handled by retry logic of poller (increment attempt count)
            } catch (Exception e) {
                log.error("Unexpected error processing outbox event ID: {} (type: {}). Attempt {}/{}. Error: {}",
                        eventEntity.getId(), eventEntity.getEventType(), eventEntity.getProcessingAttempts(), maxProcessingAttempts, e.getMessage(), e);
                // Also handled by retry logic of poller
            }

            if (publishedSuccessfully) {
                eventEntity.setProcessed(true);
            } else {
                if (eventEntity.getProcessingAttempts() >= maxProcessingAttempts) {
                    log.error("Event ID: {} (type: {}) reached max processing attempts ({}). Marking as processed to prevent further retries. Manual intervention may be required.",
                            eventEntity.getId(), eventEntity.getEventType(), maxProcessingAttempts);
                    // Mark as processed to move it out of the main retry queue.
                    // This is a form of Dead Letter Queue for outbox items.
                    // Another option: move to a separate "failed_outbox_events" table.
                    eventEntity.setProcessed(true); // Or a new 'failed' status
                }
            }
            outboxEventRepository.save(eventEntity); // Save updated attempt count, processed status, last attempt time
        }
        log.trace("Finished polling outbox events.");
    }

    private Object deserializePayload(OutboxEventEntity eventEntity) {
        // This is the challenging part: mapping eventType to a Class<?>
        // This needs a registry or a convention.
        // Example:
        String eventType = eventEntity.getEventType();
        String payloadJson = eventEntity.getPayload();
        try {
            // This assumes event DTOs are in a known package or a registry maps eventType to Class.
            if (eventType.startsWith("category.")) {
                return objectMapper.readValue(payloadJson, com.mysillydreams.catalogservice.kafka.event.CategoryEvent.class);
            } else if (eventType.startsWith("catalog.item.")) {
                return objectMapper.readValue(payloadJson, com.mysillydreams.catalogservice.kafka.event.CatalogItemEvent.class);
            } else if (eventType.equals("catalog.price.updated")) {
                return objectMapper.readValue(payloadJson, com.mysillydreams.catalogservice.kafka.event.PriceUpdatedEvent.class);
            } else if (eventType.equals("stock.level.changed")) {
                return objectMapper.readValue(payloadJson, com.mysillydreams.catalogservice.kafka.event.StockLevelChangedEvent.class);
            } else if (eventType.startsWith("bulk.pricing.rule.")) {
                return objectMapper.readValue(payloadJson, com.mysillydreams.catalogservice.kafka.event.BulkPricingRuleEvent.class);
            } else if (eventType.equals("cart.checked_out")) {
                return objectMapper.readValue(payloadJson, com.mysillydreams.catalogservice.kafka.event.CartCheckedOutEvent.class);
            } else if (eventType.startsWith("dynamic.pricing.rule.")) { // Covers .created, .updated, .deleted
                return objectMapper.readValue(payloadJson, DynamicPricingRuleDto.class);
            } else if (eventType.startsWith("price.override.")) { // Covers .created, .updated, .deleted
                return objectMapper.readValue(payloadJson, PriceOverrideDto.class);
            }
            // Add more mappings as new event types are introduced
            log.warn("No specific deserializer found for eventType: {}. Attempting generic Map deserialization for event: {}", eventType, eventEntity.getId());
            return objectMapper.readValue(payloadJson, java.util.Map.class); // Fallback to Map
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize JSON payload for event type {} (ID: {}): {}", eventType, eventEntity.getId(), payloadJson, e);
            return null;
        }
    }

    // Optional: Scheduled task to clean up old, processed outbox events
    @Scheduled(cron = "${app.outbox.cleanup.cron:0 0 3 * * ?}") // Example: Run daily at 3 AM
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cleanupProcessedOutboxEvents() {
        Instant cutoff = Instant.now().minusSeconds(Long.parseLong("${app.outbox.cleanup.retention-days:30}") * 24 * 60 * 60); // Default 30 days
        log.info("Cleaning up processed outbox events older than {}.", cutoff);
        int deletedCount = outboxEventRepository.deleteProcessedEventsOlderThan(cutoff);
        log.info("Deleted {} processed outbox events.", deletedCount);
    }
}
