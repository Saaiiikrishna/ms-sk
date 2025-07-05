package com.mysillydreams.orderapi.controller;

import com.mysillydreams.orderapi.dto.CreateOrderRequest;
import com.mysillydreams.orderapi.service.OrderApiService;
import io.micrometer.tracing.annotation.NewSpan;
import io.micrometer.tracing.annotation.SpanTag;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid; // Correct import for @Valid
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

  private static final Logger log = LoggerFactory.getLogger(OrderController.class);
  private final OrderApiService service;

  @PostMapping
  @NewSpan("orderController.create") // Create a new span for this method
  public ResponseEntity<Map<String, UUID>> create(
      @Valid @RequestBody CreateOrderRequest req,
      @RequestHeader("Idempotency-Key") @SpanTag("idempotency.key") String idempotencyKey, // Tag span with idempotency key
      @AuthenticationPrincipal Jwt jwt) {

    UUID customerId = UUID.fromString(jwt.getSubject());
    req.setCustomerId(customerId);

    MDC.put("customerId", customerId.toString());
    // IdempotencyKey is already in MDC from the filter

    log.info("Received create order request for customer: {}", customerId); // IdempotencyKey will be in logs from MDC

    UUID orderId = service.createOrder(req, idempotencyKey);
    MDC.put("orderId", orderId.toString()); // Add orderId to MDC once generated

    log.info("Order creation initiated successfully"); // orderId and IdempotencyKey will be in logs

    MDC.remove("orderId"); // Clean up MDC for this specific key
    MDC.remove("customerId"); // Clean up MDC
    return ResponseEntity.status(HttpStatus.ACCEPTED)
                         .body(Collections.singletonMap("orderId", orderId));
  }

  @PutMapping("/{id}/cancel")
  @NewSpan("orderController.cancel")
  public ResponseEntity<Void> cancel(
      @PathVariable("id") @SpanTag("order.id") UUID orderId, // Tag span with orderId
      @RequestParam @SpanTag("cancel.reason") String reason,
      @AuthenticationPrincipal Jwt jwt) {

    UUID customerId = UUID.fromString(jwt.getSubject());
    MDC.put("customerId", customerId.toString());
    MDC.put("orderId", orderId.toString());

    log.info("Received cancel order request for orderId: {} by customer: {} with reason: {}", orderId, customerId, reason);

    service.cancelOrder(orderId, reason);

    log.info("Order cancel processed for orderId: {}", orderId);
    MDC.remove("orderId");
    MDC.remove("customerId");
    return ResponseEntity.accepted().build();
  }
}
