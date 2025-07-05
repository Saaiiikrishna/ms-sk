package com.mysillydreams.orderapi.service;

import com.mysillydreams.orderapi.dto.CreateOrderRequest;
import com.mysillydreams.orderapi.dto.LineItemDto;
import com.mysillydreams.orderapi.dto.OrderCancelledEvent;
import com.mysillydreams.orderapi.dto.OrderCreatedEvent;
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

    OrderCreatedEvent event = new OrderCreatedEvent(
        orderId,
        req.getCustomerId(),
        req.getItems(),
        totalAmount,
        req.getCurrency(),
        Instant.now()
    );

    publisher.publishOrderCreated(event); // publishOrderCreated will have its own span
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

    OrderCancelledEvent event = new OrderCancelledEvent(
        orderId,
        reason,
        Instant.now()
    );

    publisher.publishOrderCancelled(event); // publishOrderCancelled will have its own span
    log.info("Order cancellation process initiated"); // orderId from MDC
  }

  // Helper method to calculate total amount from line items
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
