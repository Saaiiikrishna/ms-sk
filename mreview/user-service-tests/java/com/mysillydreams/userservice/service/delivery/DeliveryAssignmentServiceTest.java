package com.mysillydreams.userservice.service.delivery;

import com.mysillydreams.userservice.domain.delivery.AssignmentStatus;
import com.mysillydreams.userservice.domain.delivery.AssignmentType;
import com.mysillydreams.userservice.domain.delivery.DeliveryProfile;
import com.mysillydreams.userservice.domain.delivery.OrderAssignment;
import com.mysillydreams.userservice.dto.delivery.OrderAssignmentDto;
import com.mysillydreams.userservice.repository.delivery.DeliveryProfileRepository;
import com.mysillydreams.userservice.repository.delivery.OrderAssignmentRepository;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;


import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeliveryAssignmentServiceTest {

    @Mock
    private OrderAssignmentRepository mockOrderAssignmentRepository;
    @Mock
    private DeliveryProfileRepository mockDeliveryProfileRepository;
    @Mock
    private DeliveryKafkaClient mockDeliveryKafkaClient;
    @Mock
    private DeliveryEventService mockDeliveryEventService;

    @InjectMocks
    private DeliveryAssignmentService deliveryAssignmentService;

    private UUID testProfileId;
    private DeliveryProfile testDeliveryProfile;
    private OrderAssignment testOrderAssignment;
    private UUID testAssignmentId;
    private UUID testOrderId;

    @BeforeEach
    void setUp() {
        testProfileId = UUID.randomUUID();
        testDeliveryProfile = new DeliveryProfile();
        testDeliveryProfile.setId(testProfileId);
        testDeliveryProfile.setActive(true);

        testAssignmentId = UUID.randomUUID();
        testOrderId = UUID.randomUUID();
        testOrderAssignment = new OrderAssignment();
        testOrderAssignment.setId(testAssignmentId);
        testOrderAssignment.setOrderId(testOrderId);
        testOrderAssignment.setDeliveryProfile(testDeliveryProfile);
        testOrderAssignment.setStatus(AssignmentStatus.ASSIGNED);
        testOrderAssignment.setType(AssignmentType.DELIVERY);
    }

    @Test
    void listActiveAssignmentsForProfile_profileExistsAndActive_returnsDtos() {
        when(mockDeliveryProfileRepository.findById(testProfileId)).thenReturn(Optional.of(testDeliveryProfile));
        List<OrderAssignment> assignments = List.of(testOrderAssignment);
        when(mockOrderAssignmentRepository.findByDeliveryProfileAndStatusIn(
                eq(testDeliveryProfile), anyList(), any(Sort.class)))
                .thenReturn(assignments);

        List<OrderAssignmentDto> resultDtos = deliveryAssignmentService.listActiveAssignmentsForProfile(testProfileId);

        assertNotNull(resultDtos);
        assertEquals(1, resultDtos.size());
        assertEquals(testOrderAssignment.getId(), resultDtos.get(0).getId());
        verify(mockDeliveryProfileRepository).findById(testProfileId);
    }

    @Test
    void listActiveAssignmentsForProfile_profileNotActive_returnsEmptyList() {
        testDeliveryProfile.setActive(false);
        when(mockDeliveryProfileRepository.findById(testProfileId)).thenReturn(Optional.of(testDeliveryProfile));

        List<OrderAssignmentDto> resultDtos = deliveryAssignmentService.listActiveAssignmentsForProfile(testProfileId);
        assertTrue(resultDtos.isEmpty());
    }

    @Test
    void listActiveAssignmentsForProfile_profileNotFound_throwsEntityNotFound() {
        when(mockDeliveryProfileRepository.findById(testProfileId)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> {
            deliveryAssignmentService.listActiveAssignmentsForProfile(testProfileId);
        });
    }


    @Test
    void updateAssignmentStatus_validTransition_updatesStatusAndPublishesEvent() {
        testOrderAssignment.setStatus(AssignmentStatus.EN_ROUTE); // Current status
        AssignmentStatus newStatus = AssignmentStatus.ARRIVED_AT_DROPOFF;
        Map<String, Object> payload = Map.of("reason", "test update");

        when(mockOrderAssignmentRepository.findById(testAssignmentId)).thenReturn(Optional.of(testOrderAssignment));
        when(mockOrderAssignmentRepository.save(any(OrderAssignment.class))).thenReturn(testOrderAssignment);
        doNothing().when(mockDeliveryKafkaClient).publishDeliveryStatusChanged(any(OrderAssignment.class), anyString());

        OrderAssignment result = deliveryAssignmentService.updateAssignmentStatus(testAssignmentId, newStatus, payload);

        assertEquals(newStatus, result.getStatus());
        verify(mockOrderAssignmentRepository).save(testOrderAssignment);
        verify(mockDeliveryKafkaClient).publishDeliveryStatusChanged(testOrderAssignment, AssignmentStatus.EN_ROUTE.toString());
        verify(mockDeliveryEventService).recordEvent(testAssignmentId, "STATUS_UPDATE_" + newStatus.toString(), payload);
    }

    @Test
    void updateAssignmentStatus_toCompleted_prerequisitesMet_success() {
        testOrderAssignment.setStatus(AssignmentStatus.ARRIVED_AT_DROPOFF);
        AssignmentStatus newStatus = AssignmentStatus.COMPLETED;

        when(mockOrderAssignmentRepository.findById(testAssignmentId)).thenReturn(Optional.of(testOrderAssignment));
        when(mockDeliveryEventService.hasEventOccurred(testOrderAssignment, "PHOTO_TAKEN")).thenReturn(true);
        when(mockDeliveryEventService.hasEventOccurred(testOrderAssignment, "OTP_VERIFIED_SUCCESS")).thenReturn(true);
        when(mockOrderAssignmentRepository.save(any(OrderAssignment.class))).thenReturn(testOrderAssignment);

        OrderAssignment result = deliveryAssignmentService.updateAssignmentStatus(testAssignmentId, newStatus, null);

        assertEquals(newStatus, result.getStatus());
        verify(mockDeliveryKafkaClient).publishDeliveryStatusChanged(any(OrderAssignment.class), eq(AssignmentStatus.ARRIVED_AT_DROPOFF.toString()));
    }

    @Test
    void updateAssignmentStatus_toCompleted_photoMissing_throwsIllegalStateException() {
        testOrderAssignment.setStatus(AssignmentStatus.ARRIVED_AT_DROPOFF);
        AssignmentStatus newStatus = AssignmentStatus.COMPLETED;

        when(mockOrderAssignmentRepository.findById(testAssignmentId)).thenReturn(Optional.of(testOrderAssignment));
        when(mockDeliveryEventService.hasEventOccurred(testOrderAssignment, "PHOTO_TAKEN")).thenReturn(false);
        // OTP check might not even be reached if photo check fails first in validateStatusTransition

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            deliveryAssignmentService.updateAssignmentStatus(testAssignmentId, newStatus, null);
        });
        assertTrue(ex.getMessage().contains("PHOTO_TAKEN event is required"));
    }

    @Test
    void updateAssignmentStatus_toCompleted_otpMissing_throwsIllegalStateException() {
        testOrderAssignment.setStatus(AssignmentStatus.ARRIVED_AT_DROPOFF);
        AssignmentStatus newStatus = AssignmentStatus.COMPLETED;

        when(mockOrderAssignmentRepository.findById(testAssignmentId)).thenReturn(Optional.of(testOrderAssignment));
        when(mockDeliveryEventService.hasEventOccurred(testOrderAssignment, "PHOTO_TAKEN")).thenReturn(true);
        when(mockDeliveryEventService.hasEventOccurred(testOrderAssignment, "OTP_VERIFIED_SUCCESS")).thenReturn(false);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            deliveryAssignmentService.updateAssignmentStatus(testAssignmentId, newStatus, null);
        });
        assertTrue(ex.getMessage().contains("OTP_VERIFIED_SUCCESS event) is required"));
    }


    @Test
    void updateAssignmentStatus_invalidTransition_throwsIllegalStateException() {
        testOrderAssignment.setStatus(AssignmentStatus.ASSIGNED); // Current status
        AssignmentStatus newStatus = AssignmentStatus.COMPLETED; // Invalid direct transition

        when(mockOrderAssignmentRepository.findById(testAssignmentId)).thenReturn(Optional.of(testOrderAssignment));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            deliveryAssignmentService.updateAssignmentStatus(testAssignmentId, newStatus, null);
        });
        assertTrue(ex.getMessage().contains("Invalid status transition"));
    }

    @Test
    void updateAssignmentStatus_assignmentNotFound_throwsEntityNotFoundException() {
        UUID nonExistentAssignmentId = UUID.randomUUID();
        when(mockOrderAssignmentRepository.findById(nonExistentAssignmentId)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> {
            deliveryAssignmentService.updateAssignmentStatus(nonExistentAssignmentId, AssignmentStatus.EN_ROUTE, null);
        });
    }


    @Test
    void createAssignment_success() {
        UUID orderId = UUID.randomUUID();
        when(mockDeliveryProfileRepository.findById(testProfileId)).thenReturn(Optional.of(testDeliveryProfile));
        when(mockOrderAssignmentRepository.findByOrderId(orderId)).thenReturn(Optional.empty());
        when(mockOrderAssignmentRepository.save(any(OrderAssignment.class))).thenAnswer(inv -> {
            OrderAssignment oa = inv.getArgument(0);
            oa.setId(UUID.randomUUID());
            return oa;
        });

        OrderAssignment result = deliveryAssignmentService.createAssignment(orderId, testProfileId, AssignmentType.DELIVERY);

        assertNotNull(result);
        assertEquals(orderId, result.getOrderId());
        assertEquals(testDeliveryProfile, result.getDeliveryProfile());
        assertEquals(AssignmentStatus.ASSIGNED, result.getStatus());
        verify(mockDeliveryKafkaClient).publishOrderAssigned(result);
    }

    @Test
    void createAssignment_profileNotActive_throwsIllegalStateException() {
        testDeliveryProfile.setActive(false);
        when(mockDeliveryProfileRepository.findById(testProfileId)).thenReturn(Optional.of(testDeliveryProfile));

        assertThrows(IllegalStateException.class, () -> {
            deliveryAssignmentService.createAssignment(UUID.randomUUID(), testProfileId, AssignmentType.DELIVERY);
        });
    }

    @Test
    void createAssignment_orderAlreadyAssigned_throwsIllegalStateException() {
        UUID orderId = UUID.randomUUID();
        when(mockDeliveryProfileRepository.findById(testProfileId)).thenReturn(Optional.of(testDeliveryProfile));
        when(mockOrderAssignmentRepository.findByOrderId(orderId)).thenReturn(Optional.of(new OrderAssignment())); // Order exists

        assertThrows(IllegalStateException.class, () -> {
            deliveryAssignmentService.createAssignment(orderId, testProfileId, AssignmentType.DELIVERY);
        });
    }
}
