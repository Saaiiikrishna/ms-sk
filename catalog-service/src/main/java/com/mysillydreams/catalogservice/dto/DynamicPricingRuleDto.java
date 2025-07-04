package com.mysillydreams.catalogservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DynamicPricingRuleDto {
    private UUID id;
    private UUID itemId;
    private String itemSku; // Denormalized for convenience
    private String ruleType;
    private Map<String, Object> parameters;
    private boolean enabled;
    private String createdBy;
    private Instant createdAt;
    private Instant updatedAt;
    private Long version;
}
