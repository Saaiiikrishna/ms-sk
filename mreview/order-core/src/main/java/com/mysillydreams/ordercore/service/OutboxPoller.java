package com.mysillydreams.ordercore.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mysillydreams.ordercore.domain.OutboxEvent;
import com.mysillydreams.ordercore.repository.OutboxRepository;
import com.mysillydreams.ordercore.util.AvroClassMapper; // Import the mapper
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
// @RequiredArgsConstructor will generate a constructor for all final fields.
// We need to ensure correct KafkaTemplate (Avro one) is injected.
// Using explicit constructor or @Qualifier if multiple KafkaTemplates exist.
public class OutboxPoller {

    private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);
    private static final int BATCH_SIZE = 50;

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplateAvro; // For Avro messages
    private final ObjectMapper objectMapper;

    // Constructor for explicit injection
    public OutboxPoller(OutboxRepository outboxRepository,
                        @Qualifier("kafkaTemplateAvro") KafkaTemplate<String, Object> kafkaTemplateAvro,
                        ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplateAvro = kafkaTemplateAvro;
        this.objectMapper = objectMapper;
    }

    @Value("${kafka.topics.orderCreated:order.core.created}")
    private String orderCreatedTopic;
    @Value("${kafka.topics.orderCancelled:order.core.cancelled}")
    private String orderCancelledTopic;
    // This one might not be directly used if eventType itself is the topic for status updates
    // @Value("${kafka.topics.orderStatusUpdatedPrefix:order.core.status}")
    // private String orderStatusUpdatedPrefix;


    private String getTopicForEventType(String eventType) {
        // This logic needs to be robust and align with how event types are stored
        // and what topics they correspond to.
        // For now, assume eventType from OutboxEvent *is* the topic name if not specifically mapped.
        if ("order.created".equalsIgnoreCase(eventType)) { // This should match what OutboxEventService stores for eventType
            return orderCreatedTopic; // This topic is from Order-Core's perspective
        } else if ("order.cancelled".equalsIgnoreCase(eventType)) {
            return orderCancelledTopic;
        }
        // If eventType like "order.status.paid" is stored, it can be the topic directly.
        // Or, if only "paid" is stored, then construct: orderStatusUpdatedPrefix + "." + eventType
        // For now, let's assume eventType stored in Outbox IS the full topic name.
        if (eventType != null && (eventType.startsWith("order.core.status.") || eventType.startsWith("order.status."))) {
             return eventType; // Example: "order.core.status.paid" is the topic
        }
        log.warn("No specific topic mapping for eventType: '{}'. Using eventType as topic name.", eventType);
        return eventType;
    }

    private Class<?> getAvroClassForEventType(String eventType) {
        return AvroClassMapper.getClassForEventType(eventType);
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-delay-ms:2000}", initialDelayString = "${app.outbox.initial-delay-ms:5000}")
    @Transactional
    public void pollAndPublish() {
        Pageable pageable = PageRequest.of(0, BATCH_SIZE);
        List<OutboxEvent> eventsToProcess = outboxRepository.findByProcessedFalseOrderByCreatedAtAsc(pageable);

        if (eventsToProcess.isEmpty()) {
            return;
        }

        log.info("Found {} unprocessed outbox events to publish.", eventsToProcess.size());

        for (OutboxEvent event : eventsToProcess) {
            String topic = getTopicForEventType(event.getEventType());
            if (topic == null || topic.trim().isEmpty()) { // Added empty check
                log.error("Could not determine topic for outbox event id: {}, type: {}. Skipping.", event.getId(), event.getEventType());
                continue;
            }

            Object avroPayload;
            try {
                Class<?> avroClass = getAvroClassForEventType(event.getEventType());
                if (avroClass == null) {
                    log.error("No Avro class mapping for eventType: {}. Skipping event id: {}", event.getEventType(), event.getId());
                    continue;
                }
                JsonNode jsonPayload = event.getPayload();
                if (jsonPayload == null || jsonPayload.isNull()) {
                     log.error("Payload is null for event id: {}, type: {}. Skipping.", event.getId(), event.getEventType());
                     continue;
                }
                avroPayload = objectMapper.treeToValue(jsonPayload, avroClass);
            } catch (Exception e) {
                log.error("Failed to deserialize JsonNode payload to Avro for event id: {}, type: {}. Error: {}",
                    event.getId(), event.getEventType(), e.getMessage(), e);
                continue;
            }

            log.debug("Publishing outbox event id: {}, type: {} (as Avro type: {}) to topic: {}",
                      event.getId(), event.getEventType(), avroPayload.getClass().getSimpleName(), topic);

            kafkaTemplateAvro.send(topic, event.getAggregateId(), avroPayload)
                .addCallback(
                    sendResult -> {
                        event.setProcessed(true);
                        outboxRepository.save(event);
                        log.info("Successfully published and marked as processed: outbox event id: {}, type: {}", event.getId(), event.getEventType());
                    },
                    ex -> {
                        log.error("Failed to publish outbox event id: {}, type: {} to topic: {}. Error: {}",
                                event.getId(), event.getEventType(), topic, ex.getMessage(), ex);
                    }
                );
        }
    }
}
