package com.mysillydreams.userservice.dto.delivery;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Schema(description = "Request payload for verifying a One-Time Password (OTP) for delivery confirmation.")
public class OtpVerificationRequestDto {

    @NotBlank(message = "OTP cannot be blank.")
    @Size(min = 4, max = 8, message = "OTP must be between 4 and 8 characters long.") // Typical OTP length
    @Schema(description = "The One-Time Password provided by the customer.", example = "123456", requiredMode = Schema.RequiredMode.REQUIRED)
    private String otp;
}
