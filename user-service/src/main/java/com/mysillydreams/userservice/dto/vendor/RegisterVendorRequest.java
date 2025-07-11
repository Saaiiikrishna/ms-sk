package com.mysillydreams.userservice.dto.vendor;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Schema(description = "Request payload for registering a new vendor.")
public class RegisterVendorRequest {

    @NotBlank(message = "Legal name cannot be blank.")
    @Size(min = 2, max = 255, message = "Legal name must be between 2 and 255 characters.")
    @Schema(description = "The legal name of the vendor entity.", example = "Acme Innovations Inc.", requiredMode = Schema.RequiredMode.REQUIRED)
    private String legalName;

    // Add other fields if needed for initial registration, e.g.,
    // private String companyType;
    // private String contactEmail; // Could be different from the user's primary email
}
