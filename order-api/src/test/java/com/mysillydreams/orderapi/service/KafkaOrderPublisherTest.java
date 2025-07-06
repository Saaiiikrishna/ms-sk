package com.mysillydreams.orderapi.service;

// Import Avro generated classes for events
import com.mysillydreams.orderapi.dto.avro.OrderCancelledEvent as AvroOrderCancelledEvent;
import com.mysillydreams.orderapi.dto.avro.OrderCreatedEvent as AvroOrderCreatedEvent;
import com.mysillydreams.orderapi.dto.avro.LineItem as AvroLineItem;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.SettableListenableFuture;

// BigDecimal is not used by Avro events directly in this test, but Instant is for time.
import java.time.Instant;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KafkaOrderPublisherTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private KafkaOrderPublisher kafkaOrderPublisher;

    @Captor
    private ArgumentCaptor<String> topicCaptor;
    @Captor
    private ArgumentCaptor<String> keyCaptor;
    @Captor
    private ArgumentCaptor<Object> eventCaptor;

    private final String orderCreatedTopic = "test.order.created";
    private final String orderCreatedDlqTopic = "test.order.created.dlq";
    private final String orderCancelledTopic = "test.order.cancelled";
    private final String orderCancelledDlqTopic = "test.order.cancelled.dlq";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(kafkaOrderPublisher, "orderCreatedTopic", orderCreatedTopic);
        ReflectionTestUtils.setField(kafkaOrderPublisher, "orderCreatedDlqTopic", orderCreatedDlqTopic);
        ReflectionTestUtils.setField(kafkaOrderPublisher, "orderCancelledTopic", orderCancelledTopic);
        ReflectionTestUtils.setField(kafkaOrderPublisher, "orderCancelledDlqTopic", orderCancelledDlqTopic);
    }

    private ListenableFuture<SendResult<String, Object>> mockKafkaSend(boolean succeed) {
        SettableListenableFuture<SendResult<String, Object>> future = new SettableListenableFuture<>();
        if (succeed) {
            ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("topic", "key", "value"); // Example values
            RecordMetadata recordMetadata = new RecordMetadata(new TopicPartition("topic", 0), 0, 0, Instant.now().toEpochMilli(), (long) 0, 0, 0);
            SendResult<String, Object> sendResult = new SendResult<>(producerRecord, recordMetadata);
            future.set(sendResult);
        } else {
            future.setException(new RuntimeException("Simulated Kafka send failure"));
        }
        return future;
    }

    @Test
    void publishOrderCreated_success_sendsToPrimaryTopic() {
        AvroOrderCreatedEvent avroEvent = AvroOrderCreatedEvent.newBuilder()
            .setOrderId(UUID.randomUUID().toString())
            .setCustomerId(UUID.randomUUID().toString())
            .setItems(Collections.emptyList()) // Assuming Avro LineItem list
            .setTotalAmount(10.0)
            .setCurrency("USD")
            .setCreatedAt(Instant.now().toEpochMilli())
            .build();

        // Expecting AvroOrderCreatedEvent type in send method
        when(kafkaTemplate.send(eq(orderCreatedTopic), anyString(), any(AvroOrderCreatedEvent.class)))
            .thenReturn(mockKafkaSend(true));

        kafkaOrderPublisher.publishOrderCreated(avroEvent);

        verify(kafkaTemplate).send(topicCaptor.capture(), keyCaptor.capture(), eventCaptor.capture());
        assertEquals(orderCreatedTopic, topicCaptor.getValue());
        assertEquals(avroEvent.getOrderId(), keyCaptor.getValue()); // Avro event uses String for orderId
        assertEquals(avroEvent, eventCaptor.getValue());
        verify(kafkaTemplate, never()).send(eq(orderCreatedDlqTopic), anyString(), any());
    }

    @Test
    void publishOrderCreated_failure_sendsToDlqTopic() {
        AvroOrderCreatedEvent avroEvent = AvroOrderCreatedEvent.newBuilder()
            .setOrderId(UUID.randomUUID().toString())
            .setCustomerId(UUID.randomUUID().toString())
            .setItems(Collections.emptyList())
            .setTotalAmount(10.0)
            .setCurrency("USD")
            .setCreatedAt(Instant.now().toEpochMilli())
            .build();

        when(kafkaTemplate.send(eq(orderCreatedTopic), eq(avroEvent.getOrderId()), eq(avroEvent)))
            .thenReturn(mockKafkaSend(false));

        when(kafkaTemplate.send(eq(orderCreatedDlqTopic), eq(avroEvent.getOrderId()), eq(avroEvent)))
            .thenReturn(mockKafkaSend(true));

        kafkaOrderPublisher.publishOrderCreated(avroEvent);

        verify(kafkaTemplate, times(1)).send(eq(orderCreatedTopic), eq(avroEvent.getOrderId()), eq(avroEvent));
        verify(kafkaTemplate, times(1)).send(eq(orderCreatedDlqTopic), eq(avroEvent.getOrderId()), eq(avroEvent));
    }

    @Test
    void publishOrderCancelled_success_sendsToPrimaryTopic() {
        AvroOrderCancelledEvent avroEvent = AvroOrderCancelledEvent.newBuilder()
            .setOrderId(UUID.randomUUID().toString())
            .setReason("Test reason")
            .setCancelledAt(Instant.now().toEpochMilli())
            .build();

        // Expecting AvroOrderCancelledEvent type
        when(kafkaTemplate.send(eq(orderCancelledTopic), anyString(), any(AvroOrderCancelledEvent.class)))
            .thenReturn(mockKafkaSend(true));

        kafkaOrderPublisher.publishOrderCancelled(avroEvent);

        verify(kafkaTemplate).send(topicCaptor.capture(), keyCaptor.capture(), eventCaptor.capture());
        assertEquals(orderCancelledTopic, topicCaptor.getValue());
        assertEquals(avroEvent.getOrderId(), keyCaptor.getValue());
        assertEquals(avroEvent, eventCaptor.getValue());
        verify(kafkaTemplate, never()).send(eq(orderCancelledDlqTopic), anyString(), any());
    }

    @Test
    void publishOrderCancelled_failure_sendsToDlqTopic() {
         AvroOrderCancelledEvent avroEvent = AvroOrderCancelledEvent.newBuilder()
            .setOrderId(UUID.randomUUID().toString())
            .setReason("Test reason")
            .setCancelledAt(Instant.now().toEpochMilli())
            .build();

        when(kafkaTemplate.send(eq(orderCancelledTopic), eq(avroEvent.getOrderId()), eq(avroEvent)))
            .thenReturn(mockKafkaSend(false));
        when(kafkaTemplate.send(eq(orderCancelledDlqTopic), eq(avroEvent.getOrderId()), eq(avroEvent)))
            .thenReturn(mockKafkaSend(true));

        kafkaOrderPublisher.publishOrderCancelled(avroEvent);

        verify(kafkaTemplate, times(1)).send(eq(orderCancelledTopic), eq(avroEvent.getOrderId()), eq(avroEvent));
        verify(kafkaTemplate, times(1)).send(eq(orderCancelledDlqTopic), eq(avroEvent.getOrderId()), eq(avroEvent));
    }
}
