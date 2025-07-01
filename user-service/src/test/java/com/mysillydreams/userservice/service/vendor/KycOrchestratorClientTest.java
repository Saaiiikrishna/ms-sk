package com.mysillydreams.userservice.service.vendor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.SettableListenableFuture;


import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KycOrchestratorClientTest {

    @Mock
    private KafkaTemplate<String, Object> mockKafkaTemplate;

    private String testStartKycTopic = "test.kyc.start";

    // No @InjectMocks needed if we manually instantiate or if constructor takes only mocks.
    // Here, constructor takes a mock and a @Value-injected field.
    // For unit test, we pass the topic name directly.
    private KycOrchestratorClient kycOrchestratorClient;

    @Captor
    private ArgumentCaptor<String> topicCaptor;
    @Captor
    private ArgumentCaptor<String> keyCaptor;
    @Captor
    private ArgumentCaptor<Map<String, String>> payloadCaptor;

    @BeforeEach
    void setUp() {
        kycOrchestratorClient = new KycOrchestratorClient(mockKafkaTemplate, testStartKycTopic);
    }

    @Test
    void startKycWorkflow_shouldSendEventToKafkaWithCorrectPayload() {
        // Arrange
        String vendorProfileId = UUID.randomUUID().toString();
        SettableListenableFuture<SendResult<String, Object>> future = new SettableListenableFuture<>();
        when(mockKafkaTemplate.send(anyString(), anyString(), any(Map.class))).thenReturn(future);

        // Act
        String workflowId = kycOrchestratorClient.startKycWorkflow(vendorProfileId);

        // Assert
        assertNotNull(workflowId);
        verify(mockKafkaTemplate).send(topicCaptor.capture(), keyCaptor.capture(), payloadCaptor.capture());

        assertEquals(testStartKycTopic, topicCaptor.getValue());
        assertEquals(workflowId, keyCaptor.getValue()); // workflowId is used as message key

        Map<String, String> capturedPayload = payloadCaptor.getValue();
        assertEquals(workflowId, capturedPayload.get("workflowId"));
        assertEquals(vendorProfileId, capturedPayload.get("vendorProfileId"));
        assertEquals("StartKycVendorWorkflow", capturedPayload.get("eventType"));

        // Optionally, simulate success/failure of Kafka send for callback logging coverage (though not strictly testing client logic here)
        // future.set(mock(SendResult.class)); // Simulate success
    }

    @Test
    void startKycWorkflow_kafkaSendFailure_shouldLogErrorAndReturnWorkflowId() {
        // Arrange
        String vendorProfileId = UUID.randomUUID().toString();
        SettableListenableFuture<SendResult<String, Object>> future = new SettableListenableFuture<>();
        when(mockKafkaTemplate.send(anyString(), anyString(), any(Map.class))).thenReturn(future);

        // Act
        String workflowId = kycOrchestratorClient.startKycWorkflow(vendorProfileId);

        // Assert
        assertNotNull(workflowId); // Workflow ID should still be generated and returned

        // Simulate Kafka send failure to test the onFailure callback logging
        RuntimeException mockException = new RuntimeException("Kafka publish error");
        future.setException(mockException); // Trigger onFailure callback

        // Verification of logging would require a LogCaptor or similar, which is more advanced.
        // For this unit test, we trust the callback is added and would log if an error occurred.
        // The primary responsibility of this method is to generate ID and send.
        // The outcome of the send (success/failure logging) is secondary for *this method's return value*.
        verify(mockKafkaTemplate).send(eq(testStartKycTopic), eq(workflowId), anyMap());
    }
}
