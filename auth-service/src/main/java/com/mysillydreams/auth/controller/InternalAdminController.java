package com.mysillydreams.auth.controller;

import com.mysillydreams.auth.service.AdminMfaService;
import io.swagger.v3.oas.annotations.Hidden; // Hide from public Swagger
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;


import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/auth") // Internal, non-public path
@Validated
@Hidden // Attempt to hide from Swagger UI if not explicitly configured for internal docs
public class InternalAdminController {

    private static final Logger logger = LoggerFactory.getLogger(InternalAdminController.class);

    private final AdminMfaService adminMfaService;

    @Value("${app.internal-api.secret-key:}") // Load from properties, ensure it's set in prod
    private String internalApiSecretKey;

    private static final String INTERNAL_API_KEY_HEADER = "X-Internal-API-Key";

    @Autowired
    public InternalAdminController(AdminMfaService adminMfaService) {
        this.adminMfaService = adminMfaService;
    }

    // DTO for request
    public static class ProvisionMfaRequest {
        @NotNull(message = "Admin User ID cannot be null")
        public UUID adminUserId;
        @NotBlank(message = "Admin username cannot be blank (for QR code label)")
        public String adminUsername; // For QR code label
    }


    @Operation(summary = "Provision MFA Setup for an Admin User (Internal)",
               description = "Generates and stores a TOTP secret for an existing admin user, marking MFA as not yet enabled. " +
                             "This endpoint is for internal use ONLY and must be protected by a secret API key.",
               hidden = true) // Another attempt to hide
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "MFA setup details generated successfully for out-of-band delivery."),
            @ApiResponse(responseCode = "400", description = "Invalid request payload."),
            @ApiResponse(responseCode = "401", description = "Unauthorized (missing or invalid internal API key)."),
            @ApiResponse(responseCode = "500", description = "Internal server error during MFA setup.")
    })
    @PostMapping("/provision-mfa-setup")
    public ResponseEntity<AdminMfaService.MfaSetupResponse> provisionMfaForAdmin(
            @Parameter(description = "Secret API key for internal access.", required = true, in = io.swagger.v3.oas.annotations.enums.ParameterIn.HEADER)
            @RequestHeader(INTERNAL_API_KEY_HEADER) String apiKey,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Details of the admin user for whom to provision MFA.", required = true,
                    content = @Content(schema = @Schema(implementation = ProvisionMfaRequest.class)))
            @Valid @RequestBody ProvisionMfaRequest request) {

        // TODO: SECURITY - Replace this basic key check with a more robust mechanism like a Spring Security filter or HandlerInterceptor.
        if (internalApiSecretKey == null || internalApiSecretKey.isEmpty() || !internalApiSecretKey.equals(apiKey)) {
            logger.warn("Invalid or missing internal API key attempt on /provision-mfa-setup.");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or missing internal API key.");
        }

        try {
            logger.info("Internal request to provision MFA setup for admin User ID: {}", request.adminUserId);
            AdminMfaService.MfaSetupResponse setupResponse = adminMfaService.generateMfaSetup(request.adminUserId, request.adminUsername);
            // The response contains the raw secret and QR code URI.
            // This should be handled very carefully (e.g., logged to a secure audit log, or directly used
            // by an automated provisioning system to deliver to the admin out-of-band).
            // For this example, we return it, but in a real system, this might be different.
            logger.info("MFA setup details generated for admin User ID {}. Raw secret and QR URI will be returned (handle securely!).", request.adminUserId);
            return ResponseEntity.ok(setupResponse);
        } catch (AdminMfaService.MfaOperationException e) {
            logger.error("MFA provisioning failed for admin User ID {}: {}", request.adminUserId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Unexpected error during MFA provisioning for admin User ID {}: {}", request.adminUserId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error during MFA provisioning.", e);
        }
    }
}
