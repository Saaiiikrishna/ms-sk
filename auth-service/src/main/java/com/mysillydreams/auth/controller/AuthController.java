package com.mysillydreams.auth.controller;

import com.mysillydreams.auth.controller.dto.JwtResponse;
import com.mysillydreams.auth.controller.dto.LoginRequest;
import com.mysillydreams.auth.controller.dto.TokenRefreshRequest;
import com.mysillydreams.auth.domain.RefreshToken;
import com.mysillydreams.auth.repository.RefreshTokenRepository;
import com.mysillydreams.auth.service.AuthService; // Added
import com.mysillydreams.auth.service.HybridAuthenticationService;
import com.mysillydreams.auth.service.PasswordRotationService;
import com.mysillydreams.auth.service.RefreshTokenService;
import com.mysillydreams.auth.util.JwtTokenProvider;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
// AuthenticationManager no longer directly used here
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/auth")
@Validated
@Tag(name = "Authentication API", description = "Endpoints for user authentication, token management, and password rotation.")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    // private final AuthenticationManager authenticationManager; // Moved to AuthService
    private final JwtTokenProvider jwtTokenProvider; // Still needed for /refresh and /validate
    private final PasswordRotationService passwordRotationService;
    private final AuthService authService; // Added AuthService
    private final HybridAuthenticationService hybridAuthenticationService; // Added Hybrid Authentication
    private final RefreshTokenService refreshTokenService; // Added RefreshTokenService
    private final RefreshTokenRepository refreshTokenRepository; // Added RefreshTokenRepository

    @Autowired
    public AuthController(AuthService authService, // Injected AuthService
                          JwtTokenProvider jwtTokenProvider,
                          PasswordRotationService passwordRotationService,
                          HybridAuthenticationService hybridAuthenticationService,
                          RefreshTokenService refreshTokenService,
                          RefreshTokenRepository refreshTokenRepository) {
        this.authService = authService;
        this.jwtTokenProvider = jwtTokenProvider;
        this.passwordRotationService = passwordRotationService;
        this.hybridAuthenticationService = hybridAuthenticationService;
        this.refreshTokenService = refreshTokenService;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Operation(summary = "User Login", description = "Authenticates a user with username and password against Keycloak and returns a service-specific JWT. For admins with MFA enabled, an OTP must also be provided.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Login successful, JWT returned",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = JwtResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request payload (e.g., missing username/password)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = Map.class))),
            @ApiResponse(responseCode = "401", description = "Invalid credentials, authentication failed, or MFA OTP required/failed for admins",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = Map.class)))
    })
    @PostMapping("/login")
    public ResponseEntity<?> login(
            @Parameter(description = "User credentials for login, including optional OTP for MFA-enabled admins", required = true, schema = @Schema(implementation = LoginRequest.class))
            @Valid @RequestBody LoginRequest loginRequest,
            HttpServletRequest request) { // Retained for IP logging, though GlobalExceptionHandler could also get it
        // Rate limiting is implemented via RateLimitingFilter
        try {
            // IP logging can be done by a filter or WebRequest in GlobalExceptionHandler if preferred for centralization
            logger.info("Login request received for user: {} from IP: {}", loginRequest.getUsername(), request.getRemoteAddr());
            JwtResponse jwtResponse = authService.login(loginRequest); // Delegate to AuthService
            logger.info("User {} login processed successfully from IP: {}.", loginRequest.getUsername(), request.getRemoteAddr());
            return ResponseEntity.ok(jwtResponse);
        } catch (Exception e) {
            // Specific exceptions like BadCredentialsException, MfaAuthenticationRequiredException
            // will be thrown by AuthService and should be handled by GlobalExceptionHandler.
            // This catch block is for any other unexpected exceptions during controller processing itself.
            logger.error("Unexpected error during login processing for user {} from IP {}: {}",
                         loginRequest.getUsername(), request.getRemoteAddr(), e.getMessage(), e);
            throw e; // Re-throw to be caught by GlobalExceptionHandler
        }
    }

    @Operation(summary = "Refresh JWT Token", description = "Refreshes a service-specific JWT using a valid existing JWT (sent as refreshToken in body).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Token refreshed successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = JwtResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request payload (e.g., missing refreshToken)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = Map.class))),
            @ApiResponse(responseCode = "401", description = "Invalid or expired token provided for refresh",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = Map.class)))
    })
    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(
            @Parameter(description = "Request containing the current JWT to be refreshed", required = true, schema = @Schema(implementation = TokenRefreshRequest.class)) @Valid @RequestBody TokenRefreshRequest tokenRefreshRequest) {
        try {
            String oldToken = tokenRefreshRequest.getRefreshToken();
            if (oldToken != null && oldToken.startsWith("Bearer ")) {
                oldToken = oldToken.substring(7);
            }

            JwtResponse jwtResponse = authService.refreshToken(oldToken);
            logger.info("Token refresh processed successfully");
            return ResponseEntity.ok(jwtResponse);
        } catch (Exception e) {
            // Specific exceptions like BadCredentialsException will be handled by GlobalExceptionHandler
            logger.error("Unexpected error during token refresh: {}", e.getMessage(), e);
            throw e; // Re-throw to be caught by GlobalExceptionHandler
        }
    }

    @Operation(summary = "Validate JWT Token", description = "Validates a service-specific JWT passed in the Authorization header.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Token is valid",
                    content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"status\":\"valid\", \"user\":\"testuser\", \"authorities\":[\"ROLE_USER\"]}"))),
            @ApiResponse(responseCode = "400", description = "Authorization header missing or malformed"),
            @ApiResponse(responseCode = "401", description = "Token is invalid or expired",
                    content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"status\":\"invalid\"}")))
    })
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/validate")
    public ResponseEntity<?> validateToken(
            @Parameter(description = "Bearer token for validation (e.g., 'Bearer your.jwt.token')", required = true) @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader) {
        String token = null;
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            token = authorizationHeader.substring(7);
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Authorization header is missing or malformed."));
        }

        try {
            Map<String, Object> validationResult = authService.validateToken(token);
            if ("valid".equals(validationResult.get("status"))) {
                logger.debug("Token validation successful");
                return ResponseEntity.ok(validationResult);
            } else {
                logger.warn("Token validation failed");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(validationResult);
            }
        } catch (Exception e) {
            logger.error("Unexpected error during token validation: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("status", "invalid", "error", "Token validation failed"));
        }
    }

    @Schema(name = "TokenRefreshRequest", description = "Request payload for refreshing JWT token")
    private static class TokenRefreshRequest {
        @NotBlank(message = "Refresh token cannot be blank")
        @Schema(description = "The refresh token", example = "eyJhbGciOiJIUzUxMiJ9...", requiredMode = Schema.RequiredMode.REQUIRED)
        public String refreshToken;

        public String getRefreshToken() {
            return refreshToken;
        }

        public void setRefreshToken(String refreshToken) {
            this.refreshToken = refreshToken;
        }
    }

    @Operation(summary = "Refresh JWT token",
               description = "Refreshes an expired or soon-to-expire JWT token using a valid refresh token.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Token refreshed successfully"),
            @ApiResponse(responseCode = "401", description = "Invalid or expired refresh token"),
            @ApiResponse(responseCode = "400", description = "Invalid request payload"),
            @ApiResponse(responseCode = "500", description = "Internal server error during token refresh")
    })
    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(
            @Parameter(description = "Refresh token request", required = true)
            @Valid @RequestBody TokenRefreshRequest request,
            HttpServletRequest httpRequest) {

        try {
            logger.info("Token refresh request received");

            // Validate refresh token using the secure token service
            Optional<RefreshToken> refreshTokenOpt = refreshTokenService.validateRefreshToken(request.refreshToken);

            if (refreshTokenOpt.isEmpty()) {
                logger.warn("Invalid or expired refresh token provided");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid or expired refresh token"));
            }

            RefreshToken refreshToken = refreshTokenOpt.get();

            // Generate new access token
            Collection<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_USER")); // TODO: Get actual roles
            String newAccessToken = jwtTokenProvider.generateTokenForUser(refreshToken.getUsername(), authorities);
            Long expiresIn = jwtTokenProvider.getExpiryDateFromToken(newAccessToken) - System.currentTimeMillis();

            // Generate new refresh token and revoke the old one
            refreshTokenService.revokeRefreshToken(request.refreshToken);
            RefreshToken newRefreshToken = refreshTokenService.generateRefreshToken(
                refreshToken.getUsername(),
                refreshToken.getUserId(),
                httpRequest
            );

            JwtResponse response = new JwtResponse(newAccessToken, expiresIn);
            response.setRefreshToken(newRefreshToken.getToken());

            logger.info("Token refreshed successfully for user: {}", refreshToken.getUsername());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error during token refresh: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Token refresh failed"));
        }
    }

    /**
     * Get user's active sessions
     */
    @GetMapping("/sessions")
    @Operation(summary = "Get user sessions", description = "Retrieve all active sessions for the current user")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<?> getUserSessions(HttpServletRequest request) {
        try {
            String authHeader = request.getHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Missing or invalid Authorization header"));
            }

            String token = authHeader.substring(7);
            String username = jwtTokenProvider.getUsernameFromToken(token);

            List<RefreshToken> userSessions = refreshTokenService.getUserValidTokens(username);

            List<Map<String, Object>> sessionData = userSessions.stream()
                .map(session -> {
                    Map<String, Object> sessionInfo = new HashMap<>();
                    sessionInfo.put("id", session.getId().toString());
                    sessionInfo.put("issuedAt", session.getIssuedAt().toString());
                    sessionInfo.put("expiresAt", session.getExpiresAt().toString());
                    sessionInfo.put("ipAddress", session.getIpAddress());
                    sessionInfo.put("userAgent", session.getUserAgent());
                    sessionInfo.put("isCurrent", false); // TODO: Determine current session
                    sessionInfo.put("isValid", session.isValid());
                    return sessionInfo;
                })
                .collect(Collectors.toList());

            return ResponseEntity.ok(Map.of("sessions", sessionData));

        } catch (Exception e) {
            logger.error("Error retrieving user sessions: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to retrieve sessions"));
        }
    }

    /**
     * Revoke a specific session
     */
    @PostMapping("/sessions/{sessionId}/revoke")
    @Operation(summary = "Revoke session", description = "Revoke a specific user session")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<?> revokeSession(
            @PathVariable String sessionId,
            HttpServletRequest request) {
        try {
            String authHeader = request.getHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Missing or invalid Authorization header"));
            }

            String token = authHeader.substring(7);
            String username = jwtTokenProvider.getUsernameFromToken(token);

            // Find the session and verify it belongs to the user
            Optional<RefreshToken> sessionOpt = refreshTokenRepository.findById(UUID.fromString(sessionId));
            if (sessionOpt.isEmpty() || !sessionOpt.get().getUsername().equals(username)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Session not found"));
            }

            refreshTokenService.revokeRefreshToken(sessionOpt.get().getToken());

            return ResponseEntity.ok(Map.of("message", "Session revoked successfully"));

        } catch (Exception e) {
            logger.error("Error revoking session: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to revoke session"));
        }
    }

    /**
     * Revoke all other sessions (except current)
     */
    @PostMapping("/sessions/revoke-all-others")
    @Operation(summary = "Revoke all other sessions", description = "Revoke all user sessions except the current one")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<?> revokeAllOtherSessions(HttpServletRequest request) {
        try {
            String authHeader = request.getHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Missing or invalid Authorization header"));
            }

            String token = authHeader.substring(7);
            String username = jwtTokenProvider.getUsernameFromToken(token);

            // TODO: Implement logic to keep current session and revoke others
            refreshTokenService.revokeAllUserTokens(username);

            return ResponseEntity.ok(Map.of("message", "All other sessions revoked successfully"));

        } catch (Exception e) {
            logger.error("Error revoking other sessions: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to revoke other sessions"));
        }
    }

    @Operation(summary = "Initiate Password Rotation", description = "Triggers a password rotation process for a specified user. Requires ADMIN role.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Password rotation process initiated successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"message\":\"Password rotation process initiated for user uuid-goes-here\"}"))),
            @ApiResponse(responseCode = "400", description = "Invalid userId format or missing userId"),
            @ApiResponse(responseCode = "401", description = "Unauthorized (caller not authenticated)"),
            @ApiResponse(responseCode = "403", description = "Forbidden (caller does not have ADMIN role)"),
            @ApiResponse(responseCode = "404", description = "User not found in Keycloak"),
            @ApiResponse(responseCode = "500", description = "Internal server error during rotation process")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/password-rotate")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<?> rotatePassword(
            @Parameter(description = "UUID of the user whose password is to be rotated", required = true) @RequestParam @NotNull UUID userId) {
        // Assuming ROLE_ADMIN is checked by PreAuthorize, get current admin for logging
        String adminPerformingAction = "unknown_admin";
        Authentication currentAuth = SecurityContextHolder.getContext().getAuthentication();
        if (currentAuth != null) {
            adminPerformingAction = currentAuth.getName();
        }
        logger.info("Password rotation request for user ID: {} by admin: {}", userId, adminPerformingAction);
        passwordRotationService.rotatePassword(userId);
        logger.info("Password rotation initiated successfully for user ID: {}", userId);
        return ResponseEntity.ok(Map.of("message", "Password rotation process initiated for user " + userId));
    }
}
