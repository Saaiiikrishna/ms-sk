package com.mysillydreams.catalogservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateDynamicPricingRuleRequest {

    // itemId might not be updatable, service should handle this.
    // For now, including it, assuming service might allow repointing or just validates it matches.
    @NotNull(message = "Item ID cannot be null")
    private UUID itemId;

    @NotBlank(message = "Rule type cannot be blank")
    @Size(max = 100, message = "Rule type cannot exceed 100 characters")
    private String ruleType; // Rule type might also be non-updatable

    @NotNull(message = "Parameters cannot be null")
    private Map<String, Object> parameters;

    @NotNull(message = "Enabled flag must be provided")
    private Boolean enabled;

    // updatedBy will be set by the service
}
