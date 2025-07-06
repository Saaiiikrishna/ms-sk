package com.mysillydreams.inventorycore.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mysillydreams.inventorycore.domain.OutboxEvent;
import com.mysillydreams.inventorycore.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class OutboxEventService {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventService.class);

    private final OutboxRepository outboxRepository;
    // ObjectMapper can be used if complex objects need conversion to Map<String, Object>,
    // but for simple Map.of(...) it's straightforward.
    // private final ObjectMapper objectMapper;

    /**
     * Creates and saves an outbox event.
     * This method should be called within the same transaction as the domain entity changes
     * to ensure atomicity (guaranteed by @Transactional on ReservationServiceImpl).
     * The guide in ReservationServiceImpl calls this method as `outbox.publish(...)`.
     * I will rename this method to `publish` to match.
     *
     * @param aggregateType The type of the aggregate root (e.g., "Inventory").
     * @param aggregateId   The ID of the aggregate root (e.g., SKU).
     * @param eventType     The type of the event (e.g., "order.reservation.succeeded").
     * @param payload       The event payload as a Map.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(String aggregateType, String aggregateId, String eventType, Map<String, Object> payload) {
        try {
            OutboxEvent outboxEvent = new OutboxEvent();
            // ID and createdAt are set by @PrePersist in OutboxEvent entity
            outboxEvent.setAggregateType(aggregateType);
            outboxEvent.setAggregateId(aggregateId);
            outboxEvent.setEventType(eventType);
            outboxEvent.setPayload(payload); // Payload is already a Map<String, Object>
            outboxEvent.setProcessed(false);

            outboxRepository.save(outboxEvent);
            log.info("Saved outbox event: type={}, aggregateId={}, eventType={}", aggregateType, aggregateId, eventType);
        } catch (Exception e) {
            log.error("Failed to create and save outbox event: type={}, aggregateId={}, eventType={}. Error: {}",
                    aggregateType, aggregateId, eventType, e.getMessage(), e);
            // Re-throw to ensure the calling transaction (from ReservationServiceImpl) rolls back
            throw new RuntimeException("Failed to save outbox event for " + eventType + " and aggregate " + aggregateId, e);
        }
    }
}
