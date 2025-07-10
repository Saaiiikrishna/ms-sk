package com.mysillydreams.auth.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class KafkaPublisher {

    private static final Logger logger = LoggerFactory.getLogger(KafkaPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    public KafkaPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Publishes an event to the specified Kafka topic.
     * The event payload will be serialized to JSON.
     * A random UUID is used as the Kafka message key to ensure distribution across partitions,
     * unless a specific keying strategy is required for ordering for a particular event type.
     *
     * @param topic The Kafka topic to publish to.
     * @param eventKey The specific type of event (e.g., AuthEvents.PASSWORD_ROTATED), used for logging and potentially for message headers.
     * @param payload The event data to publish.
     */
    public void publishEvent(String topic, String eventKey, Object payload) {
        publishEvent(topic, UUID.randomUUID().toString(), eventKey, payload);
    }

    /**
     * Publishes an event to the specified Kafka topic with a specific Kafka message key.
     * The event payload will be serialized to JSON.
     *
     * @param topic The Kafka topic to publish to.
     * @param messageKey The key for the Kafka message.
     * @param eventKey The specific type of event (e.g., AuthEvents.PASSWORD_ROTATED), used for logging and potentially for message headers.
     * @param payload The event data to publish.
     */
    public void publishEvent(String topic, String messageKey, String eventKey, Object payload) {
        logger.info("Attempting to publish event '{}' with key '{}' to topic '{}'", eventKey, messageKey, topic);
        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(topic, messageKey, payload);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                logger.info("Successfully published event '{}' with key '{}' to topic '{}', partition {}, offset {}",
                        eventKey,
                        messageKey,
                        topic,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            } else {
                logger.error("Failed to publish event '{}' with key '{}' to topic '{}'",
                        eventKey, messageKey, topic, ex);
                // Implement retry mechanisms or dead-letter queue (DLQ) handling here if necessary
            }
        });
    }
}
