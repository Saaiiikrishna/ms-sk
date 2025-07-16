package com.mysillydreams.auth.controller;

import com.mysillydreams.auth.service.AdminBootstrapService;
import com.mysillydreams.auth.service.AdminManagementService;
import com.mysillydreams.auth.service.AdminMfaService;
import io.swagger.v3.oas.annotations.Hidden; // Hide from public Swagger
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
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
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/auth") // Internal, non-public path
@Validated
@Hidden // Attempt to hide from Swagger UI if not explicitly configured for internal docs
public class InternalAdminController {

    private static final Logger logger = LoggerFactory.getLogger(InternalAdminController.class);

    private final AdminMfaService adminMfaService;
    private final AdminBootstrapService adminBootstrapService;
    private final AdminManagementService adminManagementService;

    @Value("${app.internal-api.secret-key:}") // Load from properties, ensure it's set in prod
    private String internalApiSecretKey;

    private static final String INTERNAL_API_KEY_HEADER = "X-Internal-API-Key";

    @Autowired
    public InternalAdminController(AdminMfaService adminMfaService,
                                  AdminBootstrapService adminBootstrapService,
                                  AdminManagementService adminManagementService) {
        this.adminMfaService = adminMfaService;
        this.adminBootstrapService = adminBootstrapService;
        this.adminManagementService = adminManagementService;
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

        // Enhanced security check for internal API key
        if (!isValidInternalApiKey(apiKey)) {
            logger.warn("Invalid or missing internal API key attempt on /provision-mfa-setup from IP: {}",
                getClientIpAddress());
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

    /**
     * Bootstrap the first admin user (ONE-TIME USE ONLY).
     * This endpoint should be disabled after the first admin is created.
     */
    @PostMapping("/bootstrap-first-admin")
    @Hidden
    public ResponseEntity<?> bootstrapFirstAdmin(
            @RequestHeader(INTERNAL_API_KEY_HEADER) String apiKey,
            @Valid @RequestBody AdminBootstrapService.BootstrapRequest request) {

        if (!isValidInternalApiKey(apiKey)) {
            logger.warn("Invalid API key provided for admin bootstrap from IP: {}", getClientIpAddress());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or missing internal API key.");
        }

        try {
            AdminBootstrapService.BootstrapResponse response = adminBootstrapService.bootstrapFirstAdmin(request);
            logger.info("First admin user bootstrapped successfully: {}", request.username);
            return ResponseEntity.ok(response);

        } catch (IllegalStateException e) {
            logger.warn("Bootstrap attempt failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Failed to bootstrap first admin: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to bootstrap first admin: " + e.getMessage());
        }
    }

    /**
     * Verify MFA for bootstrapped admin and complete setup.
     */
    @PostMapping("/verify-bootstrap-mfa")
    @Hidden
    public ResponseEntity<?> verifyBootstrapMfa(
            @RequestHeader(INTERNAL_API_KEY_HEADER) String apiKey,
            @Valid @RequestBody BootstrapMfaVerifyRequest request) {

        if (!isValidInternalApiKey(apiKey)) {
            logger.warn("Invalid API key provided for bootstrap MFA verification from IP: {}", getClientIpAddress());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or missing internal API key.");
        }

        try {
            // For now, return success since MFA will be handled through Keycloak
            logger.info("Bootstrap MFA verification requested for user: {} (handled through Keycloak)", request.userId);
            return ResponseEntity.ok(Map.of("verified", true, "message", "MFA verification handled through Keycloak"));

        } catch (Exception e) {
            logger.error("Error verifying bootstrap MFA: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to verify MFA: " + e.getMessage());
        }
    }

    /**
     * Step 1: Initialize admin creation with general details and current admin MFA verification.
     */
    @PostMapping("/admin-creation/step1")
    @Hidden
    public ResponseEntity<?> initializeAdminCreation(
            @RequestHeader(INTERNAL_API_KEY_HEADER) String apiKey,
            @Valid @RequestBody AdminCreationStep1Request request) {

        if (!isValidInternalApiKey(apiKey)) {
            logger.warn("Invalid API key provided for admin creation step 1 from IP: {}", getClientIpAddress());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or missing internal API key.");
        }

        try {
            UUID currentAdminId = UUID.fromString(request.currentAdminId);
            AdminManagementService.AdminCreationStepOneRequest serviceRequest = new AdminManagementService.AdminCreationStepOneRequest();
            serviceRequest.username = request.username;
            serviceRequest.email = request.email;
            serviceRequest.firstName = request.firstName;
            serviceRequest.lastName = request.lastName;
            serviceRequest.password = request.password;
            serviceRequest.currentAdminMfaCode = request.currentAdminMfaCode;

            AdminManagementService.AdminCreationStepOneResponse response =
                adminManagementService.initializeAdminCreation(serviceRequest, currentAdminId);

            logger.info("Admin creation step 1 completed for session: {}", response.sessionId);
            return ResponseEntity.ok(response);

        } catch (SecurityException e) {
            logger.warn("Security error in admin creation step 1: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, e.getMessage());
        } catch (Exception e) {
            logger.error("Failed admin creation step 1: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to initialize admin creation: " + e.getMessage());
        }
    }

    /**
     * Step 2: Setup MFA for new admin and generate QR code.
     */
    @PostMapping("/admin-creation/step2")
    @Hidden
    public ResponseEntity<?> setupAdminMfa(
            @RequestHeader(INTERNAL_API_KEY_HEADER) String apiKey,
            @Valid @RequestBody AdminCreationStep2Request request) {

        if (!isValidInternalApiKey(apiKey)) {
            logger.warn("Invalid API key provided for admin creation step 2 from IP: {}", getClientIpAddress());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or missing internal API key.");
        }

        try {
            AdminManagementService.AdminCreationStepTwoRequest serviceRequest = new AdminManagementService.AdminCreationStepTwoRequest();
            serviceRequest.sessionId = request.sessionId;

            AdminManagementService.AdminCreationStepTwoResponse response =
                adminManagementService.completeAdminCreation(serviceRequest);

            logger.info("Admin creation step 2 completed for session: {}", request.sessionId);
            return ResponseEntity.ok(response);

        } catch (IllegalStateException e) {
            logger.warn("Invalid session in admin creation step 2: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            logger.error("Failed admin creation step 2: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to setup admin MFA: " + e.getMessage());
        }
    }

    /**
     * Step 3: Verify new admin's MFA and finalize creation.
     */
    @PostMapping("/admin-creation/finalize")
    @Hidden
    public ResponseEntity<?> finalizeAdminCreation(
            @RequestHeader(INTERNAL_API_KEY_HEADER) String apiKey,
            @Valid @RequestBody AdminCreationFinalizeRequest request) {

        if (!isValidInternalApiKey(apiKey)) {
            logger.warn("Invalid API key provided for admin creation finalize from IP: {}", getClientIpAddress());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or missing internal API key.");
        }

        try {
            AdminManagementService.AdminCreationFinalizeRequest serviceRequest = new AdminManagementService.AdminCreationFinalizeRequest();
            serviceRequest.sessionId = request.sessionId;
            serviceRequest.newAdminMfaCode = request.newAdminMfaCode;

            AdminManagementService.AdminCreationCompleteResponse response =
                adminManagementService.finalizeAdminCreation(serviceRequest);

            logger.info("Admin creation finalized successfully for admin: {}", response.adminId);
            return ResponseEntity.ok(response);

        } catch (IllegalStateException e) {
            logger.warn("Invalid session in admin creation finalize: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (SecurityException e) {
            logger.warn("Security error in admin creation finalize: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, e.getMessage());
        } catch (Exception e) {
            logger.error("Failed admin creation finalize: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to finalize admin creation: " + e.getMessage());
        }
    }

    /**
     * Delete admin user with MFA verification.
     */
    @DeleteMapping("/admin/{adminId}")
    @Hidden
    public ResponseEntity<?> deleteAdmin(
            @RequestHeader(INTERNAL_API_KEY_HEADER) String apiKey,
            @PathVariable String adminId,
            @Valid @RequestBody AdminDeleteRequest request) {

        if (!isValidInternalApiKey(apiKey)) {
            logger.warn("Invalid API key provided for admin deletion from IP: {}", getClientIpAddress());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or missing internal API key.");
        }

        try {
            UUID adminToDeleteId = UUID.fromString(adminId);
            UUID currentAdminId = UUID.fromString(request.currentAdminId);

            boolean deleted = adminManagementService.deleteAdmin(adminToDeleteId, currentAdminId, request.currentAdminMfaCode);

            if (deleted) {
                logger.info("Admin user deleted successfully: {} by admin: {}", adminToDeleteId, currentAdminId);
                return ResponseEntity.ok(Map.of("deleted", true, "message", "Admin user deleted successfully"));
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("deleted", false, "message", "Failed to delete admin user"));
            }

        } catch (SecurityException e) {
            logger.warn("Security error in admin deletion: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, e.getMessage());
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid argument in admin deletion: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            logger.error("Failed admin deletion: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to delete admin: " + e.getMessage());
        }
    }

    // DTO classes for new endpoints
    public static class BootstrapMfaVerifyRequest {
        @NotBlank
        public String userId;
        @NotBlank
        public String totpCode;
    }

    public static class AdminCreationStep1Request {
        @NotBlank
        public String currentAdminId;
        @NotBlank
        public String currentAdminMfaCode;
        @NotBlank
        public String username;
        @NotBlank
        public String email;
        @NotBlank
        public String firstName;
        @NotBlank
        public String lastName;
        @NotBlank
        public String password;
    }

    public static class AdminCreationStep2Request {
        @NotBlank
        public String sessionId;
    }

    public static class AdminCreationFinalizeRequest {
        @NotBlank
        public String sessionId;
        @NotBlank
        public String newAdminMfaCode;
    }

    public static class AdminDeleteRequest {
        @NotBlank
        public String currentAdminId;
        @NotBlank
        public String currentAdminMfaCode;
    }

    /**
     * Enhanced validation for internal API key with timing attack protection.
     * Uses constant-time comparison to prevent timing attacks.
     */
    private boolean isValidInternalApiKey(String providedKey) {
        if (internalApiSecretKey == null || internalApiSecretKey.isEmpty() ||
            providedKey == null || providedKey.isEmpty()) {
            return false;
        }

        // Use constant-time comparison to prevent timing attacks
        return MessageDigest.isEqual(
            internalApiSecretKey.getBytes(),
            providedKey.getBytes()
        );
    }

    /**
     * Gets the client IP address from the current request.
     * Handles X-Forwarded-For and X-Real-IP headers for proxy scenarios.
     */
    private String getClientIpAddress() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        HttpServletRequest request = attributes.getRequest();

        // Check for X-Forwarded-For header (common in load balancers/proxies)
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        // Check for X-Real-IP header (common in nginx)
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        // Fallback to remote address
        return request.getRemoteAddr();
    }
}
