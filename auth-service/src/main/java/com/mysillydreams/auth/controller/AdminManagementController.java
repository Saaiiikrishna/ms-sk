package com.mysillydreams.auth.controller;

import com.mysillydreams.auth.service.AdminManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

/**
 * Admin Management Controller for UI operations.
 * Handles the multi-step admin creation process and admin CRUD operations.
 */
@RestController
@RequestMapping("/admin/users")
@Validated
@Tag(name = "Admin Management API", description = "Endpoints for managing admin users with MFA verification")
public class AdminManagementController {

    private static final Logger logger = LoggerFactory.getLogger(AdminManagementController.class);

    private final AdminManagementService adminManagementService;

    @Autowired
    public AdminManagementController(AdminManagementService adminManagementService) {
        this.adminManagementService = adminManagementService;
    }

    /**
     * Step 1: Submit general details for new admin creation.
     * Requires current admin's MFA code for authorization.
     */
    @PostMapping("/admins/create/step1")
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "Initialize Admin Creation - Step 1", 
               description = "Submit general details for new admin user. Requires current admin's MFA code.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Admin creation initialized successfully"),
        @ApiResponse(responseCode = "401", description = "Invalid MFA code or unauthorized"),
        @ApiResponse(responseCode = "400", description = "Invalid request data")
    })
    public ResponseEntity<?> createAdminStep1(
            @Parameter(description = "Admin creation details with current admin's MFA code", required = true)
            @Valid @RequestBody AdminCreationStep1UIRequest request) {

        try {
            UUID currentAdminId = getCurrentAdminId();
            
            AdminManagementService.AdminCreationStepOneRequest serviceRequest = new AdminManagementService.AdminCreationStepOneRequest();
            serviceRequest.username = request.username;
            serviceRequest.email = request.email;
            serviceRequest.firstName = request.firstName;
            serviceRequest.lastName = request.lastName;
            serviceRequest.password = request.password;
            serviceRequest.currentAdminMfaCode = request.currentAdminMfaCode;

            AdminManagementService.AdminCreationStepOneResponse response = 
                adminManagementService.initializeAdminCreation(serviceRequest, currentAdminId);

            logger.info("Admin creation step 1 completed by admin: {} for session: {}", currentAdminId, response.sessionId);
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
     * Step 2: Setup MFA and generate QR code for new admin.
     */
    @PostMapping("/admins/create/step2")
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "Setup MFA - Step 2", 
               description = "Generate QR code for new admin's MFA setup")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "QR code generated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid session or request"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<?> createAdminStep2(
            @Parameter(description = "Session ID from step 1", required = true)
            @Valid @RequestBody AdminCreationStep2UIRequest request) {

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
     * Step 3: Verify new admin's MFA and complete creation.
     */
    @PostMapping("/admins/create/finalize")
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "Finalize Admin Creation - Step 3", 
               description = "Verify new admin's MFA code and complete creation")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Admin created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid session or MFA code"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<?> createAdminFinalize(
            @Parameter(description = "Session ID and new admin's MFA code", required = true)
            @Valid @RequestBody AdminCreationFinalizeUIRequest request) {

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
    @DeleteMapping("/admins/{adminId}")
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "Delete Admin User", 
               description = "Delete an admin user with MFA verification")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Admin deleted successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request or cannot delete self"),
        @ApiResponse(responseCode = "401", description = "Invalid MFA code or unauthorized")
    })
    public ResponseEntity<?> deleteAdmin(
            @PathVariable String adminId,
            @Parameter(description = "Current admin's MFA code for authorization", required = true)
            @Valid @RequestBody AdminDeleteUIRequest request) {

        try {
            UUID adminToDeleteId = UUID.fromString(adminId);
            UUID currentAdminId = getCurrentAdminId();

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

    /**
     * Get current admin's user ID from JWT token.
     */
    private UUID getCurrentAdminId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            String userId = jwt.getClaimAsString("sub");
            return UUID.fromString(userId);
        }
        throw new SecurityException("Unable to determine current admin user ID");
    }

    // UI DTO classes
    public static class AdminCreationStep1UIRequest {
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

    public static class AdminCreationStep2UIRequest {
        @NotBlank
        public String sessionId;
    }

    public static class AdminCreationFinalizeUIRequest {
        @NotBlank
        public String sessionId;
        @NotBlank
        public String newAdminMfaCode;
    }

    public static class AdminDeleteUIRequest {
        @NotBlank
        public String currentAdminMfaCode;
    }
}
