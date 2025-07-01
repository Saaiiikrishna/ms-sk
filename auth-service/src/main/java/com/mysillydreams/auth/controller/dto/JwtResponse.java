package com.mysillydreams.auth.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Schema(description = "Response payload containing the JWT access token")
public class JwtResponse {
    @Schema(description = "The JWT access token", example = "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJ0ZXN0dXNlci...")
    private String accessToken;

    @Schema(description = "Type of the token", example = "Bearer")
    private String tokenType = "Bearer";

    @Schema(description = "Token validity duration in milliseconds from the time of issuance", example = "3600000")
    private Long expiresIn; // Optional: to inform client about token lifetime

    public JwtResponse(String accessToken, Long expiresIn) {
        this.accessToken = accessToken;
        this.expiresIn = expiresIn;
    }

    public JwtResponse(String accessToken) {
        this.accessToken = accessToken;
    }
}
