package com.ecommerce.vendorfulfillmentservice.controller.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReassignAssignmentRequest {

    @NotNull(message = "New Vendor ID cannot be null.")
    private UUID newVendorId;
}
