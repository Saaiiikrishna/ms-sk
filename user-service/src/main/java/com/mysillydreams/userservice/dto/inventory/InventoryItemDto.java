package com.mysillydreams.userservice.dto.inventory;

import com.mysillydreams.userservice.domain.inventory.InventoryItem;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@Schema(description = "Data Transfer Object for Inventory Item details.")
public class InventoryItemDto {

    @Schema(description = "Unique identifier of the Inventory Item (UUID). Provided in response, not for creation.",
            example = "i-a1b2c3d4-e5f6-7890-1234-567890abcdef", accessMode = Schema.AccessMode.READ_ONLY)
    private UUID id;

    @NotBlank(message = "SKU cannot be blank.")
    @Size(max = 100, message = "SKU must be less than 100 characters.")
    @Schema(description = "Stock Keeping Unit (unique identifier for the item).", example = "SKU-XYZ-001", requiredMode = Schema.RequiredMode.REQUIRED)
    private String sku;

    @NotBlank(message = "Item name cannot be blank.")
    @Size(max = 255, message = "Item name must be less than 255 characters.")
    @Schema(description = "Name of the inventory item.", example = "Premium Quality Widget", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Schema(description = "Detailed description of the inventory item.", example = "A widget of premium quality, suitable for all purposes.")
    private String description; // Optional

    @NotNull(message = "Quantity on hand must be provided for new items, even if zero.")
    @Min(value = 0, message = "Quantity on hand cannot be negative.")
    @Schema(description = "Current quantity of the item in stock. Defaults to 0 for new items if not specified by service.", example = "100")
    private Integer quantityOnHand = 0; // Default for request, response will reflect actual

    @NotNull(message = "Reorder level must be provided.")
    @Min(value = 0, message = "Reorder level cannot be negative.")
    @Schema(description = "The minimum stock quantity at which a reorder should be triggered.", example = "20", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer reorderLevel = 0;

    @Schema(description = "Timestamp of item creation.", accessMode = Schema.AccessMode.READ_ONLY)
    private Instant createdAt;

    @Schema(description = "Timestamp of last item update.", accessMode = Schema.AccessMode.READ_ONLY)
    private Instant updatedAt;

    // @Schema(description = "ID of the Inventory Profile that owns this item. Required for creation if not path param.", example = "p-a1b2c3d4...")
    // private UUID ownerProfileId; // Usually provided as path/header param, not in body for item creation under a profile.

    public static InventoryItemDto from(InventoryItem item) {
        if (item == null) {
            return null;
        }
        InventoryItemDto dto = new InventoryItemDto();
        dto.setId(item.getId());
        dto.setSku(item.getSku());
        dto.setName(item.getName()); // Assuming name/description are not encrypted in InventoryItem
        dto.setDescription(item.getDescription());
        dto.setQuantityOnHand(item.getQuantityOnHand());
        dto.setReorderLevel(item.getReorderLevel());
        dto.setCreatedAt(item.getCreatedAt());
        dto.setUpdatedAt(item.getUpdatedAt());
        // if (item.getOwner() != null) {
        //     dto.setOwnerProfileId(item.getOwner().getId());
        // }
        return dto;
    }

    // Method to map DTO to Entity (useful in service layer, or use a dedicated mapper)
    public InventoryItem toEntity() {
        InventoryItem item = new InventoryItem();
        // ID is generated by DB, not set from DTO for creation
        item.setSku(this.sku);
        item.setName(this.name);
        item.setDescription(this.description);
        item.setQuantityOnHand(this.quantityOnHand != null ? this.quantityOnHand : 0); // Default to 0 if null
        item.setReorderLevel(this.reorderLevel != null ? this.reorderLevel : 0); // Default to 0 if null
        // Owner (InventoryProfile) needs to be set in the service layer.
        return item;
    }
}
