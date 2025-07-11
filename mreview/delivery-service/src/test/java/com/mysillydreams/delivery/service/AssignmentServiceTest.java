package com.mysillydreams.delivery.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mysillydreams.delivery.domain.DeliveryAssignment;
import com.mysillydreams.delivery.domain.DeliveryEvent;
import com.mysillydreams.delivery.domain.DeliveryProfile;
import com.mysillydreams.delivery.domain.enums.DeliveryAssignmentStatus;
import com.mysillydreams.delivery.domain.enums.DeliveryEventType;
// Import Avro classes used by AssignmentService
import com.mysillydreams.delivery.dto.avro.*; // Wildcard import for all Avro DTOs in this package
import com.mysillydreams.delivery.dto.PhotoOtpDto; // REST DTO
import com.mysillydreams.delivery.repository.DeliveryAssignmentRepository;
import com.mysillydreams.delivery.repository.DeliveryEventRepository;
import com.mysillydreams.delivery.repository.DeliveryProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils; // For setting @Value fields if needed

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssignmentServiceTest {

    @Mock
    private DeliveryAssignmentRepository assignmentRepository;
    @Mock
    private DeliveryEventRepository eventRepository;
    @Mock
    private DeliveryProfileRepository profileRepository;
    @Mock
    private OutboxEventService outboxEventService; // Real OutboxEventService (not Local stub)
    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate; // For direct GPS publishing

    @Spy // Use Spy for ObjectMapper if we need to test its actual conversion
    private ObjectMapper objectMapper = new ObjectMapper(); // Real ObjectMapper for conversions

    @InjectMocks
    private AssignmentServiceImpl assignmentService;

    private UUID orderId;
    private UUID vendorId;
    private UUID customerId;
    private UUID courierId;
    private UUID assignmentId;
    private ShipmentRequestedEvent shipmentRequestedEvent;
    private DeliveryProfile testCourier;

    // Topic names (can be set via ReflectionTestUtils if not using @Value in service for these)
    private final String gpsUpdatesTopic = "delivery.gps.updates";
    // Outbox event types (which are also topic names)
    private final String assignmentCreatedEventType = "delivery.assignment.created";
    private final String pickedUpEventType = "delivery.picked_up";
    private final String deliveredEventType = "delivery.delivered";


    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID();
        vendorId = UUID.randomUUID();
        customerId = UUID.randomUUID();
        courierId = UUID.randomUUID();
        assignmentId = UUID.randomUUID();

        // Inject ObjectMapper into assignmentService as it's now a final field
        ReflectionTestUtils.setField(assignmentService, "objectMapper", objectMapper);
        // Inject topic names (if they were @Value annotated in service and not passed via constructor)
        ReflectionTestUtils.setField(assignmentService, "gpsUpdatesTopic", gpsUpdatesTopic);
        // The other topic names are used as eventType strings directly when calling outbox.

        AddressAvro pickupAddress = AddressAvro.newBuilder().setStreet("123 Vendor St").setCity("VendorCity").setStateOrProvince("VS").setPostalCode("V1V1V1").setCountryCode("VC").build();
        AddressAvro dropoffAddress = AddressAvro.newBuilder().setStreet("456 Customer Ave").setCity("CustCity").setStateOrProvince("CS").setPostalCode("C1C1C1").setCountryCode("CC").build();

        shipmentRequestedEvent = ShipmentRequestedEvent.newBuilder()
                .setOrderId(orderId.toString())
                .setVendorId(vendorId.toString())
                .setCustomerId(customerId.toString())
                .setPickupAddress(pickupAddress)
                .setDropoffAddress(dropoffAddress)
                .build();

        testCourier = new DeliveryProfile();
        testCourier.setId(courierId);
        testCourier.setStatus("ACTIVE");
    }

    @Test
    void createAssignment_whenCourierAvailable_createsAssignmentAndPublishesEvent() {
        // Given
        when(profileRepository.findByStatus("ACTIVE")).thenReturn(List.of(testCourier));

        DeliveryAssignment savedAssignmentMock = new DeliveryAssignment();
        // Populate savedAssignmentMock with expected fields after service logic
        savedAssignmentMock.setId(assignmentId); // Service will generate one, but we can mock it
        savedAssignmentMock.setOrderId(orderId);
        savedAssignmentMock.setCourier(testCourier);
        savedAssignmentMock.setVendorId(vendorId);
        savedAssignmentMock.setCustomerId(customerId);
        savedAssignmentMock.setStatus(DeliveryAssignmentStatus.ASSIGNED);
        savedAssignmentMock.setEstimatedPickupTime(Instant.now().plusSeconds(100)); // Example, match service logic if possible
        savedAssignmentMock.setEstimatedDeliveryTime(Instant.now().plusSeconds(200));


        ArgumentCaptor<DeliveryAssignment> assignmentCaptor = ArgumentCaptor.forClass(DeliveryAssignment.class);
        // Make repository.save return the captured assignment with an ID if it's null
        when(assignmentRepository.save(assignmentCaptor.capture())).thenAnswer(invocation -> {
            DeliveryAssignment da = invocation.getArgument(0);
            if (da.getId() == null) da.setId(UUID.randomUUID()); // Simulate ID generation
            // Simulate setting other DB-generated or @PrePersist fields if service doesn't set them all
            da.setCreatedAt(Instant.now());
            da.setUpdatedAt(Instant.now());
            da.setVersion(0L);
            return da;
        });


        ArgumentCaptor<Object> outboxPayloadCaptor = ArgumentCaptor.forClass(Object.class);

        // When
        UUID resultAssignmentId = assignmentService.createAssignment(shipmentRequestedEvent);

        // Then
        assertNotNull(resultAssignmentId);
        // assertEquals(assignmentId, resultAssignmentId); // If we could control generated UUID

        DeliveryAssignment capturedAssignment = assignmentCaptor.getValue();
        assertEquals(orderId, capturedAssignment.getOrderId());
        assertEquals(courierId, capturedAssignment.getCourier().getId());
        assertEquals(DeliveryAssignmentStatus.ASSIGNED, capturedAssignment.getStatus());

        verify(eventRepository).save(any(DeliveryEvent.class));
        verify(outboxEventService).createAndSaveOutboxEvent(
                eq("DeliveryAssignment"),
                eq(resultAssignmentId.toString()),
                eq(assignmentCreatedEventType), // Topic name as eventType
                outboxPayloadCaptor.capture()
        );
        assertTrue(outboxPayloadCaptor.getValue() instanceof DeliveryAssignmentCreatedEvent);
        DeliveryAssignmentCreatedEvent event = (DeliveryAssignmentCreatedEvent) outboxPayloadCaptor.getValue();
        assertEquals(resultAssignmentId.toString(), event.getAssignmentId());
        assertEquals(orderId.toString(), event.getOrderId());
        assertEquals(courierId.toString(), event.getCourierId());
    }

    @Test
    void createAssignment_whenNoCourierAvailable_throwsIllegalStateException() {
        // Given
        when(profileRepository.findByStatus("ACTIVE")).thenReturn(Collections.emptyList());

        // When & Then
        assertThrows(IllegalStateException.class, () -> {
            assignmentService.createAssignment(shipmentRequestedEvent);
        });
        verify(assignmentRepository, never()).save(any());
        verify(outboxEventService, never()).createAndSaveOutboxEvent(any(), any(), any(), any());
    }

    @Test
    void markArrivedAtPickup_updatesStatusAndRecordsEvent() {
        // Given
        DeliveryAssignment assignment = new DeliveryAssignment();
        assignment.setId(assignmentId);
        assignment.setStatus(DeliveryAssignmentStatus.ASSIGNED);
        assignment.setCourier(testCourier); // Courier must be set for actorId
        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));

        // When
        assignmentService.markArrivedAtPickup(assignmentId);

        // Then
        assertEquals(DeliveryAssignmentStatus.ARRIVED_AT_PICKUP, assignment.getStatus());
        verify(assignmentRepository).save(assignment);
        ArgumentCaptor<DeliveryEvent> eventCaptor = ArgumentCaptor.forClass(DeliveryEvent.class);
        verify(eventRepository).save(eventCaptor.capture());
        assertEquals(DeliveryEventType.ARRIVED_AT_PICKUP, eventCaptor.getValue().getEventType());
    }

    @Test
    void markPickedUp_updatesStatusAndPublishesEvent() {
        // Given
        DeliveryAssignment assignment = new DeliveryAssignment();
        assignment.setId(assignmentId);
        assignment.setOrderId(orderId);
        assignment.setStatus(DeliveryAssignmentStatus.ARRIVED_AT_PICKUP);
        assignment.setCourier(testCourier);
        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));

        PhotoOtpDto dto = new PhotoOtpDto("http://photo.url/pickup.jpg", "1234", "Notes");
        ArgumentCaptor<Object> outboxPayloadCaptor = ArgumentCaptor.forClass(Object.class);

        // When
        assignmentService.markPickedUp(assignmentId, dto);

        // Then
        assertEquals(DeliveryAssignmentStatus.PICKED_UP, assignment.getStatus());
        assertEquals("http://photo.url/pickup.jpg", assignment.getPickupPhotoUrl());
        assertTrue(assignment.getPickupOtpVerified());
        assertNotNull(assignment.getActualPickupTime());
        verify(assignmentRepository).save(assignment);
        verify(eventRepository).save(any(DeliveryEvent.class)); // Check type PICKED_UP
        verify(outboxEventService).createAndSaveOutboxEvent(
            eq("DeliveryAssignment"), eq(assignmentId.toString()), eq(pickedUpEventType), outboxPayloadCaptor.capture());
        assertTrue(outboxPayloadCaptor.getValue() instanceof DeliveryPickedUpEvent);
        DeliveryPickedUpEvent event = (DeliveryPickedUpEvent) outboxPayloadCaptor.getValue();
        assertEquals(assignmentId.toString(), event.getAssignmentId());
    }

    @Test
    void publishGpsUpdate_sendsToKafka() {
        // Given
        GpsUpdateDto dto = new GpsUpdateDto(10.0, 20.0, Instant.now(), 5.0, 15.0, 90.0);
        // The service converts GpsUpdateDto to Avro GpsUpdateEvent
        ArgumentCaptor<Object> kafkaPayloadCaptor = ArgumentCaptor.forClass(Object.class);

        // When
        assignmentService.publishGpsUpdate(assignmentId, dto);

        // Then
        verify(kafkaTemplate).send(eq(gpsUpdatesTopic), eq(assignmentId.toString()), kafkaPayloadCaptor.capture());
        assertTrue(kafkaPayloadCaptor.getValue() instanceof GpsUpdateEvent);
        GpsUpdateEvent event = (GpsUpdateEvent) kafkaPayloadCaptor.getValue();
        assertEquals(assignmentId.toString(), event.getAssignmentId());
        assertEquals(10.0, event.getLatitude(), 0.001);
    }

    @Test
    void markDelivered_updatesStatusAndPublishesEvent() {
        // Given
        DeliveryAssignment assignment = new DeliveryAssignment();
        assignment.setId(assignmentId);
        assignment.setOrderId(orderId);
        assignment.setStatus(DeliveryAssignmentStatus.ARRIVED_AT_DROPOFF); // Or IN_TRANSIT
        assignment.setCourier(testCourier);
        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));

        PhotoOtpDto dto = new PhotoOtpDto("http://photo.url/delivery.jpg", "5678", "Delivered to recipient.");
        ArgumentCaptor<Object> outboxPayloadCaptor = ArgumentCaptor.forClass(Object.class);

        // When
        assignmentService.markDelivered(assignmentId, dto);

        // Then
        assertEquals(DeliveryAssignmentStatus.DELIVERED, assignment.getStatus());
        assertEquals("http://photo.url/delivery.jpg", assignment.getDeliveryPhotoUrl());
        assertTrue(assignment.getDeliveryOtpVerified());
        assertNotNull(assignment.getActualDeliveryTime());
        verify(assignmentRepository).save(assignment);
        verify(eventRepository).save(any(DeliveryEvent.class)); // Check type DELIVERED
        verify(outboxEventService).createAndSaveOutboxEvent(
            eq("DeliveryAssignment"), eq(assignmentId.toString()), eq(deliveredEventType), outboxPayloadCaptor.capture());
        assertTrue(outboxPayloadCaptor.getValue() instanceof DeliveryDeliveredEvent);
        DeliveryDeliveredEvent event = (DeliveryDeliveredEvent) outboxPayloadCaptor.getValue();
        assertEquals(assignmentId.toString(), event.getAssignmentId());
    }

    // TODO: Add tests for markArrivedAtDropoff, markDeliveryFailed, cancelAssignment
    // TODO: Add tests for edge cases, invalid status transitions, EntityNotFoundException
}
