package com.mysillydreams.catalogservice.controller;

import com.mysillydreams.catalogservice.dto.CreateDynamicPricingRuleRequest;
import com.mysillydreams.catalogservice.dto.DynamicPricingRuleDto;
import com.mysillydreams.catalogservice.dto.UpdateDynamicPricingRuleRequest;
import com.mysillydreams.catalogservice.service.DynamicPricingRuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
// import org.springframework.security.oauth2.jwt.Jwt; // If using JWT principal directly
import java.security.Principal; // Simpler way to get username
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/pricing/dynamic-rules")
@RequiredArgsConstructor
@Tag(name = "Dynamic Pricing Rules", description = "APIs for managing dynamic pricing rules")
public class DynamicPricingRuleController {

    private final DynamicPricingRuleService ruleService;

    // Helper to get username (or a system identifier if principal is null for non-user actions)
    private String getActor(Principal principal) {
        return (principal != null) ? principal.getName() : "SYSTEM";
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_PRICING_MANAGER')") // Example role
    @Operation(summary = "Create a new dynamic pricing rule")
    public ResponseEntity<DynamicPricingRuleDto> createRule(
            @Valid @RequestBody CreateDynamicPricingRuleRequest request, Principal principal) {
        DynamicPricingRuleDto createdRule = ruleService.createRule(request, getActor(principal));
        return new ResponseEntity<>(createdRule, HttpStatus.CREATED);
    }

    @GetMapping("/{ruleId}")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_PRICING_MANAGER', 'ROLE_CATALOG_VIEWER')") // Example roles
    @Operation(summary = "Get a dynamic pricing rule by ID")
    public ResponseEntity<DynamicPricingRuleDto> getRuleById(@PathVariable UUID ruleId) {
        return ResponseEntity.ok(ruleService.getRuleById(ruleId));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_PRICING_MANAGER', 'ROLE_CATALOG_VIEWER')")
    @Operation(summary = "List all dynamic pricing rules")
    public ResponseEntity<List<DynamicPricingRuleDto>> getAllRules() {
        return ResponseEntity.ok(ruleService.findAllRules());
    }

    @GetMapping("/item/{itemId}")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_PRICING_MANAGER', 'ROLE_CATALOG_VIEWER')")
    @Operation(summary = "List dynamic pricing rules for a specific item")
    public ResponseEntity<List<DynamicPricingRuleDto>> getRulesByItemId(@PathVariable UUID itemId) {
        return ResponseEntity.ok(ruleService.findRulesByItemId(itemId));
    }

    @PutMapping("/{ruleId}")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_PRICING_MANAGER')")
    @Operation(summary = "Update an existing dynamic pricing rule")
    public ResponseEntity<DynamicPricingRuleDto> updateRule(
            @PathVariable UUID ruleId,
            @Valid @RequestBody UpdateDynamicPricingRuleRequest request, Principal principal) {
        DynamicPricingRuleDto updatedRule = ruleService.updateRule(ruleId, request, getActor(principal));
        return ResponseEntity.ok(updatedRule);
    }

    @DeleteMapping("/{ruleId}")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_PRICING_MANAGER')")
    @Operation(summary = "Delete a dynamic pricing rule")
    public ResponseEntity<Void> deleteRule(@PathVariable UUID ruleId) {
        ruleService.deleteRule(ruleId);
        return ResponseEntity.noContent().build();
    }
}
