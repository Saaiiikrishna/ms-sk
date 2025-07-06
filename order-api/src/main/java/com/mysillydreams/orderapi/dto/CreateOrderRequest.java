package com.mysillydreams.orderapi.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateOrderRequest {
  // customerId will be set from JWT in the controller
  private UUID customerId;

  @NotNull
  @NotEmpty
  @Valid
  private List<LineItemDto> items;

  @NotNull
  @Size(min = 3, max = 3) // ISO 4217 currency code
  private String currency;
}
