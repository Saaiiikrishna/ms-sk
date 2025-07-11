package com.mysillydreams.userservice.dto.vendor;

import com.mysillydreams.userservice.domain.vendor.VendorProfile;
import com.mysillydreams.userservice.domain.vendor.VendorStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;
// Import other DTOs like UserDto if you want to embed user information
// import com.mysillydreams.userservice.dto.UserDto;

@Data
@NoArgsConstructor
@Schema(description = "Data Transfer Object for Vendor Profile information.")
public class VendorProfileDto {

    @Schema(description = "Unique identifier of the Vendor Profile.", example = "a1b2c3d4-e5f6-7890-1234-567890abcdef")
    private UUID id;

    // @Schema(description = "Associated user information.")
    // private UserDto user; // Consider if the full UserDto is needed or just userId/referenceId

    @Schema(description = "The legal name of the vendor entity.", example = "Acme Innovations Inc.")
    private String legalName;

    @Schema(description = "Current status of the vendor profile.", example = "KYC_IN_PROGRESS")
    private VendorStatus status;

    @Schema(description = "Identifier for the KYC workflow associated with this vendor.", example = "wf_123xyz")
    private String kycWorkflowId;

    @Schema(description = "Timestamp of when the vendor profile was created.")
    private Instant createdAt;

    @Schema(description = "Timestamp of the last update to the vendor profile.")
    private Instant updatedAt;

    // TODO: Add list of VendorDocumentDto if needed here.
    // private List<VendorDocumentDto> documents;

    public static VendorProfileDto from(VendorProfile vp) {
        if (vp == null) {
            return null;
        }
        VendorProfileDto dto = new VendorProfileDto();
        dto.setId(vp.getId());
        dto.setLegalName(vp.getLegalName()); // Assuming legalName is not encrypted or already decrypted by entity load
        dto.setStatus(vp.getStatus());
        dto.setKycWorkflowId(vp.getKycWorkflowId());
        dto.setCreatedAt(vp.getCreatedAt());
        dto.setUpdatedAt(vp.getUpdatedAt());

        // If you want to include basic user info (e.g., referenceId from UserEntity):
        // if (vp.getUser() != null) {
        //     dto.setUserReferenceId(vp.getUser().getReferenceId()); // Example
        // }
        return dto;
    }
}
