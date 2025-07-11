package com.mysillydreams.delivery.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
// Consider adding support for file upload if photo is sent as multipart/form-data
// For now, assuming photoUrl is a reference to an already uploaded image (e.g., to S3).

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PhotoOtpDto {

    @NotBlank(message = "Photo URL cannot be blank if photo is required.") // Or make it optional based on workflow
    private String photoUrl; // URL to the uploaded photo

    @Size(min = 4, max = 8, message = "OTP must be between 4 and 8 characters.") // Example OTP validation
    private String otp; // One-Time Password, can be optional depending on the step

    private String notes; // Optional notes from the courier

    // Could also include:
    // private Double latitude; // Location where photo/OTP was captured
    // private Double longitude;
    // private Instant capturedAt; // Timestamp of capture
}
