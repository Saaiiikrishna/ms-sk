package com.mysillydreams.orderapi.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LineItemDto {
  private UUID productId;
  private int quantity;
  private BigDecimal price; // Price per unit
}
