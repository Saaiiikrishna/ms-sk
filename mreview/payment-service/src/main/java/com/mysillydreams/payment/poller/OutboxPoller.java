package com.mysillydreams.payment.poller; // Changed package

import com.mysillydreams.payment.domain.OutboxEvent; // Changed import
import com.mysillydreams.payment.repository.OutboxRepository; // Changed import
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID; // Added import
import java.util.concurrent.CompletableFuture;

@Service
// @RequiredArgsConstructor // Cannot use with manual constructor for metrics
@Slf4j
public class OutboxPoller {

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry; // For outbox gauge

    public OutboxPoller(OutboxRepository outboxRepository,
                        KafkaTemplate<String, Object> kafkaTemplate,
                        MeterRegistry meterRegistry) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.meterRegistry = meterRegistry;

        // Register a gauge for the outbox backlog size
        Gauge.builder("payment.service.outbox.backlog.size", outboxRepository,
                        OutboxRepository::countByProcessedFalse) // Method reference to the new method
                .description("Current number of unprocessed events in the outbox")
                .register(meterRegistry);
    }

    // In PaymentServiceImpl, the eventType stored in OutboxEvent IS the topic name.

    @Scheduled(fixedDelayString = "${payment.outbox.poll.delay:5000}",
               initialDelayString = "${payment.outbox.poll.initialDelay:10000}")
    // Process each batch of events in its own transaction.
    // Individual event processing (Kafka send + marking processed) should also be transactional.
    public void pollAndPublishOutboxEvents() {
        Pageable pageable = PageRequest.of(0, 50); // Configurable batch size?
        var eventsToProcess = outboxRepository.findByProcessedFalseOrderByCreatedAtAsc(pageable);

        if (eventsToProcess.isEmpty()) {
            return; // No work to do
        }
        log.info("[OutboxPoller] Found {} unprocessed events to publish.", eventsToProcess.size());

        for (OutboxEvent event : eventsToProcess) {
            String topic = event.getEventType();
            String key = event.getAggregateId(); // e.g., PaymentTransaction ID or Order ID
            Object payload = event.getPayload(); // Map<String, Object>

            log.debug("[OutboxPoller] Attempting to send event ID {} to topic {}: key={}, payload={}",
                    event.getId(), topic, key, payload);

            try {
                CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(topic, key, payload);

                future.whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.info("[OutboxPoller] Successfully sent event ID {} to topic {} [partition {}, offset {}]. Marking as processed.",
                                event.getId(), topic, result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
                        // Mark as processed in a NEW transaction for this specific event.
                        markEventAsProcessedInNewTransaction(event.getId());
                    } else {
                        log.error("[OutboxPoller] Failed to send event ID {} to topic {}. Error: {}. Will retry on next poll.",
                                event.getId(), topic, ex.getMessage(), ex);
                        // Optional: Increment attempt count here if OutboxEvent has such a field and this is a retryable error.
                        // If OutboxEvent.attemptCount > maxAttempts, move to DLQ or log permanently.
                        // For now, it remains unprocessed and will be picked up again.
                    }
                });
            } catch (Exception e) {
                // Catches immediate errors from kafkaTemplate.send() (e.g., serialization issues)
                log.error("[OutboxPoller] Immediate exception while attempting to send event ID {}. Error: {}. Will retry on next poll.",
                        event.getId(), e.getMessage(), e);
            }
        }
    }

    /**
     * Marks a single outbox event as processed in its own new transaction.
     * This is intended to be called after a successful Kafka publish.
     * Using REQUIRES_NEW to ensure that the update to this event's status
     * is committed independently of the main polling loop's transaction,
     * and regardless of other events in the batch.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markEventAsProcessedInNewTransaction(UUID eventId) {
        try {
            OutboxEvent event = outboxRepository.findById(eventId)
                .orElseThrow(() -> {
                    // This should ideally not happen if the event was just fetched.
                    log.error("[OutboxPoller] CRITICAL: OutboxEvent with ID {} not found for marking as processed, though it was just processed for Kafka send.", eventId);
                    return new IllegalStateException("OutboxEvent not found by id " + eventId + " for update.");
                });
            event.setProcessed(true);
            outboxRepository.save(event);
            log.debug("[OutboxPoller] Successfully marked event ID {} as processed.", eventId);
        } catch (Exception e) {
            // This is a problematic state: event published to Kafka, but failed to mark as processed in DB.
            // This could lead to duplicate processing if not handled carefully (e.g. by consumer idempotency).
            log.error("[OutboxPoller] CRITICAL: Failed to mark event ID {} as processed after successful Kafka send. Error: {}. Risk of duplicate event.",
                    eventId, e.getMessage(), e);
            // Depending on strategy:
            // - Log for manual intervention.
            // - If consumer is idempotent, duplicate is less harmful but still inefficient.
            // - More advanced: A separate "cleanup" process for such events.
        }
    }
}
