package com.mysillydreams.userservice.web.inventory;

import com.mysillydreams.userservice.dto.inventory.InventoryItemDto;
import com.mysillydreams.userservice.dto.inventory.StockAdjustmentRequest;
import com.mysillydreams.userservice.service.inventory.InventoryManagementService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize; // Added
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/inventory")
@Validated
@Tag(name = "Inventory Management API", description = "Endpoints for managing inventory items and stock levels.")
// TODO: SECURITY - Implement robust authorization.
// All endpoints here should verify that the authenticated user (e.g., via X-User-Id from gateway)
// is authorized to act on the given X-Inventory-Profile-Id.
// This typically involves checking if the user owns the profile AND has ROLE_INVENTORY_USER.
public class InventoryController {

    private static final Logger logger = LoggerFactory.getLogger(InventoryController.class);

    private final InventoryManagementService inventoryManagementService;

    @Autowired
    public InventoryController(InventoryManagementService inventoryManagementService) {
        this.inventoryManagementService = inventoryManagementService;
    }

    @Operation(summary = "Add a new inventory item",
               description = "Creates a new inventory item associated with the inventory profile specified by 'X-Inventory-Profile-Id' header.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Inventory item created successfully.",
                         content = @Content(mediaType = "application/json", schema = @Schema(implementation = InventoryItemDto.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request payload (e.g., validation error, duplicate SKU).",
                         content = @Content(mediaType = "application/json", schema = @Schema(implementation = Map.class))),
            @ApiResponse(responseCode = "404", description = "Inventory profile not found for the given X-Inventory-Profile-Id.",
                         content = @Content(mediaType = "application/json", schema = @Schema(implementation = Map.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error.")
    })
    @PostMapping("/items")
    public ResponseEntity<InventoryItemDto> addItem(
            @Parameter(description = "UUID of the inventory profile owning this item.", required = true)
            @RequestHeader("X-Inventory-Profile-Id") UUID profileId,
            @Parameter(description = "Details of the inventory item to be created.", required = true)
            @Valid @RequestBody InventoryItemDto itemDto) {
        try {
            logger.info("Request to add item for Profile ID: {}. SKU: {}", profileId, itemDto.getSku());
            InventoryItemDto createdItem = inventoryManagementService.addItem(profileId, itemDto);
            logger.info("Item added successfully. Item ID: {}, SKU: {}", createdItem.getId(), createdItem.getSku());
            return ResponseEntity.status(HttpStatus.CREATED).body(createdItem);
        } catch (EntityNotFoundException e) {
            logger.warn("Failed to add item: Inventory profile not found for ID {}. Error: {}", profileId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        } catch (IllegalArgumentException e) { // e.g. duplicate SKU
            logger.warn("Failed to add item for Profile ID {}: {}", profileId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Internal error while adding item for Profile ID {}: {}", profileId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error adding inventory item.", e);
        }
    }

    @Operation(summary = "List inventory items for a profile",
               description = "Retrieves a list of all inventory items associated with the inventory profile specified by 'X-Inventory-Profile-Id' header.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved list of inventory items.",
                         content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = InventoryItemDto.class)))),
            @ApiResponse(responseCode = "404", description = "Inventory profile not found."),
            @ApiResponse(responseCode = "500", description = "Internal server error.")
    })
    @GetMapping("/items")
    public ResponseEntity<List<InventoryItemDto>> listItems(
            @Parameter(description = "UUID of the inventory profile whose items are to be listed.", required = true)
            @RequestHeader("X-Inventory-Profile-Id") UUID profileId) {
        try {
            logger.debug("Request to list items for Profile ID: {}", profileId);
            List<InventoryItemDto> items = inventoryManagementService.listItemsByProfileId(profileId);
            logger.debug("Found {} items for Profile ID: {}", items.size(), profileId);
            return ResponseEntity.ok(items);
        } catch (EntityNotFoundException e) {
            logger.warn("Failed to list items: Inventory profile not found for ID {}. Error: {}", profileId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Internal error while listing items for Profile ID {}: {}", profileId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error listing inventory items.", e);
        }
    }

    @Operation(summary = "Adjust stock for an inventory item",
               description = "Adjusts the stock quantity of a specific inventory item (RECEIVE, ISSUE, ADJUSTMENT).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Stock adjusted successfully.",
                         content = @Content(mediaType = "application/json", schema = @Schema(implementation = InventoryItemDto.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request (e.g., validation error, insufficient stock for ISSUE)."),
            @ApiResponse(responseCode = "404", description = "Inventory item not found."),
            @ApiResponse(responseCode = "500", description = "Internal server error.")
    })
    @PostMapping("/items/{itemId}/adjust")
    public ResponseEntity<InventoryItemDto> adjustStock(
            @Parameter(description = "UUID of the inventory item to adjust.", required = true)
            @PathVariable UUID itemId,
            @Parameter(description = "Details of the stock adjustment.", required = true)
            @Valid @RequestBody StockAdjustmentRequest adjustmentRequest) {
        try {
            logger.info("Request to adjust stock for Item ID: {}. Type: {}, Quantity: {}", itemId, adjustmentRequest.getType(), adjustmentRequest.getQuantity());
            InventoryItemDto updatedItem = inventoryManagementService.adjustStock(itemId, adjustmentRequest);
            logger.info("Stock adjusted for Item ID: {}. New Quantity: {}", itemId, updatedItem.getQuantityOnHand());
            return ResponseEntity.ok(updatedItem);
        } catch (EntityNotFoundException e) {
            logger.warn("Failed to adjust stock: Item not found for ID {}. Error: {}", itemId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        } catch (IllegalArgumentException e) { // e.g. insufficient stock
            logger.warn("Failed to adjust stock for Item ID {}: {}", itemId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Internal error while adjusting stock for Item ID {}: {}", itemId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error adjusting stock.", e);
        }
    }

    // TODO: Implement other CRUD endpoints for InventoryItem as needed:
    // GET /items/{itemId} - Get a specific item by ID
    // PUT /items/{itemId} - Update item details (name, description, reorderLevel - not SKU or quantityOnHand via this)
    // DELETE /items/{itemId} - Delete an item (consider soft vs hard delete, impact on transactions)

    // TODO: Endpoint to list stock transactions for an item:
    // GET /items/{itemId}/transactions
}
