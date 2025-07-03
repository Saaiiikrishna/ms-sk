package com.mysillydreams.userservice.service.delivery;

import com.mysillydreams.userservice.domain.delivery.AssignmentStatus;
import com.mysillydreams.userservice.domain.delivery.DeliveryProfile;
import com.mysillydreams.userservice.domain.delivery.OrderAssignment;
import com.mysillydreams.userservice.dto.delivery.OrderAssignmentDto; // For listAssignments
import com.mysillydreams.userservice.repository.delivery.DeliveryProfileRepository;
import com.mysillydreams.userservice.repository.delivery.OrderAssignmentRepository;

import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DeliveryAssignmentService {

    private static final Logger logger = LoggerFactory.getLogger(DeliveryAssignmentService.class);

    private final OrderAssignmentRepository orderAssignmentRepository;
    private final DeliveryProfileRepository deliveryProfileRepository; // To fetch DeliveryProfile
    private final DeliveryKafkaClient deliveryKafkaClient;
    private final DeliveryEventService deliveryEventService; // To check for required prior events

    // Define active statuses for querying current assignments
    private static final List<AssignmentStatus> ACTIVE_ASSIGNMENT_STATUSES = Arrays.asList(
            AssignmentStatus.ASSIGNED,
            AssignmentStatus.EN_ROUTE,
            AssignmentStatus.ARRIVED_AT_PICKUP,
            AssignmentStatus.PICKED_UP,
            AssignmentStatus.ARRIVED_AT_DROPOFF
    );

    @Autowired
    public DeliveryAssignmentService(OrderAssignmentRepository orderAssignmentRepository,
                                     DeliveryProfileRepository deliveryProfileRepository,
                                     DeliveryKafkaClient deliveryKafkaClient,
                                     DeliveryEventService deliveryEventService) {
        this.orderAssignmentRepository = orderAssignmentRepository;
        this.deliveryProfileRepository = deliveryProfileRepository;
        this.deliveryKafkaClient = deliveryKafkaClient;
        this.deliveryEventService = deliveryEventService;
    }

    /**
     * Lists all active assignments for a given delivery profile ID.
     * Active assignments are those not in COMPLETED, FAILED, or CANCELLED status.
     *
     * @param deliveryProfileId The UUID of the delivery profile.
     * @return A list of {@link OrderAssignmentDto}.
     * @throws EntityNotFoundException if the delivery profile does not exist.
     */
    @Transactional(readOnly = true)
    public List<OrderAssignmentDto> listActiveAssignmentsForProfile(UUID deliveryProfileId) {
        Assert.notNull(deliveryProfileId, "Delivery Profile ID cannot be null.");
        logger.debug("Listing active assignments for DeliveryProfile ID: {}", deliveryProfileId);

        DeliveryProfile profile = deliveryProfileRepository.findById(deliveryProfileId)
                .orElseThrow(() -> new EntityNotFoundException("DeliveryProfile not found with ID: " + deliveryProfileId));

        if (!profile.isActive()) {
            logger.warn("Attempt to list assignments for inactive DeliveryProfile ID: {}", deliveryProfileId);
            return List.of(); // Or throw an exception, depending on business rules
        }

        List<OrderAssignment> assignments = orderAssignmentRepository.findByDeliveryProfileAndStatusIn(
                profile, ACTIVE_ASSIGNMENT_STATUSES, Sort.by(Sort.Direction.ASC, "assignedAt")
        );

        // TODO: Enhance OrderAssignmentDto.from() or here to fetch actual order details (address, GPS)
        // from an Order Service based on assignment.getOrderId().
        return assignments.stream().map(OrderAssignmentDto::from).collect(Collectors.toList());
    }

    /**
     * Transitions the status of an order assignment.
     * Publishes a Kafka event on successful status change.
     *
     * @param assignmentId The UUID of the order assignment.
     * @param newStatus    The new status to transition to.
     * @param eventPayload Optional payload related to this status change (e.g., GPS for ARRIVED).
     * @return The updated OrderAssignment.
     * @throws EntityNotFoundException if the assignment does not exist.
     * @throws IllegalStateException   if the status transition is invalid or prerequisites are not met.
     */
    @Transactional
    public OrderAssignment updateAssignmentStatus(UUID assignmentId, AssignmentStatus newStatus, Map<String, Object> eventPayload) {
        Assert.notNull(assignmentId, "Order Assignment ID cannot be null.");
        Assert.notNull(newStatus, "New status cannot be null.");

        logger.info("Attempting to update status for OrderAssignment ID: {} to {}", assignmentId, newStatus);

        OrderAssignment assignment = orderAssignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new EntityNotFoundException("OrderAssignment not found with ID: " + assignmentId));

        AssignmentStatus oldStatus = assignment.getStatus();
        if (oldStatus == newStatus) {
            logger.info("OrderAssignment ID: {} is already in status {}. No update performed.", assignmentId, newStatus);
            return assignment;
        }

        // Validate status transition logic (can be more sophisticated)
        validateStatusTransition(assignment, oldStatus, newStatus);

        // Specific checks for certain transitions based on prior events
        if (newStatus == AssignmentStatus.COMPLETED) {
            if (!deliveryEventService.hasEventOccurred(assignment, "PHOTO_TAKEN")) {
                throw new IllegalStateException("Cannot complete assignment ID " + assignmentId + ": PHOTO_TAKEN event is required.");
            }
            if (!deliveryEventService.hasEventOccurred(assignment, "OTP_VERIFIED")) { // Or similar customer confirmation event
                throw new IllegalStateException("Cannot complete assignment ID " + assignmentId + ": OTP_VERIFIED event is required.");
            }
        }


        assignment.setStatus(newStatus);
        // assignment.setLastUpdatedAt(Instant.now()); // @UpdateTimestamp handles this

        OrderAssignment updatedAssignment = orderAssignmentRepository.save(assignment);
        logger.info("OrderAssignment ID: {} status updated from {} to {}", assignmentId, oldStatus, newStatus);

        // Publish event after successful state change
        deliveryKafkaClient.publishDeliveryStatusChanged(updatedAssignment, oldStatus.toString());

        // Optionally, record a generic "STATUS_CHANGED" DeliveryEvent as well
        // deliveryEventService.recordEvent(assignmentId, "STATUS_CHANGED_" + newStatus.toString(),
        //     Map.of("oldStatus", oldStatus.toString(), "newStatus", newStatus.toString()));


        return updatedAssignment;
    }

    private void validateStatusTransition(OrderAssignment assignment, AssignmentStatus oldStatus, AssignmentStatus newStatus) {
        logger.debug("Validating status transition for assignment {}: from {} to {}", assignment.getId(), oldStatus, newStatus);

        if (oldStatus == newStatus) return; // No transition

        // Terminal states cannot be transitioned from
        if (List.of(AssignmentStatus.COMPLETED, AssignmentStatus.FAILED, AssignmentStatus.CANCELLED).contains(oldStatus)) {
            throw new IllegalStateException("Cannot change status from a terminal state: " + oldStatus);
        }

        // Define valid transitions using a Map or more sophisticated state machine logic
        // Key: Current Status, Value: List of valid next statuses
        Map<AssignmentStatus, List<AssignmentStatus>> validTransitions = Map.of(
            AssignmentStatus.ASSIGNED, List.of(AssignmentStatus.EN_ROUTE, AssignmentStatus.CANCELLED),
            AssignmentStatus.EN_ROUTE, List.of(AssignmentStatus.ARRIVED_AT_PICKUP, AssignmentStatus.ARRIVED_AT_DROPOFF, AssignmentStatus.FAILED, AssignmentStatus.CANCELLED),
            AssignmentStatus.ARRIVED_AT_PICKUP, List.of(AssignmentStatus.PICKED_UP, AssignmentStatus.FAILED, AssignmentStatus.CANCELLED),
            AssignmentStatus.PICKED_UP, List.of(AssignmentStatus.EN_ROUTE, AssignmentStatus.ARRIVED_AT_DROPOFF, AssignmentStatus.FAILED, AssignmentStatus.CANCELLED), // EN_ROUTE again for delivery leg
            AssignmentStatus.ARRIVED_AT_DROPOFF, List.of(AssignmentStatus.COMPLETED, AssignmentStatus.FAILED, AssignmentStatus.CANCELLED)
            // COMPLETED, FAILED, CANCELLED are terminal
        );

        List<AssignmentStatus> allowedNextStates = validTransitions.getOrDefault(oldStatus, Collections.emptyList());
        if (!allowedNextStates.contains(newStatus)) {
            throw new IllegalStateException("Invalid status transition from " + oldStatus + " to " + newStatus + " for assignment " + assignment.getId());
        }

        // Specific prerequisite checks for certain target statuses
        if (newStatus == AssignmentStatus.COMPLETED) {
            // These checks are also in updateAssignmentStatus, but good to have in validator too.
            if (!deliveryEventService.hasEventOccurred(assignment, "PHOTO_TAKEN")) { // Assuming this event type string
                throw new IllegalStateException("Cannot complete assignment ID " + assignment.getId() + ": PHOTO_TAKEN event is required.");
            }
            // OTP_VERIFIED_SUCCESS will be checked in updateAssignmentStatus directly
            if (!deliveryEventService.hasEventOccurred(assignment, "OTP_VERIFIED_SUCCESS")) {
                throw new IllegalStateException("Cannot complete assignment ID " + assignment.getId() + ": Successful OTP verification (OTP_VERIFIED_SUCCESS event) is required.");
            }
        }

        if (newStatus == AssignmentStatus.PICKED_UP && assignment.getType() == AssignmentType.DELIVERY) {
             throw new IllegalStateException("Cannot transition to PICKED_UP for a DELIVERY type assignment directly. Should go through EN_ROUTE to ARRIVED_AT_DROPOFF.");
        }
        if (newStatus == AssignmentStatus.ARRIVED_AT_PICKUP && assignment.getType() == AssignmentType.DELIVERY) {
            // This might be valid if it's a multi-leg delivery with an intermediate pickup.
            // For simple delivery, EN_ROUTE directly to ARRIVED_AT_DROPOFF is more common.
            // Current state machine allows EN_ROUTE -> ARRIVED_AT_DROPOFF
        }
    }

    // This method should be the primary way to update status, ensuring validation and events.
    // The one in the scaffold was a bit too simple.
    // The /arrive endpoint in controller currently calls this with a placeholder status.
    // It should pass the specific status (e.g., ARRIVED_AT_PICKUP or ARRIVED_AT_DROPOFF).
    // Let's assume for now that the controller calls this method with the *correct target status*.
    // The validateStatusTransition will then check if it's a valid move from current.

    // Placeholder for creating an assignment - this might be triggered by an event from Order Service
    @Transactional
    public OrderAssignment createAssignment(UUID orderId, UUID deliveryProfileId, AssignmentType type) {
        DeliveryProfile profile = deliveryProfileRepository.findById(deliveryProfileId)
            .orElseThrow(() -> new EntityNotFoundException("DeliveryProfile not found: " + deliveryProfileId));

        if (!profile.isActive()) {
            throw new IllegalStateException("Cannot assign order to inactive DeliveryProfile: " + deliveryProfileId);
        }

        // Check if order already assigned
        if(orderAssignmentRepository.findByOrderId(orderId).isPresent()){
            throw new IllegalStateException("Order " + orderId + " is already assigned.");
        }

        OrderAssignment assignment = new OrderAssignment();
        assignment.setOrderId(orderId);
        assignment.setDeliveryProfile(profile);
        assignment.setType(type);
        assignment.setStatus(AssignmentStatus.ASSIGNED); // Initial status

        OrderAssignment savedAssignment = orderAssignmentRepository.save(assignment);
        logger.info("OrderAssignment created with ID {} for Order ID {} and DeliveryProfile ID {}",
            savedAssignment.getId(), orderId, deliveryProfileId);

        deliveryKafkaClient.publishOrderAssigned(savedAssignment);
        return savedAssignment;
    }
}
