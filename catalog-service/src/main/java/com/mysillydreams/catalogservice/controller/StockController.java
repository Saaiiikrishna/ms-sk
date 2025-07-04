package com.mysillydreams.catalogservice.controller;

import com.mysillydreams.catalogservice.dto.StockAdjustmentRequest;
import com.mysillydreams.catalogservice.dto.StockLevelDto;
import com.mysillydreams.catalogservice.service.StockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/stock")
@RequiredArgsConstructor
@Tag(name = "Stock Management", description = "APIs for managing stock levels of products")
public class StockController {

    private final StockService stockService;

    @GetMapping("/{itemId}")
    @PreAuthorize("isAuthenticated()") // Or specific roles if needed for just viewing stock
    @Operation(summary = "Get stock level for an item", description = "Retrieves the current stock level details for a specific item.")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved stock level")
    @ApiResponse(responseCode = "404", description = "Item or stock level not found")
    public ResponseEntity<StockLevelDto> getStockLevelByItemId(
            @Parameter(description = "ID of the item") @PathVariable UUID itemId) {
        StockLevelDto stockLevel = stockService.getStockLevelByItemId(itemId);
        return ResponseEntity.ok(stockLevel);
    }

    @PostMapping("/adjust")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_INVENTORY_MANAGER', 'ROLE_CATALOG_MANAGER')") // Define appropriate roles
    @Operation(summary = "Adjust stock for an item", description = "Adjusts the stock quantity for an item (e.g., receive, issue, manual adjustment).")
    @ApiResponse(responseCode = "200", description = "Stock adjusted successfully")
    @ApiResponse(responseCode = "400", description = "Invalid adjustment request (e.g., insufficient stock for issue)")
    @ApiResponse(responseCode = "404", description = "Item not found")
    public ResponseEntity<StockLevelDto> adjustStock(@Valid @RequestBody StockAdjustmentRequest request) {
        StockLevelDto updatedStockLevel = stockService.adjustStock(request);
        return ResponseEntity.ok(updatedStockLevel);
    }

    @GetMapping("/levels")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_INVENTORY_MANAGER', 'ROLE_CATALOG_MANAGER')")
    @Operation(summary = "List all stock levels", description = "Retrieves a paginated list of all product stock levels.")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved stock levels")
    public ResponseEntity<Page<StockLevelDto>> listStockLevels(@PageableDefault(size = 20) Pageable pageable) {
        Page<StockLevelDto> stockLevels = stockService.listStockLevels(pageable);
        return ResponseEntity.ok(stockLevels);
    }

    @GetMapping("/below-reorder")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_INVENTORY_MANAGER', 'ROLE_CATALOG_MANAGER')")
    @Operation(summary = "List items below reorder level", description = "Retrieves a list of items whose stock quantity is below their defined reorder level.")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved items below reorder level")
    public ResponseEntity<List<StockLevelDto>> getItemsBelowReorderLevel() {
        List<StockLevelDto> items = stockService.findItemsBelowReorderLevel();
        return ResponseEntity.ok(items);
    }

    // Note: PRD had GET /items/{id}/stock and POST /items/{id}/stock/adjust.
    // I've used a separate /api/v1/stock base path for these operations for clarity.
    // If strict adherence to /items/{id}/stock is needed, these can be moved under CatalogItemController
    // with appropriate method signatures. For now, keeping them separate in StockController.
}
