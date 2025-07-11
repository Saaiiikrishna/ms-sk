package com.mysillydreams.userservice.service;

import com.mysillydreams.userservice.domain.UserEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
public class UserEventKafkaClient {

    private static final Logger logger = LoggerFactory.getLogger(UserEventKafkaClient.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String userArchivedTopic;
    // Add topics for user.created, user.updated later if needed

    @Autowired
    public UserEventKafkaClient(KafkaTemplate<String, Object> kafkaTemplate,
                                @Value("${user.topic.archived:user.archived.v1}") String userArchivedTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.userArchivedTopic = userArchivedTopic;
    }

    public void publishUserArchived(UserEntity user) {
        if (user == null || user.getId() == null) {
            logger.warn("Attempted to publish user archived event for null user or user with null ID. Skipping.");
            return;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", user.getId().toString());
        payload.put("referenceId", user.getReferenceId());
        payload.put("archivedAt", user.getArchivedAt() != null ? user.getArchivedAt().toString() : null);
        payload.put("eventType", "UserArchived");

        logger.info("Publishing UserArchived event for UserID: {}, ReferenceID: {}, Topic: {}",
                user.getId(), user.getReferenceId(), userArchivedTopic);

        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(userArchivedTopic, user.getId().toString(), payload);
        addKafkaCallback(future, "UserArchived", user.getId().toString());
    }

    private void addKafkaCallback(CompletableFuture<SendResult<String, Object>> future, String eventType, String recordKey) {
        future.whenComplete((result, ex) -> {
            if (ex == null) {
                logger.info("Successfully published '{}' event for Key: {}. Topic: {}, Partition: {}, Offset: {}",
                        eventType, recordKey,
                        result.getRecordMetadata().topic(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            } else {
                logger.error("Failed to publish '{}' event for Key: {}. Error: {}",
                        eventType, recordKey, ex.getMessage(), ex);
            }
        });
    }
}
