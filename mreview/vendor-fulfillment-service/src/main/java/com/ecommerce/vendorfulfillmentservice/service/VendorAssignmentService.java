package com.ecommerce.vendorfulfillmentservice.service;

import com.ecommerce.vendorfulfillmentservice.entity.*;
import com.ecommerce.vendorfulfillmentservice.event.OrderReservationSucceededEvent;
import com.ecommerce.vendorfulfillmentservice.event.avro.*; // Import all Avro events
import com.ecommerce.vendorfulfillmentservice.exception.AssignmentNotFoundException;
import com.ecommerce.vendorfulfillmentservice.exception.InvalidStatusTransitionException;
import com.ecommerce.vendorfulfillmentservice.repository.OutboxEventRepository;
import com.ecommerce.vendorfulfillmentservice.repository.ProcessedInboundEventRepository;
import com.ecommerce.vendorfulfillmentservice.repository.VendorOrderAssignmentRepository;
import com.ecommerce.vendorfulfillmentservice.repository.VendorOrderStatusHistoryRepository;
import org.apache.avro.specific.SpecificRecordBase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry; // Example, might not use for Kafka listener directly
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.extension.annotations.WithSpan;
import io.opentelemetry.extension.annotations.SpanAttribute; // To link method params to attributes

import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class VendorAssignmentService {

    private final VendorOrderAssignmentRepository assignmentRepository;
    private final VendorOrderStatusHistoryRepository statusHistoryRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ProcessedInboundEventRepository processedInboundEventRepository;
    private final AvroSerializer avroSerializer;
    private final MetricsService metricsService; // Added MetricsService

    private static final String CONSUMER_GROUP = "vendor-fulfillment-consumer";
    private static final String KAFKA_ORDER_EVENT_PROCESSOR_CB = "kafkaOrderEventProcessor";

    @Transactional
    @CircuitBreaker(name = KAFKA_ORDER_EVENT_PROCESSOR_CB, fallbackMethod = "processOrderReservationFallback")
    @WithSpan("vendorAssignment.processOrderReservation")
    public void processOrderReservation(@SpanAttribute("event.id") OrderReservationSucceededEvent event) {
        log.info("Processing order reservation event: {}", event.getEventId());
        Span.current().setAttribute("order.id", event.getOrderId().toString());

        // 1. Idempotency Check
        if (processedInboundEventRepository.existsByEventIdAndConsumerGroup(event.getEventId(), CONSUMER_GROUP)) {
            log.warn("Event {} already processed by consumer group {}. Skipping.", event.getEventId(), CONSUMER_GROUP);
            return;
        }

        // Prevent duplicate assignments for the same order if business logic dictates one assignment per order
        if (assignmentRepository.findByOrderId(event.getOrderId()).isPresent()) {
            log.warn("Order {} has already been assigned. Skipping event {}.", event.getOrderId(), event.getEventId());
            // Mark as processed to avoid retries for this specific scenario if it's a valid terminal state for the event.
            // Or handle as an error/alert if this indicates a deeper issue.
            ProcessedInboundEvent processedEvent = ProcessedInboundEvent.builder()
                .eventId(event.getEventId())
                .consumerGroup(CONSUMER_GROUP)
                .processedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
            processedInboundEventRepository.save(processedEvent);
            return;
        }

        // 2. Determine Vendor ID (Placeholder Logic)
        UUID vendorId = determineVendorId(event);
        if (vendorId == null) {
            log.error("Could not determine vendor for order {}. Event {}. Assignment cannot be created.", event.getOrderId(), event.getEventId());
            // Potentially send to a dead-letter queue or raise an alert
            // For now, we'll skip and mark as processed to avoid retry loops on unassignable orders.
            // A more robust solution might involve a retry mechanism with backoff if vendor assignment is temporarily unavailable.
             ProcessedInboundEvent markProcessedOnError = ProcessedInboundEvent.builder()
                .eventId(event.getEventId())
                .consumerGroup(CONSUMER_GROUP)
                .processedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
            processedInboundEventRepository.save(markProcessedOnError);
            return;
        }

        // 3. Create VendorOrderAssignment
        VendorOrderAssignment assignment = VendorOrderAssignment.builder()
                .orderId(event.getOrderId())
                .vendorId(vendorId)
                .status(AssignmentStatus.ASSIGNED)
                // statusHistory will be handled by addStatusHistoryAndSave
                .build();
        // Manual save first to get ID if not relying on cascade for initial history
        VendorOrderAssignment savedAssignment = assignmentRepository.save(assignment);
        addStatusHistoryAndSave(savedAssignment, AssignmentStatus.ASSIGNED, null); // No specific user for system event
        metricsService.incrementStatusChangeCounter(AssignmentStatus.ASSIGNED); // Increment counter
        log.info("Created VendorOrderAssignment {} for order {}", savedAssignment.getId(), event.getOrderId());


        // 4. Create and Save Outbox Event for VendorOrderAssignedEvent
        VendorOrderAssignedEvent assignedAvroEvent = VendorOrderAssignedEvent.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setAssignmentId(savedAssignment.getId().toString())
                .setOrderId(savedAssignment.getOrderId().toString())
                .setVendorId(savedAssignment.getVendorId().toString())
                .setTimestamp(Instant.now().toEpochMilli())
                .setStatus(savedAssignment.getStatus().name())
                .build();
        createAndSaveOutboxEvent(savedAssignment, assignedAvroEvent, VendorOrderAssignedEvent.class.getSimpleName());

        // 5. Mark event as processed for idempotency
        ProcessedInboundEvent processedEvent = ProcessedInboundEvent.builder()
                .eventId(event.getEventId())
                .consumerGroup(CONSUMER_GROUP)
                .processedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
        processedInboundEventRepository.save(processedEvent);
        log.info("Marked event {} as processed for consumer group {}.", event.getEventId(), CONSUMER_GROUP);
    }

    @Transactional
    @WithSpan("vendorAssignment.acknowledgeOrder")
    public VendorOrderAssignment acknowledgeOrder(@SpanAttribute("assignment.id") UUID assignmentId) {
        VendorOrderAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new AssignmentNotFoundException(assignmentId));
        Span.current().setAttribute("order.id", assignment.getOrderId().toString());
        Span.current().setAttribute("vendor.id", assignment.getVendorId().toString());

        if (assignment.getStatus() != AssignmentStatus.ASSIGNED) {
            throw new InvalidStatusTransitionException(assignmentId, assignment.getStatus(), AssignmentStatus.ACKNOWLEDGED);
        }

        assignment.setStatus(AssignmentStatus.ACKNOWLEDGED);
        addStatusHistoryAndSave(assignment, AssignmentStatus.ACKNOWLEDGED, null); // TODO: Add user later
        VendorOrderAssignment savedAssignment = assignmentRepository.save(assignment);
        metricsService.incrementStatusChangeCounter(AssignmentStatus.ACKNOWLEDGED); // Increment counter

        VendorOrderAcknowledgedEvent event = VendorOrderAcknowledgedEvent.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setAssignmentId(savedAssignment.getId().toString())
                .setOrderId(savedAssignment.getOrderId().toString())
                .setVendorId(savedAssignment.getVendorId().toString())
                .setTimestamp(Instant.now().toEpochMilli())
                .setStatus(savedAssignment.getStatus().name())
                .build();
        createAndSaveOutboxEvent(savedAssignment, event, VendorOrderAcknowledgedEvent.class.getSimpleName());

        log.info("Order assignment {} acknowledged by vendor {}", assignmentId, savedAssignment.getVendorId());
        return savedAssignment;
    }

    @Transactional
    @WithSpan("vendorAssignment.packOrder")
    public VendorOrderAssignment packOrder(@SpanAttribute("assignment.id") UUID assignmentId) {
        VendorOrderAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new AssignmentNotFoundException(assignmentId));
        Span.current().setAttribute("order.id", assignment.getOrderId().toString());
        Span.current().setAttribute("vendor.id", assignment.getVendorId().toString());

        // Typically, an order should be acknowledged before being packed
        if (assignment.getStatus() != AssignmentStatus.ACKNOWLEDGED) {
            throw new InvalidStatusTransitionException(assignmentId, assignment.getStatus(), AssignmentStatus.PACKED);
        }

        assignment.setStatus(AssignmentStatus.PACKED);
        addStatusHistoryAndSave(assignment, AssignmentStatus.PACKED, null); // TODO: Add user later
        VendorOrderAssignment savedAssignment = assignmentRepository.save(assignment);
        metricsService.incrementStatusChangeCounter(AssignmentStatus.PACKED); // Increment counter

        VendorOrderPackedEvent event = VendorOrderPackedEvent.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setAssignmentId(savedAssignment.getId().toString())
                .setOrderId(savedAssignment.getOrderId().toString())
                .setVendorId(savedAssignment.getVendorId().toString())
                .setTimestamp(Instant.now().toEpochMilli())
                .setStatus(savedAssignment.getStatus().name())
                .build();
        createAndSaveOutboxEvent(savedAssignment, event, VendorOrderPackedEvent.class.getSimpleName());

        log.info("Order assignment {} marked as packed by vendor {}", assignmentId, savedAssignment.getVendorId());
        return savedAssignment;
    }


    private void addStatusHistoryAndSave(VendorOrderAssignment assignment, AssignmentStatus status, UUID userId) {
        VendorOrderStatusHistory historyEntry = VendorOrderStatusHistory.builder()
                .assignment(assignment)
                .status(status)
                .occurredAt(OffsetDateTime.now(ZoneOffset.UTC))
                // .changedByUserId(userId) // TODO: Populate when user context is available
                .build();
        statusHistoryRepository.save(historyEntry); // Save history
        // assignment.getStatusHistory().add(historyEntry); // If using cascade save from assignment
        // assignmentRepository.save(assignment); // Then save assignment
        log.debug("Status history added for assignment {}, new status {}", assignment.getId(), status);
    }

    private <T extends SpecificRecordBase> void createAndSaveOutboxEvent(
            VendorOrderAssignment assignment, T avroEventPayload, String eventType) {
        try {
            byte[] payload = avroSerializer.serialize(avroEventPayload);
            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .aggregateType(VendorOrderAssignment.class.getSimpleName())
                    .aggregateId(assignment.getId())
                    .eventType(eventType)
                    .payload(payload)
                    .build();
            outboxEventRepository.save(outboxEvent);
            log.info("Saved OutboxEvent (type: {}) for assignment {}", eventType, assignment.getId());
        } catch (IOException e) {
            log.error("Failed to serialize Avro event {} for assignment {}: {}", eventType, assignment.getId(), e.getMessage(), e);
            // This will cause the transaction to roll back due to RuntimeException.
            throw new RuntimeException("Failed to serialize Avro event " + eventType, e);
        }
    }

    private UUID determineVendorId(OrderReservationSucceededEvent event) {
        // Placeholder for vendor determination logic.
        // This could involve looking up vendor profiles, geographic routing, product category, etc.
        // For now, returning a dummy UUID.
        // In a real scenario, this might involve a call to another service or complex DB queries.
        log.warn("Using placeholder logic for vendor ID determination for order {}", event.getOrderId());
        // Example: if event carried a pre-assigned vendorId:
        // if (event.getVendorId() != null) return event.getVendorId();
        return UUID.randomUUID(); // Replace with actual logic
    }

    // --- Query Methods ---

    @Transactional(readOnly = true)
    @WithSpan("vendorAssignment.findAssignmentByIdWithHistory")
    public VendorOrderAssignmentDto findAssignmentByIdWithHistory(@SpanAttribute("assignment.id") UUID assignmentId) {
        VendorOrderAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new AssignmentNotFoundException(assignmentId));
        // Ensure history is loaded if LAZY. Accessing it here will trigger loading within the transaction.
        // Alternatively, use a JOIN FETCH query in the repository.
        assignment.getStatusHistory().size(); // Trigger loading
        return mapToDto(assignment);
    }

    @Transactional(readOnly = true)
    @WithSpan("vendorAssignment.findAllAssignments")
    public org.springframework.data.domain.Page<VendorOrderAssignmentDto> findAllAssignments(
            @SpanAttribute("filter.vendorId") UUID vendorId,
            @SpanAttribute("filter.status") AssignmentStatus status,
            org.springframework.data.domain.Pageable pageable) {

        // Using lambda for Specification for conciseness
        org.springframework.data.jpa.domain.Specification<VendorOrderAssignment> spec = (root, query, cb) -> {
            java.util.List<jakarta.persistence.criteria.Predicate> predicates = new java.util.ArrayList<>();
            if (vendorId != null) {
                predicates.add(cb.equal(root.get("vendorId"), vendorId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            // Default sort by createdAt descending if not specified in pageable
            if (pageable.getSort().isUnsorted()) {
                query.orderBy(cb.desc(root.get("createdAt")));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        org.springframework.data.domain.Page<VendorOrderAssignment> page = assignmentRepository.findAll(spec, pageable);
        return page.map(this::mapToDto); // Map page content to DTOs
    }


    // --- Helper Mappers ---
    private VendorOrderAssignmentDto mapToDto(VendorOrderAssignment assignment) {
        if (assignment == null) return null;
        return VendorOrderAssignmentDto.builder()
                .id(assignment.getId())
                .orderId(assignment.getOrderId())
                .vendorId(assignment.getVendorId())
                .status(assignment.getStatus())
                .createdAt(assignment.getCreatedAt())
                .updatedAt(assignment.getUpdatedAt())
                .trackingNo(assignment.getTrackingNo())
                .statusHistory(assignment.getStatusHistory() == null ? java.util.Collections.emptyList() :
                        assignment.getStatusHistory().stream()
                                .map(this::mapToDto)
                                .sorted(java.util.Comparator.comparing(VendorOrderStatusHistoryDto::getOccurredAt).reversed())
                                .collect(java.util.stream.Collectors.toList()))
                .build();
    }

    private VendorOrderStatusHistoryDto mapToDto(VendorOrderStatusHistory history) {
        if (history == null) return null;
        return VendorOrderStatusHistoryDto.builder()
                .id(history.getId())
                .status(history.getStatus())
                .occurredAt(history.getOccurredAt())
                .build();
    }


    // --- Command Methods (existing) ---
    @Transactional
    @WithSpan("vendorAssignment.shipAssignment")
    public VendorOrderAssignment shipAssignment(@SpanAttribute("assignment.id") UUID assignmentId,
                                              @SpanAttribute("tracking.no") String trackingNo) {
        VendorOrderAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new AssignmentNotFoundException(assignmentId));
        Span.current().setAttribute("order.id", assignment.getOrderId().toString());
        Span.current().setAttribute("vendor.id", assignment.getVendorId().toString());

        // Typically, an order should be packed before being shipped
        if (assignment.getStatus() != AssignmentStatus.PACKED) {
            throw new InvalidStatusTransitionException(assignmentId, assignment.getStatus(), AssignmentStatus.SHIPPED);
        }

        assignment.setStatus(AssignmentStatus.SHIPPED);
        assignment.setTrackingNo(trackingNo); // Set tracking number
        addStatusHistoryAndSave(assignment, AssignmentStatus.SHIPPED, null); // TODO: Add user later
        VendorOrderAssignment savedAssignment = assignmentRepository.save(assignment);
        metricsService.incrementStatusChangeCounter(AssignmentStatus.SHIPPED); // Increment counter

        VendorOrderShippedEvent event = VendorOrderShippedEvent.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setAssignmentId(savedAssignment.getId().toString())
                .setOrderId(savedAssignment.getOrderId().toString())
                .setVendorId(savedAssignment.getVendorId().toString())
                .setTimestamp(Instant.now().toEpochMilli())
                .setStatus(savedAssignment.getStatus().name())
                .setTrackingNo(savedAssignment.getTrackingNo()) // Include tracking number in event
                .build();
        createAndSaveOutboxEvent(savedAssignment, event, VendorOrderShippedEvent.class.getSimpleName());

        // Also create and save a ShipmentNotificationRequestedEvent
        // Assuming customerId needs to be fetched or is not directly on assignment.
        // For now, passing null for customerId. A real implementation might get it from the order details
        // or the original OrderReservationSucceededEvent if that data was persisted on the assignment.
        UUID customerIdForNotification = null; // Placeholder

        ShipmentNotificationRequestedEvent notificationEvent = ShipmentNotificationRequestedEvent.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setAssignmentId(savedAssignment.getId().toString())
                .setOrderId(savedAssignment.getOrderId().toString())
                .setVendorId(savedAssignment.getVendorId().toString())
                .setTrackingNo(savedAssignment.getTrackingNo())
                .setCustomerId(customerIdForNotification == null ? null : customerIdForNotification.toString())
                .setNotificationType("CUSTOMER_SHIPMENT_CONFIRMATION") // Example type
                .setTimestamp(Instant.now().toEpochMilli())
                .build();
        createAndSaveOutboxEvent(savedAssignment, notificationEvent, ShipmentNotificationRequestedEvent.class.getSimpleName());
        metricsService.incrementShipmentNotificationRequestedCounter();

        log.info("Order assignment {} marked as SHIPPED by vendor {} with tracking_no {}. Shipment notification requested.",
                assignmentId, savedAssignment.getVendorId(), trackingNo);
        return savedAssignment;
    }

    @Transactional
    @WithSpan("vendorAssignment.completeFulfillment")
    public VendorOrderAssignment completeFulfillment(@SpanAttribute("assignment.id") UUID assignmentId) {
        VendorOrderAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new AssignmentNotFoundException(assignmentId));
        Span.current().setAttribute("order.id", assignment.getOrderId().toString());
        Span.current().setAttribute("vendor.id", assignment.getVendorId().toString());

        // An order should be shipped before it can be marked as fulfilled (or completed)
        // Depending on exact flow, could also allow transition from PACKED if it's a pickup scenario without shipping.
        // For now, strict SHIPPED -> FULFILLED.
        if (assignment.getStatus() != AssignmentStatus.SHIPPED) {
            throw new InvalidStatusTransitionException(assignmentId, assignment.getStatus(), AssignmentStatus.FULFILLED);
        }

        assignment.setStatus(AssignmentStatus.FULFILLED);
        addStatusHistoryAndSave(assignment, AssignmentStatus.FULFILLED, null); // TODO: Add user later
        VendorOrderAssignment savedAssignment = assignmentRepository.save(assignment);
        metricsService.incrementStatusChangeCounter(AssignmentStatus.FULFILLED); // Increment counter

        VendorOrderFulfilledEvent event = VendorOrderFulfilledEvent.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setAssignmentId(savedAssignment.getId().toString())
                .setOrderId(savedAssignment.getOrderId().toString())
                .setVendorId(savedAssignment.getVendorId().toString())
                .setTimestamp(Instant.now().toEpochMilli())
                .setStatus(savedAssignment.getStatus().name())
                .setTrackingNo(savedAssignment.getTrackingNo()) // Carry over trackingNo if present
                .build();
        createAndSaveOutboxEvent(savedAssignment, event, VendorOrderFulfilledEvent.class.getSimpleName());

        log.info("Order assignment {} marked as FULFILLED by vendor {}", assignmentId, savedAssignment.getVendorId());
        return savedAssignment;
    }

    @Transactional
    @WithSpan("vendorAssignment.reassignOrder")
    public VendorOrderAssignment reassignOrder(@SpanAttribute("assignment.id") UUID assignmentId,
                                             @SpanAttribute("new.vendor.id") UUID newVendorId,
                                             @SpanAttribute("admin.user.id") UUID adminUserId) { // adminUserId for audit
        VendorOrderAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new AssignmentNotFoundException(assignmentId));
        Span.current().setAttribute("order.id", assignment.getOrderId().toString());
        Span.current().setAttribute("old.vendor.id", assignment.getVendorId().toString());

        UUID oldVendorId = assignment.getVendorId();

        if (oldVendorId.equals(newVendorId)) {
            throw new InvalidStatusTransitionException("Cannot reassign order to the same vendor. Assignment ID: " + assignmentId);
        }

        // Define statuses where reassignment is allowed.
        // For example, not allowed if already SHIPPED or FULFILLED.
        List<AssignmentStatus> allowedStatusesForReassignment = List.of(
                AssignmentStatus.ASSIGNED,
                AssignmentStatus.ACKNOWLEDGED,
                AssignmentStatus.PACKED // Admin might reassign even if packed by previous vendor. New vendor starts fresh.
        );

        if (!allowedStatusesForReassignment.contains(assignment.getStatus())) {
            throw new InvalidStatusTransitionException(
                    String.format("Cannot reassign order %s. Current status %s does not allow reassignment.",
                            assignmentId, assignment.getStatus())
            );
        }

        // Log the reassignment action itself as a special history entry.
        // This is more of an audit log than a direct status. The actual status will be reset for the new vendor.
        // For simplicity, we'll use the addStatusHistoryAndSave, but the 'status' field in history
        // might need a more descriptive value or a different kind of history record for reassignments.
        // For now, we'll log the new "ASSIGNED" status for the new vendor after updating.

        log.info("Reassigning order assignment {} from vendor {} to vendor {}. Admin: {}",
                assignmentId, oldVendorId, newVendorId, adminUserId);

        assignment.setVendorId(newVendorId);
        assignment.setStatus(AssignmentStatus.ASSIGNED); // Reset status for the new vendor
        assignment.setTrackingNo(null); // Clear tracking number from previous vendor, if any

        // Add history for the new "ASSIGNED" state with the new vendor
        addStatusHistoryAndSave(assignment, AssignmentStatus.ASSIGNED, adminUserId);
        // Note: ASSIGNED counter is incremented here again for the new assignment state.
        // This is correct as it represents a new assignment from the new vendor's perspective.
        metricsService.incrementStatusChangeCounter(AssignmentStatus.ASSIGNED);
        VendorOrderAssignment savedAssignment = assignmentRepository.save(assignment);
        metricsService.incrementReassignmentCounter(); // Increment reassignment action counter

        VendorOrderReassignedEvent event = VendorOrderReassignedEvent.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setAssignmentId(savedAssignment.getId().toString())
                .setOrderId(savedAssignment.getOrderId().toString())
                .setOldVendorId(oldVendorId.toString())
                .setNewVendorId(savedAssignment.getVendorId().toString())
                .setTimestamp(Instant.now().toEpochMilli())
                .setReassignedBy(adminUserId != null ? adminUserId.toString() : null)
                .build();
        createAndSaveOutboxEvent(savedAssignment, event, VendorOrderReassignedEvent.class.getSimpleName());

        log.info("Order assignment {} successfully reassigned to vendor {}. Status reset to ASSIGNED.",
                assignmentId, newVendorId);
        return savedAssignment;
    }

    // Fallback method for processOrderReservation CircuitBreaker
    @SuppressWarnings("unused") // Called by Resilience4j
    private void processOrderReservationFallback(OrderReservationSucceededEvent event, Throwable t) {
        log.error("Circuit breaker open for processOrderReservation. Event ID: {}. Error: {}", event.getEventId(), t.getMessage());
        // When the circuit breaker is open, we cannot process the message.
        // Since Kafka listener uses manual acknowledgment, this message will NOT be acknowledged
        // if the original method (processOrderReservation) throws an exception before acknowledgment.
        // The fallback's purpose here is mainly to log that the circuit is open.
        // The message will be redelivered by Kafka later, once the circuit breaker closes
        // and the application attempts to process messages again.
        // Throwing the original exception (or a new one) from fallback can influence retry policies
        // if a @Retry annotation were also in play or if the global Kafka error handler relies on it.
        // For a simple circuit breaker on a Kafka listener with manual ack, just logging is often sufficient.
        // The main goal is to stop hitting a failing downstream service (like the DB).
        // IMPORTANT: The OrderEventConsumer must NOT acknowledge the message if this fallback is hit
        // due to an exception from the main method.
        throw new RuntimeException("Circuit breaker open for Kafka event processing. Original error: " + t.getMessage(), t);
    }
}
