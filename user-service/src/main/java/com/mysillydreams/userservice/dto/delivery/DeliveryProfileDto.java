package com.mysillydreams.userservice.dto.delivery;

import com.mysillydreams.userservice.domain.delivery.DeliveryProfile;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@Schema(description = "Data Transfer Object for Delivery Profile information.")
public class DeliveryProfileDto {

    @Schema(description = "Unique identifier of the Delivery Profile.", example = "dp-a1b2c3d4-e5f6...")
    private UUID id;

    @Schema(description = "Unique identifier of the associated User.", example = "u-a1b2c3d4-e5f6...")
    private UUID userId;

    @Schema(description = "Details of the delivery vehicle.", example = "Bike - Honda Activa KA01XY1234")
    private String vehicleDetails; // Assuming this is not encrypted for DTO, or already decrypted

    @Schema(description = "Indicates if the delivery profile is active and can receive assignments.", example = "true")
    private boolean active;

    @Schema(description = "Timestamp of when the delivery profile was created.")
    private Instant createdAt;

    @Schema(description = "Timestamp of the last update to the delivery profile.")
    private Instant updatedAt;

    public static DeliveryProfileDto from(DeliveryProfile profile) {
        if (profile == null) {
            return null;
        }
        DeliveryProfileDto dto = new DeliveryProfileDto();
        dto.setId(profile.getId());
        if (profile.getUser() != null) {
            dto.setUserId(profile.getUser().getId());
        }
        dto.setVehicleDetails(profile.getVehicleDetails()); // Assumes already decrypted if was encrypted in entity
        dto.setActive(profile.isActive());
        dto.setCreatedAt(profile.getCreatedAt());
        dto.setUpdatedAt(profile.getUpdatedAt());
        return dto;
    }
}
