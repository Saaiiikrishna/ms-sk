package com.mysillydreams.auth.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;

@Data
@NoArgsConstructor
@Schema(description = "Request payload for refreshing a JWT token")
public class TokenRefreshRequest {

    @Schema(description = "The current (soon to be expired) JWT, which acts as a refresh token for this endpoint.",
            example = "your.current.jwt.token", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Refresh token cannot be blank")
    private String refreshToken; // Assuming the client sends back the current JWT as a "refresh token"
                                 // for this service-specific JWT refresh mechanism.
                                 // A more robust refresh would involve a separate refresh token.

    public TokenRefreshRequest(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}
