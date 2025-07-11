package com.mysillydreams.userservice.service.delivery;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mysillydreams.userservice.domain.delivery.DeliveryEvent;
import com.mysillydreams.userservice.domain.delivery.OrderAssignment;
import com.mysillydreams.userservice.repository.delivery.DeliveryEventRepository;
import com.mysillydreams.userservice.repository.delivery.OrderAssignmentRepository;

import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.Map;
import java.util.UUID;

@Service
public class DeliveryEventService {

    private static final Logger logger = LoggerFactory.getLogger(DeliveryEventService.class);

    private final DeliveryEventRepository deliveryEventRepository;
    private final OrderAssignmentRepository orderAssignmentRepository; // To fetch OrderAssignment
    private final ObjectMapper objectMapper; // For converting Map payload to JSON String

    @Autowired
    public DeliveryEventService(DeliveryEventRepository deliveryEventRepository,
                                OrderAssignmentRepository orderAssignmentRepository,
                                ObjectMapper objectMapper) {
        this.deliveryEventRepository = deliveryEventRepository;
        this.orderAssignmentRepository = orderAssignmentRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Records a delivery event for a given order assignment.
     *
     * @param assignmentId The UUID of the order assignment.
     * @param eventType    A string representing the type of event (e.g., "ARRIVED", "PHOTO_TAKEN").
     * @param payload      A Map containing event-specific data, to be stored as a JSON string.
     * @return The created DeliveryEvent.
     * @throws EntityNotFoundException if the order assignment does not exist.
     * @throws RuntimeException        if payload serialization to JSON fails.
     */
    @Transactional
    public DeliveryEvent recordEvent(UUID assignmentId, String eventType, Map<String, Object> payload) {
        Assert.notNull(assignmentId, "Order Assignment ID cannot be null.");
        Assert.hasText(eventType, "Event type cannot be blank.");
        // Payload can be null or empty

        logger.info("Recording event type '{}' for OrderAssignment ID: {}", eventType, assignmentId);

        OrderAssignment assignment = orderAssignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new EntityNotFoundException("OrderAssignment not found with ID: " + assignmentId));

        DeliveryEvent event = new DeliveryEvent();
        event.setAssignment(assignment);
        event.setEventType(eventType);

        if (payload != null && !payload.isEmpty()) {
            try {
                event.setPayload(objectMapper.writeValueAsString(payload));
            } catch (JsonProcessingException e) {
                logger.error("Failed to serialize payload to JSON for event type '{}', assignment ID {}: {}",
                        eventType, assignmentId, e.getMessage(), e);
                // Depending on policy, either throw, or save event with null/error payload
                throw new RuntimeException("Failed to serialize event payload to JSON.", e);
            }
        }

        DeliveryEvent savedEvent = deliveryEventRepository.save(event);
        logger.info("DeliveryEvent ID: {} recorded for event type '{}', assignment ID {}",
                savedEvent.getId(), eventType, assignmentId);

        return savedEvent;
    }

    /**
     * Checks if a specific event type has occurred for a given assignment.
     *
     * @param assignment The OrderAssignment entity.
     * @param eventType  The event type string to check for.
     * @return true if at least one event of the specified type exists for the assignment, false otherwise.
     */
    @Transactional(readOnly = true)
    public boolean hasEventOccurred(OrderAssignment assignment, String eventType) {
        Assert.notNull(assignment, "OrderAssignment cannot be null.");
        Assert.hasText(eventType, "Event type cannot be blank.");

        long count = deliveryEventRepository.countByAssignmentAndEventType(assignment, eventType);
        return count > 0;
    }

    /**
     * Verifies that required events happened in sequence for an assignment.
     * Example: OTP_VERIFIED should only happen after PHOTO_TAKEN.
     * This is a placeholder for more complex sequence/state validation logic.
     *
     * @param assignment The OrderAssignment entity.
     * @param targetEventType The event type we are about to record or action we are about to perform.
     * @throws IllegalStateException if prerequisite events have not occurred.
     */
    public void verifyEventSequence(OrderAssignment assignment, String targetEventType) {
        // This is a simplified example. A proper state machine or more detailed checks would be better.
        if ("OTP_VERIFIED".equals(targetEventType)) {
            if (!hasEventOccurred(assignment, "PHOTO_TAKEN")) { // Example prerequisite
                logger.warn("Attempt to record '{}' for assignment {} before 'PHOTO_TAKEN' event.", targetEventType, assignment.getId());
                throw new IllegalStateException("Cannot verify OTP before photo is taken for assignment: " + assignment.getId());
            }
        }
        // Add more sequence rules as needed
        // e.g., if ("COMPLETED_DELIVERY_PHOTO".equals(targetEventType)) {
        //     if (!hasEventOccurred(assignment, "ARRIVED_AT_DROPOFF")) {
        //         throw new IllegalStateException("Cannot record delivery photo before arrival at dropoff for assignment: " + assignment.getId());
        //     }
        // }
        logger.debug("Event sequence check passed for target event '{}' on assignment {}", targetEventType, assignment.getId());
    }

    // TODO: Add methods to retrieve events for an assignment if needed, though OrderAssignment.getEvents() handles this.
}
