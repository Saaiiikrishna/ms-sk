package com.mysillydreams.userservice.dto.inventory;

import com.mysillydreams.userservice.domain.inventory.InventoryProfile;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@Schema(description = "Data Transfer Object for Inventory Profile information.")
public class InventoryProfileDto {

    @Schema(description = "Unique identifier of the Inventory Profile.", example = "a1b2c3d4-e5f6-7890-1234-567890abcdef")
    private UUID id;

    @Schema(description = "Unique identifier of the associated User.", example = "d290f1ee-6c54-4b01-90e6-d701748f0851")
    private UUID userId; // ID of the UserEntity

    // Could also include user's referenceId if that's more commonly used externally
    // @Schema(description = "Business reference ID of the associated User.", example = "user-ref-xyz")
    // private String userReferenceId;

    @Schema(description = "Timestamp of when the inventory profile was created.")
    private Instant createdAt;

    // Add other fields from InventoryProfile if they become relevant for API responses,
    // e.g., defaultWarehouse, specific permissions flags, etc.

    public static InventoryProfileDto from(InventoryProfile profile) {
        if (profile == null) {
            return null;
        }
        InventoryProfileDto dto = new InventoryProfileDto();
        dto.setId(profile.getId());
        if (profile.getUser() != null) {
            dto.setUserId(profile.getUser().getId());
            // if (profile.getUser().getReferenceId() != null) {
            //     dto.setUserReferenceId(profile.getUser().getReferenceId());
            // }
        }
        dto.setCreatedAt(profile.getCreatedAt());
        return dto;
    }
}
