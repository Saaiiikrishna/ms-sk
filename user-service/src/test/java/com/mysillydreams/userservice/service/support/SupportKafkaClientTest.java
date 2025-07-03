package com.mysillydreams.userservice.service.support;

import com.mysillydreams.userservice.domain.support.SupportProfile;
import com.mysillydreams.userservice.domain.support.SupportTicket;
import com.mysillydreams.userservice.domain.support.TicketStatus;
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
class SupportKafkaClientTest {

    @Mock
    private KafkaTemplate<String, Object> mockKafkaTemplate;

    private String testTicketCreatedTopic = "test.support.ticket.created";
    private String testTicketUpdatedTopic = "test.support.ticket.updated";

    private SupportKafkaClient supportKafkaClient;

    @Captor
    private ArgumentCaptor<String> topicCaptor;
    @Captor
    private ArgumentCaptor<String> keyCaptor;
    @Captor
    private ArgumentCaptor<Map<String, Object>> payloadCaptor;

    private SupportTicket sampleTicket;

    @BeforeEach
    void setUp() {
        supportKafkaClient = new SupportKafkaClient(
                mockKafkaTemplate,
                testTicketCreatedTopic,
                testTicketUpdatedTopic
        );

        SupportProfile assignedTo = new SupportProfile();
        assignedTo.setId(UUID.randomUUID());

        sampleTicket = new SupportTicket();
        sampleTicket.setId(UUID.randomUUID());
        sampleTicket.setCustomerId(UUID.randomUUID());
        sampleTicket.setSubject("Test Ticket Subject");
        sampleTicket.setDescription("Test ticket description.");
        sampleTicket.setStatus(TicketStatus.OPEN);
        sampleTicket.setCreatedAt(Instant.now());
        sampleTicket.setUpdatedAt(Instant.now());
        sampleTicket.setAssignedTo(assignedTo);
    }

    @Test
    void publishSupportTicketCreated_sendsCorrectEvent() {
        SettableListenableFuture<SendResult<String, Object>> future = new SettableListenableFuture<>();
        when(mockKafkaTemplate.send(anyString(), anyString(), anyMap())).thenReturn(future);

        supportKafkaClient.publishSupportTicketCreated(sampleTicket);

        verify(mockKafkaTemplate).send(topicCaptor.capture(), keyCaptor.capture(), payloadCaptor.capture());

        assertEquals(testTicketCreatedTopic, topicCaptor.getValue());
        assertEquals(sampleTicket.getId().toString(), keyCaptor.getValue());

        Map<String, Object> payload = payloadCaptor.getValue();
        assertEquals(sampleTicket.getId().toString(), payload.get("ticketId"));
        assertEquals(sampleTicket.getCustomerId().toString(), payload.get("customerId"));
        assertEquals(sampleTicket.getSubject(), payload.get("subject"));
        assertEquals(sampleTicket.getStatus().toString(), payload.get("status"));
        assertEquals(sampleTicket.getCreatedAt().toString(), payload.get("createdAt"));
        assertEquals(sampleTicket.getAssignedTo().getId().toString(), payload.get("assignedToSupportProfileId"));
        assertEquals("SupportTicketCreated", payload.get("eventType"));
    }

    @Test
    void publishSupportTicketCreated_nullTicket_doesNotSend() {
        supportKafkaClient.publishSupportTicketCreated(null);
        verifyNoInteractions(mockKafkaTemplate);
    }

    @Test
    void publishSupportTicketUpdated_sendsCorrectEvent() {
        SettableListenableFuture<SendResult<String, Object>> future = new SettableListenableFuture<>();
        when(mockKafkaTemplate.send(anyString(), anyString(), anyMap())).thenReturn(future);

        String oldStatus = TicketStatus.OPEN.toString();
        UUID newMessageId = UUID.randomUUID();
        sampleTicket.setStatus(TicketStatus.IN_PROGRESS); // Simulate status update
        sampleTicket.setUpdatedAt(Instant.now().plusSeconds(60));


        supportKafkaClient.publishSupportTicketUpdated(sampleTicket, oldStatus, newMessageId);

        verify(mockKafkaTemplate).send(topicCaptor.capture(), keyCaptor.capture(), payloadCaptor.capture());

        assertEquals(testTicketUpdatedTopic, topicCaptor.getValue());
        assertEquals(sampleTicket.getId().toString(), keyCaptor.getValue());

        Map<String, Object> payload = payloadCaptor.getValue();
        assertEquals(sampleTicket.getId().toString(), payload.get("ticketId"));
        assertEquals(sampleTicket.getCustomerId().toString(), payload.get("customerId"));
        assertEquals(TicketStatus.IN_PROGRESS.toString(), payload.get("newStatus"));
        assertEquals(oldStatus, payload.get("oldStatus"));
        assertEquals(sampleTicket.getAssignedTo().getId().toString(), payload.get("assignedToSupportProfileId"));
        assertEquals(newMessageId.toString(), payload.get("newMessageId"));
        assertEquals(sampleTicket.getUpdatedAt().toString(), payload.get("updatedAt"));
        assertEquals("SupportTicketUpdated", payload.get("eventType"));
    }

    @Test
    void publishSupportTicketUpdated_nullTicket_doesNotSend() {
        supportKafkaClient.publishSupportTicketUpdated(null, "OLD_STATUS", UUID.randomUUID());
        verifyNoInteractions(mockKafkaTemplate);
    }
}
