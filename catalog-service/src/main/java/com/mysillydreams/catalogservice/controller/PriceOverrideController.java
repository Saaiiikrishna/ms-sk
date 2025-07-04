package com.mysillydreams.catalogservice.controller;

import com.mysillydreams.catalogservice.dto.CreatePriceOverrideRequest;
import com.mysillydreams.catalogservice.dto.PriceOverrideDto;
import com.mysillydreams.catalogservice.dto.UpdatePriceOverrideRequest;
import com.mysillydreams.catalogservice.service.PriceOverrideService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/pricing/overrides")
@RequiredArgsConstructor
@Tag(name = "Price Overrides", description = "APIs for managing manual price overrides")
public class PriceOverrideController {

    private final PriceOverrideService overrideService;

    // Helper to get username
    private String getUserIdFromPrincipal(Principal principal) {
        return (principal != null) ? principal.getName() : "SYSTEM_UNKNOWN_USER";
    }

    // Helper to get user roles (simplified)
    private String getUserRolesFromPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getAuthorities() != null) {
            return authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.joining(","));
        }
        return "ROLE_SYSTEM"; // Default or unknown
    }


    @PostMapping
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_PRICING_MANAGER')") // Example role
    @Operation(summary = "Create a new price override")
    public ResponseEntity<PriceOverrideDto> createOverride(
            @Valid @RequestBody CreatePriceOverrideRequest request, Principal principal) {
        String userId = getUserIdFromPrincipal(principal);
        String userRoles = getUserRolesFromPrincipal(); // This gets "ROLE_ADMIN", etc.
        PriceOverrideDto createdOverride = overrideService.createOverride(request, userId, userRoles);
        return new ResponseEntity<>(createdOverride, HttpStatus.CREATED);
    }

    @GetMapping("/{overrideId}")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_PRICING_MANAGER', 'ROLE_CATALOG_VIEWER')")
    @Operation(summary = "Get a price override by ID")
    public ResponseEntity<PriceOverrideDto> getOverrideById(@PathVariable UUID overrideId) {
        return ResponseEntity.ok(overrideService.getOverrideById(overrideId));
    }

    @GetMapping("/item/{itemId}")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_PRICING_MANAGER', 'ROLE_CATALOG_VIEWER')")
    @Operation(summary = "List all price overrides for a specific item")
    public ResponseEntity<List<PriceOverrideDto>> getOverridesByItemId(@PathVariable UUID itemId) {
        return ResponseEntity.ok(overrideService.findOverridesByItemId(itemId));
    }

    @GetMapping("/item/{itemId}/active")
    @PreAuthorize("isAuthenticated()") // Any authenticated user might need to see active overrides for an item.
    @Operation(summary = "List active price overrides for a specific item")
    public ResponseEntity<List<PriceOverrideDto>> getActiveOverridesByItemId(@PathVariable UUID itemId) {
        return ResponseEntity.ok(overrideService.findActiveOverridesForItem(itemId));
    }


    @PutMapping("/{overrideId}")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_PRICING_MANAGER')")
    @Operation(summary = "Update an existing price override")
    public ResponseEntity<PriceOverrideDto> updateOverride(
            @PathVariable UUID overrideId,
            @Valid @RequestBody UpdatePriceOverrideRequest request, Principal principal) {
        String userId = getUserIdFromPrincipal(principal);
        String userRoles = getUserRolesFromPrincipal();
        PriceOverrideDto updatedOverride = overrideService.updateOverride(overrideId, request, userId, userRoles);
        return ResponseEntity.ok(updatedOverride);
    }

    @DeleteMapping("/{overrideId}")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_PRICING_MANAGER')")
    @Operation(summary = "Delete a price override")
    public ResponseEntity<Void> deleteOverride(@PathVariable UUID overrideId) {
        overrideService.deleteOverride(overrideId);
        return ResponseEntity.noContent().build();
    }
}
