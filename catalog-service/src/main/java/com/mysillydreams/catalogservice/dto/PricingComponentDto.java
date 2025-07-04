package com.mysillydreams.catalogservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

// This DTO mirrors the one in pricing-engine
// Used as part of PriceUpdatedEvent
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PricingComponentDto {
    private String componentName; // e.g., "BASE_PRICE", "MANUAL_OVERRIDE", "DEMAND_SURGE_ADJUSTMENT"
    private BigDecimal value;     // The price or adjustment amount for this component
    private String ruleId;        // Optional: ID of the rule that generated this component (if applicable)
    private String description;   // Optional: Further details
}
