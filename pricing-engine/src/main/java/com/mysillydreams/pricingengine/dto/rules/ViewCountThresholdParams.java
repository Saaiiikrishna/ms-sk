package com.mysillydreams.pricingengine.dto.rules;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// Using a specific package for rule parameters DTOs
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ViewCountThresholdParams {
    private Long threshold; // Using Long for counts
    private Double adjustmentPercentage; // e.g., 0.10 for +10%, -0.05 for -5%
}
