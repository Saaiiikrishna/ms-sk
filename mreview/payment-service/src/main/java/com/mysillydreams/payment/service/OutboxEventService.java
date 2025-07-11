package com.mysillydreams.payment.service; // Changed package

import com.mysillydreams.payment.domain.OutboxEvent; // Changed import
import com.mysillydreams.payment.repository.OutboxRepository; // Changed import
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

    /**
     * Creates and saves an outbox event.
     * This method must be called within an existing transaction to ensure atomicity
     * with domain entity changes.
     *
     * @param aggregateType The type of the aggregate root (e.g., "Payment").
     * @param aggregateId   The ID of the aggregate root (e.g., PaymentTransaction ID or Order ID).
     * @param eventType     The type of the event (e.g., "order.payment.succeeded").
     * @param payload       The event payload as a Map.
     */
    @Transactional(propagation = Propagation.MANDATORY) // Ensures this is part of an existing transaction
    public void publish(String aggregateType, String aggregateId, String eventType, Map<String, Object> payload) {
        try {
            OutboxEvent outboxEvent = new OutboxEvent();
            // ID and createdAt are set by @PrePersist in OutboxEvent entity
            outboxEvent.setAggregateType(aggregateType);
            outboxEvent.setAggregateId(aggregateId);
            outboxEvent.setEventType(eventType);
            outboxEvent.setPayload(payload);
            outboxEvent.setProcessed(false);
            // outboxEvent.setAttemptCount(0); // If using retry logic

            outboxRepository.save(outboxEvent);
            log.info("Saved outbox event: ID={}, type={}, aggregateId={}, eventType={}",
                    outboxEvent.getId(), aggregateType, aggregateId, eventType);
        } catch (Exception e) {
            log.error("Failed to create and save outbox event: type={}, aggregateId={}, eventType={}. Error: {}",
                    aggregateType, aggregateId, eventType, e.getMessage(), e);
            // Re-throw to ensure the calling transaction rolls back
            throw new RuntimeException("Failed to save outbox event for " + eventType + ", aggregate " + aggregateId, e);
        }
    }
}
