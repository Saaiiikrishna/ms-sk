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
public class CreateDynamicPricingRuleRequest {

    @NotNull(message = "Item ID cannot be null")
    private UUID itemId;

    @NotBlank(message = "Rule type cannot be blank")
    @Size(max = 100, message = "Rule type cannot exceed 100 characters")
    private String ruleType;

    @NotNull(message = "Parameters cannot be null")
    // Basic validation for map not being null. Specific parameter validation
    // would happen in service layer based on ruleType.
    private Map<String, Object> parameters;

    @Builder.Default
    private boolean enabled = true;

    // createdBy will be set by the service based on authenticated principal or system context
}
