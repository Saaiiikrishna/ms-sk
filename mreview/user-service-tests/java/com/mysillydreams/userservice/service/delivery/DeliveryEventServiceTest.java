package com.mysillydreams.userservice.service.delivery;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mysillydreams.userservice.domain.delivery.DeliveryEvent;
import com.mysillydreams.userservice.domain.delivery.OrderAssignment;
import com.mysillydreams.userservice.repository.delivery.DeliveryEventRepository;
import com.mysillydreams.userservice.repository.delivery.OrderAssignmentRepository;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils; // Not needed if using @Spy correctly


import java.util.Collections; // Added for Collections.emptyMap()
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeliveryEventServiceTest {

    @Mock
    private DeliveryEventRepository mockDeliveryEventRepository;
    @Mock
    private OrderAssignmentRepository mockOrderAssignmentRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @InjectMocks
    private DeliveryEventService deliveryEventService;

    private UUID testAssignmentId;
    private OrderAssignment testOrderAssignment;

    @BeforeEach
    void setUp() {
        testAssignmentId = UUID.randomUUID();
        testOrderAssignment = new OrderAssignment();
        testOrderAssignment.setId(testAssignmentId);
    }

    @Test
    void recordEvent_success_withPayload() throws JsonProcessingException {
        when(mockOrderAssignmentRepository.findById(testAssignmentId)).thenReturn(Optional.of(testOrderAssignment));
        when(mockDeliveryEventRepository.save(any(DeliveryEvent.class))).thenAnswer(inv -> {
            DeliveryEvent event = inv.getArgument(0);
            event.setId(UUID.randomUUID());
            return event;
        });

        String eventType = "GPS_UPDATE";
        Map<String, Object> payload = Map.of("lat", 12.34, "lon", 56.78);

        DeliveryEvent resultEvent = deliveryEventService.recordEvent(testAssignmentId, eventType, payload);

        assertNotNull(resultEvent);
        assertNotNull(resultEvent.getId());
        assertEquals(testOrderAssignment, resultEvent.getAssignment());
        assertEquals(eventType, resultEvent.getEventType());
        assertEquals(objectMapper.writeValueAsString(payload), resultEvent.getPayload());
        assertNotNull(resultEvent.getTimestamp());

        verify(mockDeliveryEventRepository).save(any(DeliveryEvent.class));
    }

    @Test
    void recordEvent_success_nullPayload() {
        when(mockOrderAssignmentRepository.findById(testAssignmentId)).thenReturn(Optional.of(testOrderAssignment));
        when(mockDeliveryEventRepository.save(any(DeliveryEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        String eventType = "CALL_INITIATED";
        DeliveryEvent resultEvent = deliveryEventService.recordEvent(testAssignmentId, eventType, null);

        assertNotNull(resultEvent);
        assertEquals(eventType, resultEvent.getEventType());
        assertNull(resultEvent.getPayload());
        verify(mockDeliveryEventRepository).save(any(DeliveryEvent.class));
    }

    @Test
    void recordEvent_success_emptyPayload() {
        when(mockOrderAssignmentRepository.findById(testAssignmentId)).thenReturn(Optional.of(testOrderAssignment));
        when(mockDeliveryEventRepository.save(any(DeliveryEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        String eventType = "NOTE_ADDED";
        DeliveryEvent resultEvent = deliveryEventService.recordEvent(testAssignmentId, eventType, Collections.emptyMap());

        assertNotNull(resultEvent);
        assertEquals(eventType, resultEvent.getEventType());
        assertNull(resultEvent.getPayload());
        verify(mockDeliveryEventRepository).save(any(DeliveryEvent.class));
    }


    @Test
    void recordEvent_assignmentNotFound_throwsEntityNotFoundException() {
        when(mockOrderAssignmentRepository.findById(testAssignmentId)).thenReturn(Optional.empty());
        String eventType = "ANY_EVENT";
        assertThrows(EntityNotFoundException.class, () -> {
            deliveryEventService.recordEvent(testAssignmentId, eventType, null);
        });
    }

    @Test
    void recordEvent_payloadSerializationFailure_throwsRuntimeException() throws JsonProcessingException {
        when(mockOrderAssignmentRepository.findById(testAssignmentId)).thenReturn(Optional.of(testOrderAssignment));

        // Use doThrow with the @Spy objectMapper
        doThrow(new JsonProcessingException("Serialization fail"){})
            .when(objectMapper).writeValueAsString(anyMap());

        String eventType = "FAIL_EVENT";
        Map<String, Object> payload = Map.of("key", "value");

        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            deliveryEventService.recordEvent(testAssignmentId, eventType, payload);
        });
        assertTrue(ex.getMessage().contains("Failed to serialize event payload"));
    }

    @Test
    void hasEventOccurred_eventExists_returnsTrue() {
        String eventTypeToCheck = "PHOTO_TAKEN";
        when(mockDeliveryEventRepository.countByAssignmentAndEventType(testOrderAssignment, eventTypeToCheck)).thenReturn(1L);
        assertTrue(deliveryEventService.hasEventOccurred(testOrderAssignment, eventTypeToCheck));
    }

    @Test
    void hasEventOccurred_eventNotExists_returnsFalse() {
        String eventTypeToCheck = "NON_EXISTENT_EVENT";
        when(mockDeliveryEventRepository.countByAssignmentAndEventType(testOrderAssignment, eventTypeToCheck)).thenReturn(0L);
        assertFalse(deliveryEventService.hasEventOccurred(testOrderAssignment, eventTypeToCheck));
    }

    @Test
    void verifyEventSequence_otpBeforePhoto_throwsIllegalStateException() {
        when(mockDeliveryEventRepository.countByAssignmentAndEventType(testOrderAssignment, "PHOTO_TAKEN")).thenReturn(0L);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            deliveryEventService.verifyEventSequence(testOrderAssignment, "OTP_VERIFIED");
        });
        assertTrue(ex.getMessage().contains("Cannot verify OTP before photo is taken"));
    }

    @Test
    void verifyEventSequence_otpAfterPhoto_doesNotThrow() {
        when(mockDeliveryEventRepository.countByAssignmentAndEventType(testOrderAssignment, "PHOTO_TAKEN")).thenReturn(1L);

        assertDoesNotThrow(() -> {
            deliveryEventService.verifyEventSequence(testOrderAssignment, "OTP_VERIFIED");
        });
    }

    @Test
    void verifyEventSequence_otherEvent_doesNotThrow() {
         assertDoesNotThrow(() -> {
            deliveryEventService.verifyEventSequence(testOrderAssignment, "ARRIVED_AT_LOCATION");
        });
    }
}
