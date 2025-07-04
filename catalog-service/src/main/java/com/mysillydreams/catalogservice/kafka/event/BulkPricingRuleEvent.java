package com.mysillydreams.catalogservice.kafka.event;

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
public class BulkPricingRuleEvent {
    private String eventType; // "bulk.pricing.rule.added", ".updated", ".deleted"
    private UUID ruleId;
    private UUID itemId;
    private String itemSku;
    private Integer minQuantity;
    private BigDecimal discountPercentage;
    private Instant validFrom;
    private Instant validTo;
    private boolean active;
    private Instant timestamp; // Event occurrence time
}
