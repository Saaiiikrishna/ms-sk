package com.mysillydreams.auth.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;

@Data
@NoArgsConstructor
@Schema(description = "Request payload for user login")
public class LoginRequest {

    @Schema(description = "Username of the user", example = "testuser", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Username cannot be blank")
    private String username;

    @NotBlank(message = "Password cannot be blank")
    private String password;

    @Schema(description = "One-Time Password (OTP) for MFA, required for admins if MFA is enabled.", example = "123456", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String otp; // Optional: for MFA

    public LoginRequest(String username, String password) {
        this.username = username;
        this.password = password;
    }

    // Constructor with OTP
    public LoginRequest(String username, String password, String otp) {
        this.username = username;
        this.password = password;
        this.otp = otp;
    }
}
