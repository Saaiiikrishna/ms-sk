package com.mysillydreams.catalogservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PriceOverrideDto {
    private UUID id;
    private UUID itemId;
    private String itemSku; // Denormalized
    private BigDecimal overridePrice;
    private Instant startTime;
    private Instant endTime;
    private boolean enabled;
    private String createdByUserId;
    private String createdByRole;
    private Instant createdAt;
    private Instant updatedAt;
    private Long version;
}
