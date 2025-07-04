package com.mysillydreams.pricingengine.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

// This DTO is a direct copy from catalog-service for event deserialization
// It represents the structure of the event payload for dynamic pricing rules.
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DynamicPricingRuleDto {
    private UUID id;
    private UUID itemId;
    private String itemSku; // Denormalized for convenience, may or may not be used by pricing-engine
    private String ruleType;
    private Map<String, Object> parameters;
    private boolean enabled;
    private String createdBy;
    private Instant createdAt;
    private Instant updatedAt;
    private Long version;
}
