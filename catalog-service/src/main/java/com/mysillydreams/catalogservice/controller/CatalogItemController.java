package com.mysillydreams.catalogservice.controller;

import com.mysillydreams.catalogservice.domain.model.ItemType;
import com.mysillydreams.catalogservice.dto.CatalogItemDto;
import com.mysillydreams.catalogservice.dto.CreateCatalogItemRequest;
import com.mysillydreams.catalogservice.service.ItemService;
import com.mysillydreams.catalogservice.service.search.CatalogItemSearchDocument;
import com.mysillydreams.catalogservice.service.search.SearchService;
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
import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/items")
@RequiredArgsConstructor
@Tag(name = "Catalog Item Management", description = "APIs for managing products and services in the catalog")
public class CatalogItemController {

    private final ItemService itemService;
    private final SearchService searchService; // Added SearchService

    @PostMapping
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_CATALOG_MANAGER')")
    @Operation(summary = "Create a new catalog item", description = "Creates a new product or service item.")
    @ApiResponse(responseCode = "201", description = "Item created successfully")
    public ResponseEntity<CatalogItemDto> createItem(@Valid @RequestBody CreateCatalogItemRequest request) {
        CatalogItemDto createdItem = itemService.createItem(request);
        return new ResponseEntity<>(createdItem, HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get catalog item by ID", description = "Retrieves full details of a catalog item, including current stock and price.")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved item")
    @ApiResponse(responseCode = "404", description = "Item not found")
    public ResponseEntity<CatalogItemDto> getItemById(@Parameter(description = "ID of the item to retrieve") @PathVariable UUID id) {
        CatalogItemDto item = itemService.getItemById(id);
        return ResponseEntity.ok(item);
    }

    @GetMapping("/sku/{sku}")
    @Operation(summary = "Get catalog item by SKU", description = "Retrieves full details of a catalog item by its SKU.")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved item")
    @ApiResponse(responseCode = "404", description = "Item not found for SKU")
    public ResponseEntity<CatalogItemDto> getItemBySku(@Parameter(description = "SKU of the item to retrieve") @PathVariable String sku) {
        CatalogItemDto item = itemService.getItemBySku(sku);
        return ResponseEntity.ok(item);
    }


    @GetMapping
    @Operation(summary = "List all catalog items", description = "Retrieves a paginated list of all catalog items.")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved items")
    public ResponseEntity<Page<CatalogItemDto>> getAllItems(@PageableDefault(size = 20) Pageable pageable) {
        Page<CatalogItemDto> items = itemService.getAllItems(pageable);
        return ResponseEntity.ok(items);
    }

    @GetMapping("/category/{categoryId}")
    @Operation(summary = "List items by category ID", description = "Retrieves a paginated list of items belonging to a specific category.")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved items")
    @ApiResponse(responseCode = "404", description = "Category not found")
    public ResponseEntity<Page<CatalogItemDto>> getItemsByCategoryId(
            @Parameter(description = "ID of the category") @PathVariable UUID categoryId,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<CatalogItemDto> items = itemService.getItemsByCategoryId(categoryId, pageable);
        return ResponseEntity.ok(items);
    }


    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_CATALOG_MANAGER')")
    @Operation(summary = "Update a catalog item", description = "Updates details of an existing catalog item. SKU updates may be restricted.")
    @ApiResponse(responseCode = "200", description = "Item updated successfully")
    @ApiResponse(responseCode = "404", description = "Item not found")
    public ResponseEntity<CatalogItemDto> updateItem(
            @Parameter(description = "ID of the item to update") @PathVariable UUID id,
            @Valid @RequestBody CreateCatalogItemRequest request) {
        CatalogItemDto updatedItem = itemService.updateItem(id, request);
        return ResponseEntity.ok(updatedItem);
    }

    @PutMapping("/{id}/price")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_CATALOG_MANAGER')")
    @Operation(summary = "Update item's base price", description = "Updates the base price of an item and records it in price history.")
    @ApiResponse(responseCode = "200", description = "Item price updated successfully")
    @ApiResponse(responseCode = "400", description = "Invalid price value")
    @ApiResponse(responseCode = "404", description = "Item not found")
    public ResponseEntity<CatalogItemDto> updateItemPrice(
            @Parameter(description = "ID of the item to update price for") @PathVariable UUID id,
            @Parameter(description = "The new base price", required = true) @RequestParam BigDecimal newPrice) {
        CatalogItemDto updatedItem = itemService.updateItemPrice(id, newPrice);
        return ResponseEntity.ok(updatedItem);
    }


    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_CATALOG_MANAGER')")
    @Operation(summary = "Delete a catalog item", description = "Deletes a catalog item. Consider implications if item is in carts or orders.")
    @ApiResponse(responseCode = "204", description = "Item deleted successfully")
    @ApiResponse(responseCode = "404", description = "Item not found")
    public ResponseEntity<Void> deleteItem(@Parameter(description = "ID of the item to delete") @PathVariable UUID id) {
        itemService.deleteItem(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/search")
    @Operation(summary = "Search for catalog items",
               description = "Performs a faceted search across catalog items based on keywords, category, price range, and type.")
    @ApiResponse(responseCode = "200", description = "Search results retrieved successfully")
    public ResponseEntity<Page<CatalogItemSearchDocument>> searchItems(
            @Parameter(description = "Keyword to search in name, description, SKU, category name") @RequestParam(required = false) String query,
            @Parameter(description = "Filter by specific Category ID") @RequestParam(required = false) UUID categoryId,
            @Parameter(description = "Filter by category path (e.g., /electronics/laptops/) to search in a category and its descendants") @RequestParam(required = false) String categoryPath,
            @Parameter(description = "Filter by item type (PRODUCT or SERVICE)") @RequestParam(required = false) ItemType itemType,
            @Parameter(description = "Minimum price filter") @RequestParam(required = false) BigDecimal minPrice,
            @Parameter(description = "Maximum price filter") @RequestParam(required = false) BigDecimal maxPrice,
            @PageableDefault(size = 20, sort = {"_score"}) Pageable pageable) {

        Page<CatalogItemSearchDocument> results = searchService.searchItems(
                query, categoryId, categoryPath, itemType, minPrice, maxPrice, pageable
        );
        return ResponseEntity.ok(results);
    }
}
