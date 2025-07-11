package com.mysillydreams.orderapi.service;

// Import Avro generated classes
import com.mysillydreams.orderapi.dto.avro.OrderCancelledEvent as AvroOrderCancelledEvent;
import com.mysillydreams.orderapi.dto.avro.OrderCreatedEvent as AvroOrderCreatedEvent;
import io.micrometer.tracing.annotation.NewSpan;
import io.micrometer.tracing.annotation.SpanTag;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.ListenableFutureCallback;

@Service
@RequiredArgsConstructor
public class KafkaOrderPublisher {

  private static final Logger log = LoggerFactory.getLogger(KafkaOrderPublisher.class);

  private final KafkaTemplate<String, Object> kafkaTemplate;

  @Value("${kafka.topics.orderCreated}")
  private String orderCreatedTopic;
  @Value("${kafka.topics.orderCreatedDlq}")
  private String orderCreatedDlqTopic;

  @Value("${kafka.topics.orderCancelled}")
  private String orderCancelledTopic;
  @Value("${kafka.topics.orderCancelledDlq}")
  private String orderCancelledDlqTopic;

  @NewSpan("kafka.publisher.orderCreated")
  public void publishOrderCreated(@SpanTag("event.orderId") AvroOrderCreatedEvent avroEvent) { // Changed to Avro type
    // The eventId for Avro is already a String as per schema (orderId: "string")
    String eventId = avroEvent.getOrderId();
    log.info("Publishing Avro OrderCreatedEvent for orderId: {} to topic: {}", eventId, orderCreatedTopic);

    ListenableFuture<SendResult<String, Object>> future =
        kafkaTemplate.send(orderCreatedTopic, eventId, avroEvent); // Send Avro object

    future.addCallback(
      successResult -> {
        log.info("Successfully sent Avro OrderCreatedEvent for orderId: {} with offset: {}",
            eventId, successResult.getRecordMetadata().offset());
      },
      failureException -> {
        log.error("Failed to send Avro OrderCreatedEvent for orderId: {} to topic {}. Error: {}. Sending to DLQ topic {}.",
            eventId, orderCreatedTopic, failureException.getMessage(), orderCreatedDlqTopic, failureException);
        // Send to DLQ
        kafkaTemplate.send(orderCreatedDlqTopic, eventId, avroEvent) // Send Avro object to DLQ
          .addCallback(dlqSuccess -> log.info("Successfully sent Avro OrderCreatedEvent for orderId: {} to DLQ topic {} with offset: {}",
                                                eventId, orderCreatedDlqTopic, dlqSuccess.getRecordMetadata().offset()),
                       dlqFailure -> log.error("Failed to send Avro OrderCreatedEvent for orderId: {} to DLQ topic {}. Error: {}",
                                                eventId, orderCreatedDlqTopic, dlqFailure.getMessage(), dlqFailure)
          );
      }
    );
  }

  @NewSpan("kafka.publisher.orderCancelled")
  public void publishOrderCancelled(@SpanTag("event.orderId") AvroOrderCancelledEvent avroEvent) { // Changed to Avro type
    String eventId = avroEvent.getOrderId();
    log.info("Publishing Avro OrderCancelledEvent for orderId: {} to topic: {}", eventId, orderCancelledTopic);

    ListenableFuture<SendResult<String, Object>> future =
        kafkaTemplate.send(orderCancelledTopic, eventId, avroEvent); // Send Avro object

    future.addCallback(
      successResult -> {
        log.info("Successfully sent Avro OrderCancelledEvent for orderId: {} with offset: {}",
            eventId, successResult.getRecordMetadata().offset());
      },
      failureException -> {
        log.error("Failed to send Avro OrderCancelledEvent for orderId: {} to topic {}. Error: {}. Sending to DLQ topic {}.",
            eventId, orderCancelledTopic, failureException.getMessage(), orderCancelledDlqTopic, failureException);
        // Send to DLQ
        kafkaTemplate.send(orderCancelledDlqTopic, eventId, avroEvent) // Send Avro object to DLQ
          .addCallback(dlqSuccess -> log.info("Successfully sent Avro OrderCancelledEvent for orderId: {} to DLQ topic {} with offset: {}",
                                                eventId, orderCancelledDlqTopic, dlqSuccess.getRecordMetadata().offset()),
                       dlqFailure -> log.error("Failed to send Avro OrderCancelledEvent for orderId: {} to DLQ topic {}. Error: {}",
                                                eventId, orderCancelledDlqTopic, dlqFailure.getMessage(), dlqFailure)
          );
      }
    );
  }
}
