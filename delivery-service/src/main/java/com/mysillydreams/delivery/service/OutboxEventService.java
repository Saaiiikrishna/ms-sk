package com.mysillydreams.delivery.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mysillydreams.delivery.domain.OutboxEvent;
import com.mysillydreams.delivery.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service("deliveryOutboxEventService") // Keep qualifier in case it's used elsewhere
@RequiredArgsConstructor
public class OutboxEventService {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventService.class);

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    /**
     * Creates and saves an outbox event. The payload (e.g., an Avro object)
     * is converted to JsonNode for storage in the JSONB column.
     * This method MUST be called within an existing transaction that includes
     * the domain entity changes to ensure atomicity.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void createAndSaveOutboxEvent(String aggregateType, String aggregateId, String eventType, Object payload) {
        try {
            JsonNode payloadJsonNode = objectMapper.valueToTree(payload);

            OutboxEvent outboxEvent = new OutboxEvent();
            // ID and createdAt are set by @PrePersist in OutboxEvent entity
            outboxEvent.setAggregateType(aggregateType);
            outboxEvent.setAggregateId(aggregateId);
            outboxEvent.setEventType(eventType); // This should be the Kafka topic or a key resolvable to one
            outboxEvent.setPayload(payloadJsonNode);
            outboxEvent.setProcessed(false);

            outboxRepository.save(outboxEvent);
            log.info("Saved outbox event: type={}, aggregateId={}, eventType={}",
                     aggregateType, aggregateId, eventType);
        } catch (Exception e) {
            log.error("Failed to create and save outbox event: type={}, aggregateId={}, eventType={}. Error: {}",
                    aggregateType, aggregateId, eventType, e.getMessage(), e);
            // Re-throw to ensure the calling transaction rolls back
            throw new RuntimeException("Failed to save outbox event for " + eventType + " due to: " + e.getMessage(), e);
        }
    }
}
