package com.mysillydreams.userservice.web.internal;

import com.mysillydreams.userservice.domain.UserEntity;
import com.mysillydreams.userservice.dto.UserDto; // Re-use for response, or create AdminUserProvisionResponseDto
import com.mysillydreams.userservice.service.UserService;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;


import java.util.Set; // For roles
import java.util.UUID;

@RestController
@RequestMapping("/internal/users") // Path for internal user operations
@Validated
@Hidden // Hide from public Swagger documentation
public class InternalAdminController {

    private static final Logger logger = LoggerFactory.getLogger(InternalAdminController.class);
    private static final String ROLE_ADMIN = "ROLE_ADMIN"; // Centralize if using constants later

    private final UserService userService;
    private final com.mysillydreams.userservice.repository.UserRepository userRepository; // Direct repo for role setting

    @Value("${app.internal-api.secret-key:}") // Same key as Auth-Service or a different one
    private String internalApiSecretKey;

    private static final String INTERNAL_API_KEY_HEADER = "X-Internal-API-Key";

    @Autowired
    public InternalAdminController(UserService userService, com.mysillydreams.userservice.repository.UserRepository userRepository) {
        this.userService = userService;
        this.userRepository = userRepository;
    }

    // DTO for the request
    @Data
    @NoArgsConstructor
    public static class AdminProvisionRequest {
        @NotNull(message = "Keycloak User ID cannot be null.")
        @Schema(description = "The User ID from Keycloak (UUID). This will be used as the primary ID for the UserEntity.", requiredMode = Schema.RequiredMode.REQUIRED)
        private UUID keycloakUserId; // This ID will become UserEntity.id

        @NotBlank(message = "Reference ID cannot be blank.")
        @Schema(description = "A business reference ID for the user.", example = "admin-ref-001", requiredMode = Schema.RequiredMode.REQUIRED)
        private String referenceId;

        @NotBlank(message = "Admin name cannot be blank.")
        @Schema(description = "Name of the admin user.", example = "Super Administrator", requiredMode = Schema.RequiredMode.REQUIRED)
        private String name;

        @NotBlank(message = "Admin email cannot be blank.")
        @Email(message = "A valid email format is required.")
        @Schema(description = "Email address of the admin user.", example = "admin@mysillydreams.com", requiredMode = Schema.RequiredMode.REQUIRED)
        private String email;

        // Other optional fields from UserDto can be added here if needed for provisioning
        // e.g., phone, dob (as string), gender, profilePicUrl
    }

    @Operation(summary = "Provision an Admin User Profile (Internal)",
               description = "Creates a local user profile in the User-Service for an existing Keycloak user who has been designated as an admin. " +
                             "The user's Keycloak ID is used as the primary ID. Assigns ROLE_ADMIN. " +
                             "This endpoint is for internal use ONLY and must be protected by a secret API key.",
               hidden = true)
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Admin user profile provisioned successfully.",
                         content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserDto.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request payload or user already exists with this ID/email/referenceId."),
            @ApiResponse(responseCode = "401", description = "Unauthorized (missing or invalid internal API key)."),
            @ApiResponse(responseCode = "500", description = "Internal server error.")
    })
    @PostMapping("/provision-admin")
    public ResponseEntity<UserDto> provisionAdminUser(
            @Parameter(description = "Secret API key for internal access.", required = true, in = io.swagger.v3.oas.annotations.enums.ParameterIn.HEADER)
            @RequestHeader(INTERNAL_API_KEY_HEADER) String apiKey,
            @Valid @RequestBody AdminProvisionRequest request) {

        // TODO: SECURITY - Replace this basic key check with a more robust mechanism like a Spring Security filter or HandlerInterceptor.
        if (internalApiSecretKey == null || internalApiSecretKey.isEmpty() || !internalApiSecretKey.equals(apiKey)) {
            logger.warn("Invalid or missing internal API key attempt on /provision-admin.");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or missing internal API key.");
        }

        try {
            logger.info("Internal request to provision admin user profile for Keycloak User ID: {}", request.getKeycloakUserId());

            // Check if user already exists by Keycloak ID (which is our UserEntity.id)
            if (userRepository.existsById(request.getKeycloakUserId())) {
                 logger.warn("Admin provisioning failed: UserEntity with ID {} already exists.", request.getKeycloakUserId());
                throw new IllegalArgumentException("User with Keycloak ID " + request.getKeycloakUserId() + " already has a profile.");
            }
            // Additional checks for existing referenceId or email might be needed depending on business rules
            // For example, using userService.checkEmailExists(request.getEmail()) if such a method existed.

            UserEntity adminUser = new UserEntity();
            adminUser.setId(request.getKeycloakUserId()); // Use Keycloak User ID as primary key
            adminUser.setReferenceId(request.getReferenceId());
            adminUser.setName(request.getName());
            adminUser.setEmail(request.getEmail());
            // Set other fields from request if they were added to AdminProvisionRequest DTO

            adminUser.getRoles().add(ROLE_ADMIN); // Assign ROLE_ADMIN
            // Potentially add other default roles an admin might have, e.g., ROLE_USER
            // adminUser.getRoles().add("ROLE_USER");

            UserEntity savedAdminUser = userRepository.save(adminUser); // Save directly using repository
            logger.info("Admin user profile provisioned successfully. User ID: {}, Reference ID: {}", savedAdminUser.getId(), savedAdminUser.getReferenceId());

            return ResponseEntity.status(HttpStatus.CREATED).body(UserDto.from(savedAdminUser));

        } catch (IllegalArgumentException e) {
            logger.warn("Admin provisioning failed: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Unexpected error during admin user provisioning for Keycloak User ID {}: {}", request.getKeycloakUserId(), e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error during admin provisioning.", e);
        }
    }
}
