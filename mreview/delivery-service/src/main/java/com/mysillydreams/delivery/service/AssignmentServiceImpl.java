package com.mysillydreams.delivery.service;

import com.mysillydreams.delivery.domain.DeliveryAssignment;
import com.mysillydreams.delivery.domain.DeliveryEvent;
import com.mysillydreams.delivery.domain.DeliveryProfile;
import com.mysillydreams.delivery.domain.enums.DeliveryAssignmentStatus;
import com.mysillydreams.delivery.domain.enums.DeliveryEventType;
// Import DTOs for REST API
import com.mysillydreams.delivery.dto.PhotoOtpDto;
// Import Avro DTOs for Kafka events
import com.mysillydreams.delivery.dto.avro.DeliveryAssignmentCreatedEvent;
import com.mysillydreams.delivery.dto.avro.DeliveryDeliveredEvent;
import com.mysillydreams.delivery.dto.avro.DeliveryPickedUpEvent;
// GpsUpdateEvent is already imported
// Import Avro ShipmentRequestedEvent for createAssignment method input
import com.mysillydreams.delivery.dto.avro.ShipmentRequestedEvent;


import com.mysillydreams.delivery.repository.DeliveryAssignmentRepository;
import com.mysillydreams.delivery.repository.DeliveryEventRepository;
import com.mysillydreams.delivery.repository.DeliveryProfileRepository;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityNotFoundException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AssignmentServiceImpl implements AssignmentService {

    private static final Logger log = LoggerFactory.getLogger(AssignmentServiceImpl.class);

    private final DeliveryAssignmentRepository assignmentRepository;
    private final DeliveryEventRepository eventRepository;
    private final DeliveryProfileRepository profileRepository; // For courier selection
    private final ObjectMapper objectMapper; // Added back for AddressDto conversion

    private final OutboxEventService outboxEventService; // Use the real OutboxEventService

    private final KafkaTemplate<String, Object> kafkaTemplate; // Generic template from KafkaProducerConfig

    // Kafka Topic Names from application.yml
    @Value("${kafka.topics.deliveryAssignmentCreated:delivery.assignment.created}")
    private String assignmentCreatedTopic; // Not used if outbox eventType is topic

    @Value("${kafka.topics.deliveryPickedUp:delivery.picked_up}")
    private String pickedUpTopic;  // Not used if outbox eventType is topic

    @Value("${kafka.topics.deliveryGpsUpdates:delivery.gps.updates}")
    private String gpsUpdatesTopic;

    @Value("${kafka.topics.deliveryDelivered:delivery.delivered}")
    private String deliveredTopic; // Not used if outbox eventType is topic


    @Override
    @Transactional
    public UUID createAssignment(ShipmentRequestedEvent event) { // Changed to Avro type
        log.info("Creating assignment for orderId: {}", event.getOrderId());

        // 1. Select a courier (simplified: find first available active courier)
        List<DeliveryProfile> availableCouriers = profileRepository.findByStatus("ACTIVE");
        if (availableCouriers.isEmpty()) {
            log.warn("No available couriers to create assignment for orderId: {}", event.getOrderId());
            throw new IllegalStateException("No available couriers found.");
        }
        DeliveryProfile assignedCourier = availableCouriers.get(0);
        log.info("Assigned courier {} to orderId {}", assignedCourier.getId(), event.getOrderId());

        DeliveryAssignment assignment = new DeliveryAssignment();
        assignment.setId(UUID.randomUUID());
        assignment.setOrderId(UUID.fromString(event.getOrderId())); // Avro String to UUID
        assignment.setCourier(assignedCourier);
        assignment.setVendorId(UUID.fromString(event.getVendorId())); // Avro String to UUID
        assignment.setCustomerId(UUID.fromString(event.getCustomerId())); // Avro String to UUID

        // Convert Avro AddressAvro to Map<String, Object> for JSONB storage
        // Avro specific records can be serialized to map by ObjectMapper
        assignment.setPickupAddress(objectMapper.convertValue(event.getPickupAddress(), Map.class));
        assignment.setDropoffAddress(objectMapper.convertValue(event.getDropoffAddress(), Map.class));

        assignment.setStatus(DeliveryAssignmentStatus.ASSIGNED);
        assignment.setEstimatedPickupTime(Instant.now().plusSeconds(3600)); // e.g., 1 hour from now
        assignment.setEstimatedDeliveryTime(Instant.now().plusSeconds(3600 * 2)); // e.g., 2 hours from now

        DeliveryAssignment savedAssignment = assignmentRepository.save(assignment);
        recordEvent(savedAssignment, DeliveryEventType.ASSIGNMENT_CREATED,
                    Map.of("courierId", assignedCourier.getId().toString()), assignedCourier.getId(), "SYSTEM");

        // Publish Avro event via Outbox
        DeliveryAssignmentCreatedEvent avroEvent = DeliveryAssignmentCreatedEvent.newBuilder()
            .setAssignmentId(savedAssignment.getId().toString())
            .setOrderId(savedAssignment.getOrderId().toString())
            .setCourierId(assignedCourier.getId().toString())
            .setVendorId(savedAssignment.getVendorId().toString())
            .setCustomerId(savedAssignment.getCustomerId().toString())
            .setEstimatedPickupTime(savedAssignment.getEstimatedPickupTime() != null ? savedAssignment.getEstimatedPickupTime().toEpochMilli() : null)
            .setEstimatedDeliveryTime(savedAssignment.getEstimatedDeliveryTime() != null ? savedAssignment.getEstimatedDeliveryTime().toEpochMilli() : null)
            .build();

        outboxEventService.createAndSaveOutboxEvent("DeliveryAssignment", savedAssignment.getId().toString(),
                                   "delivery.assignment.created", avroEvent); // Topic name as eventType

        log.info("Delivery assignment {} created for orderId {}", savedAssignment.getId(), dto.getOrderId());
        return savedAssignment.getId();
    }

    @Override
    @Transactional
    public void markArrivedAtPickup(UUID assignmentId) {
        log.info("Courier arrived at pickup for assignmentId: {}", assignmentId);
        DeliveryAssignment assignment = findAssignmentOrFail(assignmentId);
        // TODO: Check current status if valid for this transition
        assignment.setStatus(DeliveryAssignmentStatus.ARRIVED_AT_PICKUP);
        assignmentRepository.save(assignment);
        recordEvent(assignment, DeliveryEventType.ARRIVED_AT_PICKUP, null, assignment.getCourier().getId(), "COURIER");
    }

    @Override
    @Transactional
    public void markPickedUp(UUID assignmentId, PhotoOtpDto dto) {
        log.info("Marking assignmentId: {} as picked up. OTP: {}, Photo: {}", assignmentId, dto.getOtp() != null ? "provided" : "N/A", dto.getPhotoUrl());
        DeliveryAssignment assignment = findAssignmentOrFail(assignmentId);
        // TODO: Validate OTP if provided and required for pickup.
        // TODO: Store photoUrl (dto.getPhotoUrl()) with appropriate storage mechanism.
        assignment.setPickupPhotoUrl(dto.getPhotoUrl()); // Store reference
        assignment.setPickupOtpVerified(dto.getOtp() != null); // Simplified: true if OTP provided
        assignment.setStatus(DeliveryAssignmentStatus.PICKED_UP);
        assignment.setActualPickupTime(Instant.now());
        assignmentRepository.save(assignment);

        Map<String, Object> eventPayload = Map.of(
            "photoUrl", dto.getPhotoUrl(),
            "otpProvided", dto.getOtp() != null,
            "notes", dto.getNotes() != null ? dto.getNotes() : ""
        );
        recordEvent(assignment, DeliveryEventType.PICKED_UP, eventPayload, assignment.getCourier().getId(), "COURIER");

        // Publish Avro event via Outbox
        DeliveryPickedUpEvent avroEvent = DeliveryPickedUpEvent.newBuilder()
            .setAssignmentId(assignment.getId().toString())
            .setOrderId(assignment.getOrderId().toString())
            .setActualPickupTime(assignment.getActualPickupTime().toEpochMilli())
            .setPickupPhotoUrl(assignment.getPickupPhotoUrl())
            .setNotes(dto.getNotes()) // Assuming PhotoOtpDto.notes is relevant here
            .build();
        outboxEventService.createAndSaveOutboxEvent("DeliveryAssignment", assignment.getId().toString(),
                                   "delivery.picked_up", avroEvent);
    }

    @Override
    public void publishGpsUpdate(UUID assignmentId, GpsUpdateEvent avroEvent) { // Parameter changed to Avro type
        // Direct publish to Kafka for high-frequency GPS updates
        log.debug("Publishing GPS update for assignmentId: {} to topic {}", assignmentId, gpsUpdatesTopic);

        // The avroEvent is already the correct type.
        // Ensure its assignmentId field matches the path variable if necessary, or trust the caller.
        // For safety, one might do: avroEvent.setAssignmentId(assignmentId.toString()); if mutable
        // But Avro generated objects are typically immutable after build(). Caller should construct correctly.

        kafkaTemplate.send(gpsUpdatesTopic, assignmentId.toString(), avroEvent); // Send Avro event
    }

    @Override
    @Transactional
    public void markArrivedAtDropoff(UUID assignmentId) {
        log.info("Courier arrived at dropoff for assignmentId: {}", assignmentId);
        DeliveryAssignment assignment = findAssignmentOrFail(assignmentId);
        assignment.setStatus(DeliveryAssignmentStatus.ARRIVED_AT_DROPOFF);
        assignmentRepository.save(assignment);
        recordEvent(assignment, DeliveryEventType.ARRIVED_AT_DROPOFF, null, assignment.getCourier().getId(), "COURIER");
    }

    @Override
    @Transactional
    public void markDelivered(UUID assignmentId, PhotoOtpDto dto) {
        log.info("Marking assignmentId: {} as delivered. OTP: {}, Photo: {}", assignmentId, dto.getOtp() != null ? "provided" : "N/A", dto.getPhotoUrl());
        DeliveryAssignment assignment = findAssignmentOrFail(assignmentId);
        // TODO: Validate OTP, store photo reference
        assignment.setDeliveryPhotoUrl(dto.getPhotoUrl());
        assignment.setDeliveryOtpVerified(dto.getOtp() != null);
        assignment.setStatus(DeliveryAssignmentStatus.DELIVERED);
        assignment.setActualDeliveryTime(Instant.now());
        assignmentRepository.save(assignment);

        Map<String, Object> eventPayload = Map.of(
            "photoUrl", dto.getPhotoUrl(),
            "otpProvided", dto.getOtp() != null,
            "notes", dto.getNotes() != null ? dto.getNotes() : ""
        );
        recordEvent(assignment, DeliveryEventType.DELIVERED, eventPayload, assignment.getCourier().getId(), "COURIER");

        // Publish Avro event via Outbox
        DeliveryDeliveredEvent avroEvent = DeliveryDeliveredEvent.newBuilder()
            .setAssignmentId(assignment.getId().toString())
            .setOrderId(assignment.getOrderId().toString())
            .setActualDeliveryTime(assignment.getActualDeliveryTime().toEpochMilli())
            .setDeliveryPhotoUrl(assignment.getDeliveryPhotoUrl())
            .setNotes(dto.getNotes()) // Assuming PhotoOtpDto.notes is relevant
            // .setRecipientName() // Add if captured and part of Avro schema
            .build();
        outboxEventService.createAndSaveOutboxEvent("DeliveryAssignment", assignment.getId().toString(),
                                   "delivery.delivered", avroEvent);
    }

    @Override
    @Transactional
    public void markDeliveryFailed(UUID assignmentId, String reason, String notes) {
        log.warn("Marking assignmentId: {} as FAILED_DELIVERY. Reason: {}, Notes: {}", assignmentId, reason, notes);
        DeliveryAssignment assignment = findAssignmentOrFail(assignmentId);
        assignment.setStatus(DeliveryAssignmentStatus.FAILED_DELIVERY);
        assignment.setNotes(appendNote(assignment.getNotes(), "Failure: " + reason + ". Details: " + notes));
        assignmentRepository.save(assignment);
        recordEvent(assignment, DeliveryEventType.DELIVERY_FAILED,
                    Map.of("reason", reason, "notes", notes),
                    assignment.getCourier() != null ? assignment.getCourier().getId() : null,
                    "COURIER_OR_SYSTEM"); // Or determine actor more precisely
        // TODO: Publish a delivery.failed event? (Not in original topic list but common)
    }

    @Override
    @Transactional
    public void cancelAssignment(UUID assignmentId, String reason, String cancelledBy) {
        log.warn("Cancelling assignmentId: {}. Reason: {}, By: {}", assignmentId, reason, cancelledBy);
        DeliveryAssignment assignment = findAssignmentOrFail(assignmentId);
        // TODO: Check if assignment is in a cancellable state
        assignment.setStatus(DeliveryAssignmentStatus.CANCELLED);
        assignment.setNotes(appendNote(assignment.getNotes(), "Cancelled: " + reason));
        assignmentRepository.save(assignment);
        recordEvent(assignment, DeliveryEventType.ASSIGNMENT_CANCELLED,
                    Map.of("reason", reason),
                    null, // Actor ID might be system or an admin user ID
                    cancelledBy != null ? cancelledBy.toUpperCase() : "SYSTEM");
        // TODO: Publish a delivery.assignment.cancelled event?
    }

    private DeliveryAssignment findAssignmentOrFail(UUID assignmentId) {
        return assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new EntityNotFoundException("DeliveryAssignment not found with ID: " + assignmentId));
    }

    private void recordEvent(DeliveryAssignment assignment, DeliveryEventType eventType, Map<String, Object> payload, UUID actorId, String actorType) {
        DeliveryEvent event = new DeliveryEvent(assignment, eventType, payload, actorId, actorType);
        eventRepository.save(event);
        log.debug("Recorded delivery event: type={}, assignmentId={}, actorId={}", eventType, assignment.getId(), actorId);
    }

    private String appendNote(String existingNotes, String newNote) {
        if (existingNotes == null || existingNotes.trim().isEmpty()) {
            return newNote;
        }
        return existingNotes + "\n" + newNote;
    }

    // For converting AddressDto to Map for JSONB - requires ObjectMapper
    // This should ideally be a shared utility or part of AddressDto itself if it were more complex.
    // ObjectMapper will be injected via @RequiredArgsConstructor if it's a final field.
    private final ObjectMapper objectMapper;
}
