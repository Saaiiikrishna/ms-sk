package com.mysillydreams.userservice.service.support;

import com.mysillydreams.userservice.domain.support.SupportTicket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
public class SupportKafkaClient {

    private static final Logger logger = LoggerFactory.getLogger(SupportKafkaClient.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    private final String ticketCreatedTopic;
    private final String ticketUpdatedTopic;

    @Autowired
    public SupportKafkaClient(KafkaTemplate<String, Object> kafkaTemplate,
                              @Value("${support.topic.ticketCreated:support.ticket.created.v1}") String ticketCreatedTopic,
                              @Value("${support.topic.ticketUpdated:support.ticket.updated.v1}") String ticketUpdatedTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.ticketCreatedTopic = ticketCreatedTopic;
        this.ticketUpdatedTopic = ticketUpdatedTopic;
    }

    /**
     * Publishes an event when a new support ticket is created.
     * Uses SupportTicket ID as the Kafka message key.
     * @param ticket The created SupportTicket.
     */
    public void publishSupportTicketCreated(SupportTicket ticket) {
        if (ticket == null || ticket.getId() == null) {
            logger.warn("Attempted to publish ticket created event for null ticket or ticket with null ID. Skipping.");
            return;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("ticketId", ticket.getId().toString());
        payload.put("customerId", ticket.getCustomerId().toString());
        payload.put("subject", ticket.getSubject());
        // Description can be long, consider if it's always needed in the event or if a summary/truncation is better.
        // payload.put("description", ticket.getDescription());
        payload.put("status", ticket.getStatus().toString());
        payload.put("createdAt", ticket.getCreatedAt().toString());
        if (ticket.getAssignedTo() != null) {
            payload.put("assignedToSupportProfileId", ticket.getAssignedTo().getId().toString());
        }
        payload.put("eventType", "SupportTicketCreated");

        logger.info("Publishing SupportTicketCreated event for TicketId: {}, CustomerId: {}, Topic: {}",
                ticket.getId(), ticket.getCustomerId(), ticketCreatedTopic);

        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(ticketCreatedTopic, ticket.getId().toString(), payload);
        addKafkaCallback(future, "SupportTicketCreated", ticket.getId().toString());
    }

    /**
     * Publishes an event when a support ticket is updated (e.g., status change, new message, assignment).
     * Uses SupportTicket ID as the Kafka message key.
     * @param ticket The updated SupportTicket.
     * @param oldStatus Optional: The previous status, if the update was a status change.
     * @param messageId Optional: The ID of a new message if the update was a new message.
     */
    public void publishSupportTicketUpdated(SupportTicket ticket, String oldStatus, UUID messageId) {
         if (ticket == null || ticket.getId() == null) {
            logger.warn("Attempted to publish ticket updated event for null ticket or ticket with null ID. Skipping.");
            return;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("ticketId", ticket.getId().toString());
        payload.put("customerId", ticket.getCustomerId().toString());
        payload.put("newStatus", ticket.getStatus().toString());
        if (oldStatus != null && !oldStatus.isEmpty()) {
            payload.put("oldStatus", oldStatus);
        }
        if (ticket.getAssignedTo() != null) {
            payload.put("assignedToSupportProfileId", ticket.getAssignedTo().getId().toString());
        }
        if (messageId != null) {
            payload.put("newMessageId", messageId.toString());
        }
        payload.put("updatedAt", ticket.getUpdatedAt().toString());
        payload.put("eventType", "SupportTicketUpdated");

        logger.info("Publishing SupportTicketUpdated event for TicketId: {}, NewStatus: {}, Topic: {}",
                ticket.getId(), ticket.getStatus(), ticketUpdatedTopic);

        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(ticketUpdatedTopic, ticket.getId().toString(), payload);
        addKafkaCallback(future, "SupportTicketUpdated", ticket.getId().toString());
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
