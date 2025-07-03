package com.mysillydreams.userservice.service.delivery;

import com.mysillydreams.userservice.domain.UserEntity;
import com.mysillydreams.userservice.domain.delivery.AssignmentStatus;
import com.mysillydreams.userservice.domain.delivery.AssignmentType;
import com.mysillydreams.userservice.domain.delivery.DeliveryProfile;
import com.mysillydreams.userservice.domain.delivery.OrderAssignment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.util.concurrent.SettableListenableFuture;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeliveryKafkaClientTest {

    @Mock
    private KafkaTemplate<String, Object> mockKafkaTemplate;

    private String testOrderAssignedTopic = "test.order.assigned";
    private String testDeliveryStatusChangedTopic = "test.delivery.status.changed";

    private DeliveryKafkaClient deliveryKafkaClient;

    @Captor
    private ArgumentCaptor<String> topicCaptor;
    @Captor
    private ArgumentCaptor<String> keyCaptor;
    @Captor
    private ArgumentCaptor<Map<String, Object>> payloadCaptor;

    private OrderAssignment sampleAssignment;
    private DeliveryProfile sampleDeliveryProfile;
    private UserEntity sampleUser;

    @BeforeEach
    void setUp() {
        deliveryKafkaClient = new DeliveryKafkaClient(
                mockKafkaTemplate,
                testOrderAssignedTopic,
                testDeliveryStatusChangedTopic
        );

        sampleUser = new UserEntity();
        sampleUser.setId(UUID.randomUUID());

        sampleDeliveryProfile = new DeliveryProfile();
        sampleDeliveryProfile.setId(UUID.randomUUID());
        sampleDeliveryProfile.setUser(sampleUser);

        sampleAssignment = new OrderAssignment();
        sampleAssignment.setId(UUID.randomUUID());
        sampleAssignment.setOrderId(UUID.randomUUID());
        sampleAssignment.setDeliveryProfile(sampleDeliveryProfile);
        sampleAssignment.setType(AssignmentType.DELIVERY);
        sampleAssignment.setStatus(AssignmentStatus.ASSIGNED);
        sampleAssignment.setAssignedAt(Instant.now());
        sampleAssignment.setLastUpdatedAt(Instant.now());
    }

    @Test
    void publishOrderAssigned_sendsCorrectEvent() {
        SettableListenableFuture<SendResult<String, Object>> future = new SettableListenableFuture<>();
        when(mockKafkaTemplate.send(anyString(), anyString(), anyMap())).thenReturn(future);

        deliveryKafkaClient.publishOrderAssigned(sampleAssignment);

        verify(mockKafkaTemplate).send(topicCaptor.capture(), keyCaptor.capture(), payloadCaptor.capture());

        assertEquals(testOrderAssignedTopic, topicCaptor.getValue());
        assertEquals(sampleAssignment.getId().toString(), keyCaptor.getValue());

        Map<String, Object> payload = payloadCaptor.getValue();
        assertEquals(sampleAssignment.getId().toString(), payload.get("assignmentId"));
        assertEquals(sampleAssignment.getOrderId().toString(), payload.get("orderId"));
        assertEquals(sampleDeliveryProfile.getId().toString(), payload.get("deliveryProfileId"));
        assertEquals(sampleUser.getId().toString(), payload.get("deliveryUserId"));
        assertEquals(sampleAssignment.getType().toString(), payload.get("assignmentType"));
        assertEquals(sampleAssignment.getStatus().toString(), payload.get("status"));
        assertEquals(sampleAssignment.getAssignedAt().toString(), payload.get("assignedAt"));
        assertEquals("OrderAssigned", payload.get("eventType"));
    }

    @Test
    void publishDeliveryStatusChanged_sendsCorrectEvent() {
        SettableListenableFuture<SendResult<String, Object>> future = new SettableListenableFuture<>();
        when(mockKafkaTemplate.send(anyString(), anyString(), anyMap())).thenReturn(future);

        String oldStatus = AssignmentStatus.EN_ROUTE.toString();
        sampleAssignment.setStatus(AssignmentStatus.ARRIVED_AT_DROPOFF); // New status
        sampleAssignment.setLastUpdatedAt(Instant.now().plusSeconds(60));


        deliveryKafkaClient.publishDeliveryStatusChanged(sampleAssignment, oldStatus);

        verify(mockKafkaTemplate).send(topicCaptor.capture(), keyCaptor.capture(), payloadCaptor.capture());

        assertEquals(testDeliveryStatusChangedTopic, topicCaptor.getValue());
        assertEquals(sampleAssignment.getId().toString(), keyCaptor.getValue());

        Map<String, Object> payload = payloadCaptor.getValue();
        assertEquals(sampleAssignment.getId().toString(), payload.get("assignmentId"));
        assertEquals(sampleAssignment.getOrderId().toString(), payload.get("orderId"));
        assertEquals(sampleDeliveryProfile.getId().toString(), payload.get("deliveryProfileId"));
        assertEquals(AssignmentStatus.ARRIVED_AT_DROPOFF.toString(), payload.get("newStatus"));
        assertEquals(oldStatus, payload.get("oldStatus"));
        assertEquals(sampleAssignment.getLastUpdatedAt().toString(), payload.get("statusChangeTimestamp"));
        assertEquals("DeliveryStatusChanged", payload.get("eventType"));
    }

    @Test
    void publishOrderAssigned_nullAssignment_doesNotSend() {
        deliveryKafkaClient.publishOrderAssigned(null);
        verifyNoInteractions(mockKafkaTemplate);
    }

    @Test
    void publishDeliveryStatusChanged_nullAssignment_doesNotSend() {
        deliveryKafkaClient.publishDeliveryStatusChanged(null, "ANY_STATUS");
        verifyNoInteractions(mockKafkaTemplate);
    }
}
