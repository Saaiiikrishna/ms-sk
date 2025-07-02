package com.mysillydreams.userservice.service.inventory;

import com.mysillydreams.userservice.domain.inventory.InventoryItem;
import com.mysillydreams.userservice.domain.inventory.InventoryProfile;
import com.mysillydreams.userservice.domain.inventory.StockTransaction;
import com.mysillydreams.userservice.domain.inventory.TransactionType;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryKafkaClientTest {

    @Mock
    private KafkaTemplate<String, Object> mockKafkaTemplate;

    private String testItemCreatedTopic = "test.inventory.item.created";
    private String testStockAdjustedTopic = "test.inventory.stock.adjusted";

    private InventoryKafkaClient inventoryKafkaClient;

    @Captor
    private ArgumentCaptor<String> topicCaptor;
    @Captor
    private ArgumentCaptor<String> keyCaptor;
    @Captor
    private ArgumentCaptor<Map<String, Object>> payloadCaptor;

    private InventoryItem sampleItem;
    private StockTransaction sampleTransaction;
    private InventoryProfile sampleOwnerProfile;

    @BeforeEach
    void setUp() {
        inventoryKafkaClient = new InventoryKafkaClient(
                mockKafkaTemplate,
                testItemCreatedTopic,
                testStockAdjustedTopic
        );

        sampleOwnerProfile = new InventoryProfile(UUID.randomUUID());

        sampleItem = new InventoryItem();
        sampleItem.setId(UUID.randomUUID());
        sampleItem.setSku("SKU123");
        sampleItem.setName("Test Item");
        sampleItem.setOwner(sampleOwnerProfile);
        sampleItem.setQuantityOnHand(100);
        sampleItem.setReorderLevel(10);
        sampleItem.setCreatedAt(Instant.now());

        sampleTransaction = new StockTransaction();
        sampleTransaction.setId(UUID.randomUUID());
        sampleTransaction.setType(TransactionType.RECEIVE);
        sampleTransaction.setQuantity(50);
        sampleTransaction.setTimestamp(Instant.now());
    }

    @Test
    void publishItemCreated_sendsCorrectEvent() {
        SettableListenableFuture<SendResult<String, Object>> future = new SettableListenableFuture<>();
        when(mockKafkaTemplate.send(anyString(), anyString(), anyMap())).thenReturn(future);

        inventoryKafkaClient.publishItemCreated(sampleItem);

        verify(mockKafkaTemplate).send(topicCaptor.capture(), keyCaptor.capture(), payloadCaptor.capture());

        assertEquals(testItemCreatedTopic, topicCaptor.getValue());
        assertEquals(sampleItem.getId().toString(), keyCaptor.getValue());

        Map<String, Object> payload = payloadCaptor.getValue();
        assertEquals(sampleItem.getId().toString(), payload.get("itemId"));
        assertEquals(sampleItem.getSku(), payload.get("sku"));
        assertEquals(sampleItem.getName(), payload.get("name"));
        assertEquals(sampleItem.getOwner().getId().toString(), payload.get("inventoryProfileId"));
        assertEquals(sampleItem.getQuantityOnHand(), payload.get("quantityOnHand"));
        assertEquals(sampleItem.getReorderLevel(), payload.get("reorderLevel"));
        assertEquals(sampleItem.getCreatedAt().toString(), payload.get("createdAt"));
        assertEquals("InventoryItemCreated", payload.get("eventType"));
    }

    @Test
    void publishItemCreated_nullItem_doesNotSend() {
        inventoryKafkaClient.publishItemCreated(null);
        verifyNoInteractions(mockKafkaTemplate);
    }

    @Test
    void publishItemCreated_itemWithNullId_doesNotSend() {
        sampleItem.setId(null);
        inventoryKafkaClient.publishItemCreated(sampleItem);
        verifyNoInteractions(mockKafkaTemplate);
    }


    @Test
    void publishStockAdjusted_sendsCorrectEvent() {
        SettableListenableFuture<SendResult<String, Object>> future = new SettableListenableFuture<>();
        when(mockKafkaTemplate.send(anyString(), anyString(), anyMap())).thenReturn(future);

        inventoryKafkaClient.publishStockAdjusted(sampleItem, sampleTransaction);

        verify(mockKafkaTemplate).send(topicCaptor.capture(), keyCaptor.capture(), payloadCaptor.capture());

        assertEquals(testStockAdjustedTopic, topicCaptor.getValue());
        assertEquals(sampleItem.getId().toString(), keyCaptor.getValue());

        Map<String, Object> payload = payloadCaptor.getValue();
        assertEquals(sampleItem.getId().toString(), payload.get("itemId"));
        assertEquals(sampleItem.getSku(), payload.get("sku"));
        assertEquals(sampleTransaction.getId().toString(), payload.get("transactionId"));
        assertEquals(sampleTransaction.getType().toString(), payload.get("transactionType"));
        assertEquals(sampleTransaction.getQuantity(), payload.get("quantityAdjusted"));
        assertEquals(sampleItem.getQuantityOnHand(), payload.get("newQuantityOnHand"));
        assertEquals(sampleTransaction.getTimestamp().toString(), payload.get("transactionTimestamp"));
        assertEquals(sampleItem.getOwner().getId().toString(), payload.get("inventoryProfileId"));
        assertEquals("InventoryStockAdjusted", payload.get("eventType"));
    }

    @Test
    void publishStockAdjusted_nullItem_doesNotSend() {
        inventoryKafkaClient.publishStockAdjusted(null, sampleTransaction);
        verifyNoInteractions(mockKafkaTemplate);
    }

    @Test
    void publishStockAdjusted_nullTransaction_doesNotSend() {
        inventoryKafkaClient.publishStockAdjusted(sampleItem, null);
        verifyNoInteractions(mockKafkaTemplate);
    }

    @Test
    void publishStockAdjusted_itemWithNullId_doesNotSend() {
        sampleItem.setId(null);
        inventoryKafkaClient.publishStockAdjusted(sampleItem, sampleTransaction);
        verifyNoInteractions(mockKafkaTemplate);
    }

    @Test
    void publishStockAdjusted_transactionWithNullId_doesNotSend() {
        sampleTransaction.setId(null);
        inventoryKafkaClient.publishStockAdjusted(sampleItem, sampleTransaction);
        verifyNoInteractions(mockKafkaTemplate);
    }


    // Test Kafka callback logging (success and failure)
    @Test
    void kafkaCallback_onSuccess_logsSuccess() {
        SettableListenableFuture<SendResult<String, Object>> future = new SettableListenableFuture<>();
        when(mockKafkaTemplate.send(anyString(), anyString(), anyMap())).thenReturn(future);

        inventoryKafkaClient.publishItemCreated(sampleItem); // Trigger a send to get the callback attached

        // Simulate successful send
        SendResult<String, Object> mockSendResult = mock(SendResult.class, RETURNS_DEEP_STUBS);
        when(mockSendResult.getRecordMetadata().topic()).thenReturn(testItemCreatedTopic);
        when(mockSendResult.getRecordMetadata().partition()).thenReturn(0);
        when(mockSendResult.getRecordMetadata().offset()).thenReturn(123L);
        future.set(mockSendResult);

        // Assertions on logging would require a LogCaptor. For now, this test ensures the callback path is callable.
        // No direct assertion on logs here, but visual inspection or LogCaptor in a real environment.
    }

    @Test
    void kafkaCallback_onFailure_logsError() {
        SettableListenableFuture<SendResult<String, Object>> future = new SettableListenableFuture<>();
        when(mockKafkaTemplate.send(anyString(), anyString(), anyMap())).thenReturn(future);

        inventoryKafkaClient.publishStockAdjusted(sampleItem, sampleTransaction); // Trigger another send

        // Simulate failed send
        RuntimeException testException = new RuntimeException("Kafka send failed!");
        future.setException(testException);

        // Assertions on logging would require a LogCaptor.
    }
}
