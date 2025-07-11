package com.mysillydreams.ordercore.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mysillydreams.ordercore.domain.OutboxEvent;
import com.mysillydreams.ordercore.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OutboxEventService {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventService.class);

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper; // Spring Boot auto-configures one, or define a custom one

    /**
     * Creates and saves an outbox event.
     * This method should be called within the same transaction as the domain entity changes
     * to ensure atomicity.
     *
     * @param aggregateType The type of the aggregate root (e.g., "Order").
     * @param aggregateId   The ID of the aggregate root.
     * @param eventType     The type of the event (e.g., "order.created").
     * @param payload       The event payload object.
     */
    @Transactional(propagation = Propagation.MANDATORY) // Ensures this is part of an existing transaction
    public void createAndSaveOutboxEvent(String aggregateType, String aggregateId, String eventType, Object payload) {
        try {
            JsonNode payloadJsonNode = objectMapper.valueToTree(payload);

            OutboxEvent outboxEvent = new OutboxEvent();
            // ID and createdAt are set by @PrePersist in OutboxEvent entity
            outboxEvent.setAggregateType(aggregateType);
            outboxEvent.setAggregateId(aggregateId);
            outboxEvent.setEventType(eventType);
            outboxEvent.setPayload(payloadJsonNode);
            outboxEvent.setProcessed(false);
            // version and attempts would be set here if used

            outboxRepository.save(outboxEvent);
            log.info("Saved outbox event: type={}, aggregateId={}, eventType={}", aggregateType, aggregateId, eventType);
        } catch (Exception e) {
            // Log error, but let the transaction roll back if this fails.
            // The calling service's transaction will handle the rollback.
            log.error("Failed to create and save outbox event: type={}, aggregateId={}, eventType={}. Error: {}",
                    aggregateType, aggregateId, eventType, e.getMessage(), e);
            throw new RuntimeException("Failed to save outbox event for " + eventType, e); // Re-throw to ensure transaction rollback
        }
    }
}
