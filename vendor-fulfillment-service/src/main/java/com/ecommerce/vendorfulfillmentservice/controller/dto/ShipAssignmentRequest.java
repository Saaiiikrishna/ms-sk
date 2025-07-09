package com.ecommerce.vendorfulfillmentservice.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShipAssignmentRequest {

    @NotBlank(message = "Tracking number cannot be blank.")
    @Size(min = 1, max = 100, message = "Tracking number must be between 1 and 100 characters.")
    private String trackingNo;
}
