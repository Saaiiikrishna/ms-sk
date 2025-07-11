package com.mysillydreams.delivery.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mysillydreams.delivery.domain.OutboxEvent;
import com.mysillydreams.delivery.repository.OutboxRepository;
import com.mysillydreams.delivery.util.DeliveryAvroClassMapper; // Specific AvroClassMapper for Delivery Service events

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class OutboxPoller {

    private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);
    private static final int BATCH_SIZE = 50;

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplateAvro; // Using Avro-configured template
    private final ObjectMapper objectMapper;
    private final DeliveryAvroClassMapper deliveryAvroClassMapper; // Specific mapper

    // Constructor for explicit injection
    public OutboxPoller(OutboxRepository outboxRepository,
                        @Qualifier("kafkaTemplate") KafkaTemplate<String, Object> kafkaTemplateAvro, // Assuming default KafkaTemplate is the Avro one
                        ObjectMapper objectMapper,
                        DeliveryAvroClassMapper deliveryAvroClassMapper) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplateAvro = kafkaTemplateAvro;
        this.objectMapper = objectMapper;
        this.deliveryAvroClassMapper = deliveryAvroClassMapper;
    }

    // Topic determination logic: OutboxEvent.eventType is expected to be the topic name.
    // If specific topics are configured via @Value, they can be used for validation or specific routing if needed,
    // but generally, the eventType stored in the outbox should be the destination topic.

    @Scheduled(fixedDelayString = "${app.outbox.poll-delay-ms:3000}", initialDelayString = "${app.outbox.initial-delay-ms:7000}")
    @Transactional
    public void pollAndPublish() {
        Pageable pageable = PageRequest.of(0, BATCH_SIZE);
        List<OutboxEvent> eventsToProcess = outboxRepository.findByProcessedFalseOrderByCreatedAtAsc(pageable);

        if (eventsToProcess.isEmpty()) {
            return;
        }

        log.info("[DeliveryOutboxPoller] Found {} unprocessed outbox events to publish.", eventsToProcess.size());

        for (OutboxEvent event : eventsToProcess) {
            String topic = event.getEventType(); // Assuming eventType *is* the topic name

            if (topic == null || topic.trim().isEmpty()) {
                log.error("[DeliveryOutboxPoller] Topic is null or empty for outbox event id: {}, type: {}. Skipping.", event.getId(), event.getEventType());
                // TODO: Consider error handling, e.g., marking as unprocessable after N retries.
                continue;
            }

            Object avroPayload;
            try {
                Class<?> avroClass = deliveryAvroClassMapper.getClassForEventType(event.getEventType());
                if (avroClass == null) {
                    log.error("[DeliveryOutboxPoller] No Avro class mapping for eventType: {}. Skipping event id: {}", event.getEventType(), event.getId());
                    continue;
                }
                JsonNode jsonPayload = event.getPayload();
                if (jsonPayload == null || jsonPayload.isNull()) {
                     log.error("[DeliveryOutboxPoller] Payload is null for event id: {}, type: {}. Skipping.", event.getId(), event.getEventType());
                     continue;
                }
                avroPayload = objectMapper.treeToValue(jsonPayload, avroClass);
            } catch (Exception e) {
                log.error("[DeliveryOutboxPoller] Failed to deserialize JsonNode payload to Avro for event id: {}, type: {}. Error: {}",
                    event.getId(), event.getEventType(), e.getMessage(), e);
                continue;
            }

            log.debug("[DeliveryOutboxPoller] Publishing outbox event id: {}, type: {} (as Avro type: {}) to topic: {}",
                      event.getId(), event.getEventType(), avroPayload.getClass().getSimpleName(), topic);

            kafkaTemplateAvro.send(topic, event.getAggregateId(), avroPayload)
                .addCallback(
                    sendResult -> {
                        // This update should be in its own transaction or handled carefully.
                        // For now, assume @Transactional on pollAndPublish covers it, but if it fails after this save
                        // and before overall commit, this save is also rolled back.
                        // A more robust approach might involve a separate service call with REQUIRES_NEW.
                        markEventAsProcessedInNewTransaction(event.getId());
                        log.info("[DeliveryOutboxPoller] Successfully published and marked as processed: outbox event id: {}, type: {}", event.getId(), event.getEventType());
                    },
                    ex -> {
                        log.error("[DeliveryOutboxPoller] Failed to publish outbox event id: {}, type: {} to topic: {}. Error: {}",
                                event.getId(), event.getEventType(), topic, ex.getMessage(), ex);
                        // Event remains unprocessed for next poll. Implement retry limits / dead letter for outbox later.
                    }
                );
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markEventAsProcessedInNewTransaction(UUID eventId) {
        OutboxEvent event = outboxRepository.findById(eventId)
            .orElseThrow(() -> new EntityNotFoundException("OutboxEvent not found for id: " + eventId + " while trying to mark as processed."));
        event.setProcessed(true);
        outboxRepository.save(event);
    }
}
