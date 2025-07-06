package com.mysillydreams.orderapi.service;

// Import existing DTOs for request and internal use
import com.mysillydreams.orderapi.dto.CreateOrderRequest;
import com.mysillydreams.orderapi.dto.LineItemDto;

// Import Avro generated classes for Kafka events
import com.mysillydreams.orderapi.dto.avro.OrderCancelledEvent as AvroOrderCancelledEvent;
import com.mysillydreams.orderapi.dto.avro.OrderCreatedEvent as AvroOrderCreatedEvent;
import com.mysillydreams.orderapi.dto.avro.LineItem as AvroLineItem;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.tracing.annotation.NewSpan;
import io.micrometer.tracing.annotation.SpanTag;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class OrderApiService {

  private static final Logger log = LoggerFactory.getLogger(OrderApiService.class);

  private final KafkaOrderPublisher publisher;
  private final Counter orderCreatedAttemptsCounter;
  private final Counter orderCancelledAttemptsCounter;

  public OrderApiService(KafkaOrderPublisher publisher, MeterRegistry meterRegistry) {
    this.publisher = publisher;
    this.orderCreatedAttemptsCounter = Counter.builder("order.service.created.attempts")
        .description("Number of times order creation is attempted")
        .register(meterRegistry);
    this.orderCancelledAttemptsCounter = Counter.builder("order.service.cancelled.attempts")
        .description("Number of times order cancellation is attempted")
        .register(meterRegistry);
  }

  @NewSpan("orderService.createOrder")
  public UUID createOrder(@SpanTag("create.request") CreateOrderRequest req, @SpanTag(key = "idempotency.key", expression = "idempotencyKey") String idempotencyKey) {
    orderCreatedAttemptsCounter.increment();

    UUID orderId = UUID.randomUUID();
    // Assuming customerId and idempotencyKey are already in MDC from controller/filter
    // If not, or for service-specific logging:
    // MDC.put("customerId", req.getCustomerId().toString()); // Already set in controller
    MDC.put("orderId", orderId.toString());

    BigDecimal totalAmount = calculateTotal(req.getItems());

    // Map DTO LineItems to Avro LineItems
    List<AvroLineItem> avroItems = req.getItems().stream()
        .map(dtoItem -> AvroLineItem.newBuilder()
            .setProductId(dtoItem.getProductId().toString()) // Assuming productId in Avro is String
            .setQuantity(dtoItem.getQuantity())
            .setPrice(dtoItem.getPrice().doubleValue()) // Assuming price in Avro is double
            .build())
        .collect(Collectors.toList());

    // Create Avro OrderCreatedEvent
    AvroOrderCreatedEvent avroEvent = AvroOrderCreatedEvent.newBuilder()
        .setOrderId(orderId.toString())
        .setCustomerId(req.getCustomerId().toString())
        .setItems(avroItems)
        .setTotalAmount(totalAmount.doubleValue()) // Assuming totalAmount in Avro is double
        .setCurrency(req.getCurrency())
        .setCreatedAt(Instant.now().toEpochMilli())
        .build();

    publisher.publishOrderCreated(avroEvent); // Pass Avro event to publisher
    log.info("Order creation process initiated"); // orderId, customerId, idempotencyKey from MDC

    // Clean up MDC specific to this method call if it was set here.
    // OrderId is added by controller after this method returns, so it's fine.
    // However, if this service method was the one setting it primarily, it should clean it.
    // MDC.remove("orderId"); // Controller usually handles this for the request scope.
    return orderId;
  }

  @NewSpan("orderService.cancelOrder")
  public void cancelOrder(@SpanTag("order.id") UUID orderId, @SpanTag("cancel.reason") String reason) {
    orderCancelledAttemptsCounter.increment();
    // Assuming orderId and customerId (if applicable) are in MDC from controller
    // MDC.put("orderId", orderId.toString()); // Already set in controller for this request

    AvroOrderCancelledEvent avroEvent = AvroOrderCancelledEvent.newBuilder()
        .setOrderId(orderId.toString())
        .setReason(reason)
        .setCancelledAt(Instant.now().toEpochMilli())
        .build();

    publisher.publishOrderCancelled(avroEvent); // Pass Avro event to publisher
    log.info("Order cancellation process initiated"); // orderId from MDC
  }

  // Helper method to calculate total amount from line items
  // This method still works with List<LineItemDto> from the CreateOrderRequest
  // No @NewSpan needed for private helper methods unless they are complex and warrant a separate span
  private BigDecimal calculateTotal(List<LineItemDto> items) {
    if (items == null || items.isEmpty()) {
      return BigDecimal.ZERO;
    }
    return items.stream()
        .map(item -> item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }
}
