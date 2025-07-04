package com.mysillydreams.pricingengine.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PricingComponent {
    private String componentName; // e.g., "BASE_PRICE", "MANUAL_OVERRIDE", "DEMAND_SURGE_ADJUSTMENT", "LOW_STOCK_UPLIFT"
    private BigDecimal value;     // The price or adjustment amount for this component
    private String ruleId;        // Optional: ID of the rule that generated this component
    private String description;   // Optional: Further details
}
