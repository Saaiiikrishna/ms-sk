package com.mysillydreams.vendor.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateVendorRequest {

  @NotNull(message = "User ID cannot be null.")
  private UUID userId;

  @NotBlank(message = "Vendor name cannot be blank.")
  @Size(min = 2, max = 255, message = "Vendor name must be between 2 and 255 characters.")
  private String name;

  @Size(max = 50, message = "Legal type cannot exceed 50 characters.")
  private String legalType; // e.g., LLC, Sole Proprietorship, Corporation

  // For JsonNode, specific validation might be complex with annotations.
  // Consider custom validators or validating within the service if specific structure is needed.
  private JsonNode contactInfo; // e.g., {"email": "vendor@example.com", "phone": "123-456-7890"}

  private JsonNode bankDetails; // e.g., {"accountNumber": "...", "routingNumber": "...", "bankName": "..."}
}
