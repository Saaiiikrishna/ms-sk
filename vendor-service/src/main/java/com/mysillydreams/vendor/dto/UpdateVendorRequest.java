package com.mysillydreams.vendor.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateVendorRequest {

  @NotBlank(message = "Vendor name cannot be blank.")
  @Size(min = 2, max = 255, message = "Vendor name must be between 2 and 255 characters.")
  private String name;

  @Size(max = 20, message = "KYC status cannot exceed 20 characters.") // e.g., REGISTERED, PENDING, VERIFIED, REJECTED
  private String kycStatus;

  // For JsonNode, specific validation might be complex with annotations.
  // Consider custom validators or validating within the service if specific structure is needed.
  private JsonNode contactInfo;

  private JsonNode bankDetails;
}
