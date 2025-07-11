package com.mysillydreams.userservice.dto.support;

import com.mysillydreams.userservice.domain.support.SupportProfile;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@Schema(description = "Data Transfer Object for Support Profile information.")
public class SupportProfileDto {

    @Schema(description = "Unique identifier of the Support Profile.")
    private UUID id;

    @Schema(description = "Unique identifier of the associated User (the support agent).")
    private UUID userId;

    @Schema(description = "Specialization of the support agent (e.g., 'Billing', 'Technical').")
    private String specialization;

    @Schema(description = "Indicates if the support agent's profile is active.")
    private boolean active;

    @Schema(description = "Timestamp of when the support profile was created.")
    private Instant createdAt;

    @Schema(description = "Timestamp of the last update to the support profile.")
    private Instant updatedAt;

    public static SupportProfileDto from(SupportProfile profile) {
        if (profile == null) {
            return null;
        }
        SupportProfileDto dto = new SupportProfileDto();
        dto.setId(profile.getId());
        if (profile.getUser() != null) {
            dto.setUserId(profile.getUser().getId());
        }
        dto.setSpecialization(profile.getSpecialization());
        dto.setActive(profile.isActive());
        dto.setCreatedAt(profile.getCreatedAt());
        dto.setUpdatedAt(profile.getUpdatedAt());
        return dto;
    }
}
