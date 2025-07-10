package com.mysillydreams.userservice.service.delivery;

import com.mysillydreams.userservice.domain.delivery.OrderAssignment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import java.util.concurrent.CompletableFuture;

import java.util.HashMap;
import java.util.Map;

@Service
public class DeliveryKafkaClient {

    private static final Logger logger = LoggerFactory.getLogger(DeliveryKafkaClient.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    private final String orderAssignedTopic;
    private final String deliveryStatusChangedTopic;

    @Autowired
    public DeliveryKafkaClient(KafkaTemplate<String, Object> kafkaTemplate,
                               @Value("${delivery.topic.orderAssigned:order.assigned.v1}") String orderAssignedTopic,
                               @Value("${delivery.topic.deliveryStatusChanged:delivery.status.changed.v1}") String deliveryStatusChangedTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.orderAssignedTopic = orderAssignedTopic;
        this.deliveryStatusChangedTopic = deliveryStatusChangedTopic;
    }

    /**
     * Publishes an event when an order is assigned to a delivery user.
     * Uses OrderAssignment ID as the Kafka message key.
     * @param assignment The OrderAssignment that was created.
     */
    public void publishOrderAssigned(OrderAssignment assignment) {
        if (assignment == null || assignment.getId() == null) {
            logger.warn("Attempted to publish order assigned event for null assignment or assignment with null ID. Skipping.");
            return;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("assignmentId", assignment.getId().toString());
        payload.put("orderId", assignment.getOrderId().toString());
        if (assignment.getDeliveryProfile() != null) {
            payload.put("deliveryProfileId", assignment.getDeliveryProfile().getId().toString());
            if (assignment.getDeliveryProfile().getUser() != null) {
                payload.put("deliveryUserId", assignment.getDeliveryProfile().getUser().getId().toString());
            }
        }
        payload.put("assignmentType", assignment.getType().toString());
        payload.put("status", assignment.getStatus().toString()); // Should be ASSIGNED
        payload.put("assignedAt", assignment.getAssignedAt().toString());
        payload.put("eventType", "OrderAssigned");

        logger.info("Publishing OrderAssigned event for AssignmentId: {}, OrderId: {}, Topic: {}",
                assignment.getId(), assignment.getOrderId(), orderAssignedTopic);

        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(orderAssignedTopic, assignment.getId().toString(), payload);
        addKafkaCallback(future, "OrderAssigned", assignment.getId().toString());
    }

    /**
     * Publishes an event when a delivery assignment's status changes.
     * Uses OrderAssignment ID as the Kafka message key.
     * @param assignment The OrderAssignment whose status changed.
     * @param oldStatus Optional: the previous status, if known and relevant for the event.
     */
    public void publishDeliveryStatusChanged(OrderAssignment assignment, String oldStatus) {
         if (assignment == null || assignment.getId() == null) {
            logger.warn("Attempted to publish delivery status changed event for null assignment or assignment with null ID. Skipping.");
            return;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("assignmentId", assignment.getId().toString());
        payload.put("orderId", assignment.getOrderId().toString());
        if (assignment.getDeliveryProfile() != null) {
            payload.put("deliveryProfileId", assignment.getDeliveryProfile().getId().toString());
        }
        payload.put("newStatus", assignment.getStatus().toString());
        if (oldStatus != null && !oldStatus.isEmpty()) {
            payload.put("oldStatus", oldStatus);
        }
        payload.put("statusChangeTimestamp", assignment.getLastUpdatedAt().toString()); // Assuming lastUpdatedAt reflects this change
        payload.put("eventType", "DeliveryStatusChanged");


        logger.info("Publishing DeliveryStatusChanged event for AssignmentId: {}, OrderId: {}, NewStatus: {}, Topic: {}",
                assignment.getId(), assignment.getOrderId(), assignment.getStatus(), deliveryStatusChangedTopic);

        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(deliveryStatusChangedTopic, assignment.getId().toString(), payload);
        addKafkaCallback(future, "DeliveryStatusChanged", assignment.getId().toString());
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
