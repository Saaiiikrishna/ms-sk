package com.mysillydreams.orderapi.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderCreatedEvent {
  private UUID orderId;
  private UUID customerId;
  private List<LineItemDto> items;
  private BigDecimal totalAmount;
  private String currency;
  private Instant createdAt;
}
