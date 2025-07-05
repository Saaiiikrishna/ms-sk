package com.mysillydreams.orderapi.service;

import com.mysillydreams.orderapi.dto.OrderCancelledEvent;
import com.mysillydreams.orderapi.dto.OrderCreatedEvent;
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

  @Value("${kafka.topics.orderCancelled}")
  private String orderCancelledTopic;

  @NewSpan("kafka.publisher.orderCreated")
  public void publishOrderCreated(@SpanTag("event.orderId") OrderCreatedEvent event) {
    // MDC values (orderId, customerId, idempotencyKey) should propagate from the calling service/controller context
    // or be explicitly set if this is a new root span context for some reason.
    // For logging within this method, ensure critical IDs are present.
    // If orderId is not in MDC yet, this is a good place to ensure it is for the log lines here.
    // String orderIdStr = event.getOrderId().toString();
    // MDC.put("kafkaTopic", orderCreatedTopic); // Add topic to MDC for these logs

    log.info("Publishing OrderCreatedEvent to topic: {}", orderCreatedTopic); // orderId, etc., from MDC
    ListenableFuture<SendResult<String, Object>> future =
        kafkaTemplate.send(orderCreatedTopic, event.getOrderId().toString(), event);

    future.addCallback(new ListenableFutureCallback<SendResult<String, Object>>() {
      @Override
      public void onSuccess(SendResult<String, Object> result) {
        log.info("Successfully sent OrderCreatedEvent with offset: {}",
            result.getRecordMetadata().offset()); // orderId from MDC
      }

      @Override
      public void onFailure(Throwable ex) {
        log.error("Failed to send OrderCreatedEvent due to: {}", ex.getMessage(), ex); // orderId from MDC
        // Consider retry mechanisms or dead-letter queue (DLQ) handling here
      }
    });
    // MDC.remove("kafkaTopic");
  }

  @NewSpan("kafka.publisher.orderCancelled")
  public void publishOrderCancelled(@SpanTag("event.orderId") OrderCancelledEvent event) {
    // MDC.put("kafkaTopic", orderCancelledTopic);
    log.info("Publishing OrderCancelledEvent to topic: {}", orderCancelledTopic); // orderId from MDC
    ListenableFuture<SendResult<String, Object>> future =
        kafkaTemplate.send(orderCancelledTopic, event.getOrderId().toString(), event);

    future.addCallback(new ListenableFutureCallback<SendResult<String, Object>>() {
      @Override
      public void onSuccess(SendResult<String, Object> result) {
        log.info("Successfully sent OrderCancelledEvent with offset: {}",
             result.getRecordMetadata().offset()); // orderId from MDC
      }

      @Override
      public void onFailure(Throwable ex) {
        log.error("Failed to send OrderCancelledEvent due to: {}", ex.getMessage(), ex); // orderId from MDC
        // Consider retry mechanisms or DLQ handling here
      }
    });
    // MDC.remove("kafkaTopic");
  }
}
