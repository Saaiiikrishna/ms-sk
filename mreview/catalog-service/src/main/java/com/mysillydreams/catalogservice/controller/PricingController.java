package com.mysillydreams.catalogservice.controller;

import com.mysillydreams.catalogservice.domain.model.PriceHistoryEntity; // For Price History specific DTO if any, or directly use entity for simple cases
import com.mysillydreams.catalogservice.dto.BulkPricingRuleDto;
import com.mysillydreams.catalogservice.dto.CreateBulkPricingRuleRequest;
import com.mysillydreams.catalogservice.dto.PriceDetailDto; // For getting calculated price
import com.mysillydreams.catalogservice.service.ItemService; // For price history (if managed there)
import com.mysillydreams.catalogservice.service.PricingService;
import com.mysillydreams.catalogservice.domain.repository.PriceHistoryRepository; // Temp direct use

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/pricing")
@RequiredArgsConstructor
@Tag(name = "Pricing Management", description = "APIs for managing item prices, price history, and bulk pricing rules")
public class PricingController {

    private final PricingService pricingService;
    private final ItemService itemService; // For price history, though it's better if PricingService handles this.
                                         // For now, ItemService has updateItemPrice which creates history.
                                         // Let's add a direct way to get history.
    private final PriceHistoryRepository priceHistoryRepository; // Direct use for simplicity, ideally via service

    // --- Bulk Pricing Rules ---

    @PostMapping("/items/{itemId}/bulk-rules")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_CATALOG_MANAGER')")
    @Operation(summary = "Create a bulk pricing rule for an item")
    @ApiResponse(responseCode = "201", description = "Bulk pricing rule created")
    public ResponseEntity<BulkPricingRuleDto> createBulkPricingRule(
            @Parameter(description = "ID of the item") @PathVariable UUID itemId,
            @Valid @RequestBody CreateBulkPricingRuleRequest request) {
        // Ensure request's itemId matches path variable or is set correctly by service
        if (!request.getItemId().equals(itemId)) {
            // Or service can enforce/override this
            // For now, let's assume request DTO's itemId is the source of truth if service uses it directly
            // A better DTO might not have itemId if it's in path.
             request.setItemId(itemId); // Override DTO itemId with path variable
        }
        BulkPricingRuleDto createdRule = pricingService.createBulkPricingRule(request);
        return new ResponseEntity<>(createdRule, HttpStatus.CREATED);
    }

    @GetMapping("/items/{itemId}/bulk-rules")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get all bulk pricing rules for an item")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved rules")
    public ResponseEntity<List<BulkPricingRuleDto>> getBulkPricingRulesForItem(
            @Parameter(description = "ID of the item") @PathVariable UUID itemId) {
        List<BulkPricingRuleDto> rules = pricingService.getBulkPricingRulesForItem(itemId);
        return ResponseEntity.ok(rules);
    }

    @GetMapping("/bulk-rules/{ruleId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get a specific bulk pricing rule by its ID")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved rule")
    @ApiResponse(responseCode = "404", description = "Rule not found")
    public ResponseEntity<BulkPricingRuleDto> getBulkPricingRuleById(@PathVariable UUID ruleId) {
        BulkPricingRuleDto rule = pricingService.getBulkPricingRuleById(ruleId);
        return ResponseEntity.ok(rule);
    }

    @PutMapping("/bulk-rules/{ruleId}")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_CATALOG_MANAGER')")
    @Operation(summary = "Update a bulk pricing rule")
    @ApiResponse(responseCode = "200", description = "Rule updated successfully")
    @ApiResponse(responseCode = "404", description = "Rule not found")
    public ResponseEntity<BulkPricingRuleDto> updateBulkPricingRule(
            @Parameter(description = "ID of the rule to update") @PathVariable UUID ruleId,
            @Valid @RequestBody CreateBulkPricingRuleRequest request) {
        BulkPricingRuleDto updatedRule = pricingService.updateBulkPricingRule(ruleId, request);
        return ResponseEntity.ok(updatedRule);
    }

    @DeleteMapping("/bulk-rules/{ruleId}")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_CATALOG_MANAGER')")
    @Operation(summary = "Delete a bulk pricing rule")
    @ApiResponse(responseCode = "204", description = "Rule deleted successfully")
    @ApiResponse(responseCode = "404", description = "Rule not found")
    public ResponseEntity<Void> deleteBulkPricingRule(@PathVariable UUID ruleId) {
        pricingService.deleteBulkPricingRule(ruleId);
        return ResponseEntity.noContent().build();
    }

    // --- Price Calculation & History ---

    @GetMapping("/items/{itemId}/price-detail")
    @PreAuthorize("isAuthenticated()") // All authenticated users can check prices
    @Operation(summary = "Get detailed price for an item at a given quantity",
               description = "Calculates the unit price and total price for an item, considering bulk discounts.")
    @ApiResponse(responseCode = "200", description = "Price details calculated")
    @ApiResponse(responseCode = "404", description = "Item not found")
    public ResponseEntity<PriceDetailDto> getPriceDetailForItem(
            @Parameter(description = "ID of the item") @PathVariable UUID itemId,
            @Parameter(description = "Quantity for which to calculate price", required = true) @RequestParam int quantity) {
        PriceDetailDto priceDetail = pricingService.getPriceDetail(itemId, quantity);
        return ResponseEntity.ok(priceDetail);
    }

    @GetMapping("/items/{itemId}/price-history")
    @PreAuthorize("isAuthenticated()") // Or more restrictive if history is sensitive
    @Operation(summary = "Get price history for an item")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved price history")
    @ApiResponse(responseCode = "404", description = "Item not found")
    public ResponseEntity<Page<PriceHistoryEntity>> getPriceHistoryForItem(
            @Parameter(description = "ID of the item") @PathVariable UUID itemId,
            @PageableDefault(size = 10, sort = "effectiveFrom") Pageable pageable) {
        // Ensure item exists first (optional, repository method might handle it)
        if (!itemService.itemExists(itemId)) { // Assuming itemService gets an existsById method
            throw new ResourceNotFoundException("CatalogItem", "id", itemId);
        }
        Page<PriceHistoryEntity> history = priceHistoryRepository.findByCatalogItemIdOrderByEffectiveFromDesc(itemId, pageable);
        return ResponseEntity.ok(history);
    }

    // The PRD also had GET /items/{id}/price-history. This is covered by the above.
    // The base price update is on CatalogItemController: PUT /items/{id}/price
}
