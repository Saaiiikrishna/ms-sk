package com.mysillydreams.auth.controller;

import com.mysillydreams.auth.controller.dto.JwtResponse;
import com.mysillydreams.auth.controller.dto.LoginRequest;
import com.mysillydreams.auth.controller.dto.TokenRefreshRequest;
import com.mysillydreams.auth.service.PasswordRotationService;
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
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/auth")
@Validated
@Tag(name = "Authentication API", description = "Endpoints for user authentication, token management, and password rotation.")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordRotationService passwordRotationService;

    @Autowired
    public AuthController(AuthenticationManager authenticationManager,
                          JwtTokenProvider jwtTokenProvider,
                          PasswordRotationService passwordRotationService) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
        this.passwordRotationService = passwordRotationService;
    }

    @Operation(summary = "User Login", description = "Authenticates a user with username and password against Keycloak and returns a service-specific JWT.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Login successful, JWT returned",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = JwtResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request payload (e.g., missing username/password)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = Map.class))),
            @ApiResponse(responseCode = "401", description = "Invalid credentials or authentication failed",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = Map.class)))
    })
    @PostMapping("/login")
    public ResponseEntity<?> login(
            @Parameter(description = "User credentials for login", required = true, schema = @Schema(implementation = LoginRequest.class)) @Valid @RequestBody LoginRequest loginRequest,
            HttpServletRequest request) {
        // TODO: SECURITY - Implement rate limiting / brute force protection for login endpoint.
        try {
            logger.info("Login attempt for user: {} from IP: {}", loginRequest.getUsername(), request.getRemoteAddr());
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword())
            );
            String jwt = jwtTokenProvider.generateToken(authentication);
            Long expiresIn = jwtTokenProvider.getExpiryDateFromToken(jwt) - System.currentTimeMillis();
            logger.info("User {} logged in successfully from IP: {}. JWT generated.", loginRequest.getUsername(), request.getRemoteAddr());
            return ResponseEntity.ok(new JwtResponse(jwt, expiresIn));
        } catch (Exception e) {
            logger.error("Unexpected error during login for user {} from IP {}: {}", loginRequest.getUsername(), request.getRemoteAddr(), e.getMessage(), e);
             throw e;
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
        String oldToken = tokenRefreshRequest.getRefreshToken();
        if (oldToken != null && oldToken.startsWith("Bearer ")) {
            oldToken = oldToken.substring(7);
        }

        if (jwtTokenProvider.validateToken(oldToken)) {
            Authentication authentication = jwtTokenProvider.getAuthentication(oldToken);
            String newJwt = jwtTokenProvider.generateToken(authentication);
            Long expiresIn = jwtTokenProvider.getExpiryDateFromToken(newJwt) - System.currentTimeMillis();
            logger.info("Token refreshed for user: {}", authentication.getName());
            return ResponseEntity.ok(new JwtResponse(newJwt, expiresIn));
        } else {
            logger.warn("Invalid token provided for refresh.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid token for refresh"));
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

        if (jwtTokenProvider.validateToken(token)) {
            Authentication authentication = jwtTokenProvider.getAuthentication(token);
            logger.debug("Token validated successfully for user: {}", authentication.getName());
            return ResponseEntity.ok(Map.of(
                    "status", "valid",
                    "user", authentication.getName(),
                    "authorities", authentication.getAuthorities()
            ));
        } else {
            logger.warn("Token validation failed.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("status", "invalid"));
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
        logger.info("Password rotation request for user ID: {} by admin: {}", userId, SecurityContextHolder.getContext().getAuthentication().getName());
        passwordRotationService.rotatePassword(userId);
        logger.info("Password rotation initiated successfully for user ID: {}", userId);
        return ResponseEntity.ok(Map.of("message", "Password rotation process initiated for user " + userId));
    }
}
