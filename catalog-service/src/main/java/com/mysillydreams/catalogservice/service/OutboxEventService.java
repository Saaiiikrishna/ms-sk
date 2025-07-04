package com.mysillydreams.catalogservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mysillydreams.catalogservice.domain.model.OutboxEventEntity;
import com.mysillydreams.catalogservice.domain.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxEventService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper; // Ensure ObjectMapper is available as a bean

    /**
     * Saves an event to the outbox table. This method is intended to be called
     * within an existing transaction of the business operation.
     *
     * @param aggregateType The type of the aggregate root (e.g., "Category", "CatalogItem").
     * @param aggregateId   The ID of the aggregate root.
     * @param eventType     A string identifying the type of event (e.g., "category.created").
     * @param kafkaTopic    The target Kafka topic for this event.
     * @param payload       The event payload object (will be serialized to JSON).
     */
    @Transactional(propagation = Propagation.MANDATORY) // Ensures this is called within an existing transaction
    public void saveOutboxEvent(String aggregateType, UUID aggregateId, String eventType, String kafkaTopic, Object payload) {
        try {
            String payloadJson = objectMapper.writeValueAsString(payload);
            OutboxEventEntity outboxEvent = OutboxEventEntity.builder()
                    .aggregateType(aggregateType)
                    .aggregateId(aggregateId.toString())
                    .eventType(eventType)
                    .kafkaTopic(kafkaTopic)
                    .payload(payloadJson)
                    .processed(false)
                    .processingAttempts(0)
                    .build();
            outboxEventRepository.save(outboxEvent);
            log.debug("Saved event to outbox: type={}, aggregateId={}, eventType={}", aggregateType, aggregateId, eventType);
        } catch (JsonProcessingException e) {
            log.error("Error serializing event payload to JSON for outbox: aggregateType={}, aggregateId={}, eventType={}",
                    aggregateType, aggregateId, eventType, e);
            // This is a critical error. If serialization fails, the event won't be saved.
            // Depending on policy, might rethrow as a runtime exception to roll back the main transaction.
            throw new RuntimeException("Failed to serialize event payload for outbox", e);
        }
    }
}
