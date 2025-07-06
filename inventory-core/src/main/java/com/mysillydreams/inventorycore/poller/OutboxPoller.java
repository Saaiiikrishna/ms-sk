package com.mysillydreams.inventorycore.poller;

import com.mysillydreams.inventorycore.domain.OutboxEvent;
import com.mysillydreams.inventorycore.repository.OutboxRepository;
import com.mysillydreams.inventorycore.service.OutboxEventService; // For transactional method
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxPoller {

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate; // Using the avroKafkaTemplate bean

    // Note: The guide's OutboxPoller uses ev.getEventType().contains("succeeded") ? succTopic : failTopic;
    // This implies eventType in OutboxEvent might be a generic type like "InventoryReservationSucceeded"
    // and not the actual topic name.
    // However, ReservationServiceImpl currently stores the topic name itself in eventType.
    // If eventType IS the topic name, then the logic `String topic = ev.getEventType()` is simpler.
    // The current ReservationServiceImpl sets eventType to reservationSucceededTopic / reservationFailedTopic.
    // So, ev.getEventType() will directly give the topic.

    // Let's assume eventType in OutboxEvent IS the topic name as per current ReservationServiceImpl.
    // If it were a logical type, we'd need these @Value fields:
    // @Value("${kafka.topics.reservationSucceeded}") private String reservationSucceededTopic;
    // @Value("${kafka.topics.reservationFailed}") private String reservationFailedTopic;


    // We need a transactional method to update the OutboxEvent, preferably in its own service
    // or by self-injecting this poller to call a @Transactional method.
    // For simplicity, let's add a helper method here and call it.
    // A better approach would be a dedicated method in OutboxEventService,
    // but a private @Transactional method called locally works if component-scan is correctly configured
    // or if called via self-injection. The current setup with REQUIRES_NEW on the public method
    // markEventAsProcessed should work when called from the CompletableFuture callback.


    @Scheduled(fixedDelayString = "${inventory.outbox.poll.delay:5000}", initialDelayString = "${inventory.outbox.poll.initialDelay:10000}")
    @Transactional(propagation = Propagation.REQUIRES_NEW) // Process each batch in a new transaction
    public void pollOutbox() {
        Pageable pageable = PageRequest.of(0, 50); // Process up to 50 events per poll
        // Using findByProcessedFalseOrderByCreatedAtAsc from OutboxRepository
        var eventsToProcess = outboxRepository.findByProcessedFalseOrderByCreatedAtAsc(pageable);

        if (eventsToProcess.isEmpty()) {
            return;
        }
        log.info("Polling outbox: Found {} unprocessed events.", eventsToProcess.size());

        for (OutboxEvent event : eventsToProcess) {
            String topic = event.getEventType(); // Directly use eventType as topic
            Object payload = event.getPayload(); // This is Map<String, Object>
            String key = event.getAggregateId(); // SKU

            log.debug("Attempting to send event ID {} to topic {}: key={}, payload={}", event.getId(), topic, key, payload);

            try {
                // kafkaTemplate.send returns CompletableFuture
                CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(topic, key, payload);

                future.whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.info("Successfully sent event ID {} to topic {} partition {} offset {}.",
                                event.getId(), topic, result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
                        // Mark as processed in a new transaction to ensure atomicity for this specific event's update
                        markEventAsProcessed(event.getId());
                    } else {
                        // Kafka send failed
                        log.error("Failed to send event ID {} to topic {}. Error: {}", event.getId(), topic, ex.getMessage(), ex);
                        // Error handling:
                        // - Increment retry count (if OutboxEvent has such a field)
                        // - Log for monitoring
                        // - Potentially move to a dead letter table after N retries
                        // - For now, it will be picked up by the next poll if not marked processed.
                        // This could lead to repeated processing attempts if Kafka is down.
                        // Consider adding a transient error check or max attempts.
                    }
                });
            } catch (Exception e) {
                // This catches immediate exceptions from the .send() call itself (e.g., serialization issues before async send)
                log.error("Immediate exception while trying to send event ID {} to topic {}. Error: {}", event.getId(), topic, e.getMessage(), e);
                // Event will not be marked processed and will be retried.
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markEventAsProcessed(UUID eventId) {
        try {
            OutboxEvent event = outboxRepository.findById(eventId)
                .orElseThrow(() -> new IllegalStateException("OutboxEvent not found by id " + eventId + " for processing update. This should not happen."));
            event.setProcessed(true);
            outboxRepository.save(event);
            log.debug("Marked event ID {} as processed.", eventId);
        } catch (Exception e) {
            log.error("Failed to mark event ID {} as processed. Error: {}", eventId, e.getMessage(), e);
            // This is problematic. If the event was sent but marking failed, it might be resent.
            // This requires careful consideration for idempotency or more robust 2PC-like mechanisms if possible.
            // For now, log and it might be retried by the poller (undesirable if already sent).
        }
    }
}
