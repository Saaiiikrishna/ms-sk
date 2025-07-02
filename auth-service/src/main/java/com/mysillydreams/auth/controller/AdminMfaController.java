package com.mysillydreams.auth.controller;

import com.mysillydreams.auth.service.AdminMfaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt; // Assuming JWT principal from Spring Security OAuth2 Resource Server
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;


import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/auth/admin/mfa")
@Validated
@Tag(name = "Admin MFA Management API", description = "Endpoints for administrators to set up and verify MFA.")
@SecurityRequirement(name = "bearerAuth") // Assuming these endpoints are protected and require prior admin auth
public class AdminMfaController {

    private static final Logger logger = LoggerFactory.getLogger(AdminMfaController.class);

    private final AdminMfaService adminMfaService;

    @Autowired
    public AdminMfaController(AdminMfaService adminMfaService) {
        this.adminMfaService = adminMfaService;
    }

    // Helper to get current admin's user ID from JWT principal
    private UUID getCurrentAdminUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt) {
            Jwt jwt = (Jwt) authentication.getPrincipal();
            String sub = jwt.getSubject(); // Keycloak 'sub' claim is usually the user ID
            try {
                return UUID.fromString(sub);
            } catch (IllegalArgumentException e) {
                logger.error("Invalid UUID format for subject claim in JWT: {}", sub, e);
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Invalid user identifier in token.");
            }
        }
        // This should not happen if endpoint is properly secured and token is valid.
        logger.error("Could not extract admin user ID from security context. Authentication: {}", authentication);
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Admin user ID not found in security context.");
    }

    // Helper to get current admin's username (preferred_username) from JWT principal
    private String getCurrentAdminUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt) {
            Jwt jwt = (Jwt) authentication.getPrincipal();
            return jwt.getClaimAsString("preferred_username");
        }
        return "admin_user"; // Fallback if not found, though it shouldn't happen
    }


    @Operation(summary = "Setup MFA for the authenticated admin",
               description = "Generates a new TOTP secret and QR code for the currently authenticated admin user. " +
                             "The admin must have the ROLE_ADMIN.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "MFA setup details returned successfully.",
                         content = @Content(mediaType = "application/json",
                                            schema = @Schema(implementation = AdminMfaService.MfaSetupResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized."),
            @ApiResponse(responseCode = "403", description = "Forbidden (User is not an admin)."),
            @ApiResponse(responseCode = "500", description = "Internal server error during MFA setup.")
    })
    @PostMapping("/setup")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<AdminMfaService.MfaSetupResponse> setupMfa() {
        try {
            UUID adminUserId = getCurrentAdminUserId();
            String adminUsername = getCurrentAdminUsername(); // For QR code label
            logger.info("Admin MFA setup request for User ID: {}", adminUserId);
            AdminMfaService.MfaSetupResponse setupResponse = adminMfaService.generateMfaSetup(adminUserId, adminUsername);
            return ResponseEntity.ok(setupResponse);
        } catch (AdminMfaService.MfaOperationException e) {
            logger.error("MFA setup failed for admin {}: {}", getCurrentAdminUsername(), e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
        } catch (ResponseStatusException rse) {
            throw rse; // Re-throw if it's already one of these from helper
        }
         catch (Exception e) {
            logger.error("Unexpected error during MFA setup for admin {}: {}", getCurrentAdminUsername(), e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error during MFA setup.", e);
        }
    }

    @Schema(name = "MfaVerificationRequest", description = "Request payload for verifying an OTP.")
    private static class MfaVerificationRequest {
        @NotBlank(message = "OTP cannot be blank.")
        @Schema(description = "The One-Time Password from the authenticator app.", example = "123456", requiredMode = Schema.RequiredMode.REQUIRED)
        public String otp;
    }


    @Operation(summary = "Verify and enable MFA for the authenticated admin",
               description = "Verifies the provided OTP against the admin's stored TOTP secret and enables MFA if valid. " +
                             "Admin must have ROLE_ADMIN.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "MFA successfully verified and enabled."),
            @ApiResponse(responseCode = "400", description = "Invalid OTP or MFA already enabled/not setup."),
            @ApiResponse(responseCode = "401", description = "Unauthorized."),
            @ApiResponse(responseCode = "403", description = "Forbidden (User is not an admin)."),
            @ApiResponse(responseCode = "404", description = "MFA configuration not found for user (setup not run)."),
            @ApiResponse(responseCode = "500", description = "Internal server error.")
    })
    @PostMapping("/verify")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<?> verifyMfa(
            @Parameter(description = "OTP for verification.", required = true)
            @Valid @RequestBody MfaVerificationRequest verificationRequest) {
        try {
            UUID adminUserId = getCurrentAdminUserId();
            logger.info("Admin MFA verification request for User ID: {} with OTP: {}", adminUserId, "****"); // Mask OTP in logs

            boolean success = adminMfaService.verifyAndEnableMfa(adminUserId, verificationRequest.otp);
            if (success) {
                return ResponseEntity.ok(Map.of("message", "MFA successfully verified and enabled."));
            } else {
                // AdminMfaService logs specific reason for failure (e.g. invalid OTP)
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid OTP or MFA setup issue."));
            }
        } catch (EntityNotFoundException e) {
            logger.warn("MFA verification failed for admin {}: {}", getCurrentAdminUsername(), e.getMessage());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        } catch (AdminMfaService.MfaOperationException e) {
            logger.error("MFA verification operation failed for admin {}: {}", getCurrentAdminUsername(), e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        } catch (ResponseStatusException rse) {
            throw rse;
        }
         catch (Exception e) {
            logger.error("Unexpected error during MFA verification for admin {}: {}", getCurrentAdminUsername(), e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error during MFA verification.", e);
        }
    }
}
