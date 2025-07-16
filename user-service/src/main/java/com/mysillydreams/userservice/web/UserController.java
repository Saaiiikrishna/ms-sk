package com.mysillydreams.userservice.web;

import com.mysillydreams.userservice.domain.UserEntity;
import com.mysillydreams.userservice.dto.UserDto;
import com.mysillydreams.userservice.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;
import java.util.Map; // For simpler error responses if not using GlobalExceptionHandler for all

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID; // For potential future use with primary ID

@RestController
@RequestMapping("/users")
@Validated
@Tag(name = "User API", description = "Endpoints for managing user profiles.")
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);
    private final UserService userService;

    @Autowired
    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "Create a new user profile",
               description = "Creates a new user profile with the provided information. Sensitive fields will be encrypted.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "User profile created successfully",
                         content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserDto.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request payload (e.g., validation error, existing email)",
                         content = @Content(mediaType = "application/json", schema = @Schema(implementation = Map.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error (e.g., encryption failure)",
                         content = @Content(mediaType = "application/json", schema = @Schema(implementation = Map.class)))
    })
    @PostMapping
    public ResponseEntity<UserDto> createUser(
            @Parameter(description = "User data for creating a new profile", required = true,
                       schema = @Schema(implementation = UserDto.class))
            @Valid @RequestBody UserDto userDto) {
        try {
            logger.info("Received request to create user with email: {}", userDto.getEmail());
            UserEntity createdUser = userService.createUser(userDto);
            UserDto responseDto = UserDto.from(createdUser);
            logger.info("User created successfully with referenceId: {}", responseDto.getReferenceId());
            return ResponseEntity.status(HttpStatus.CREATED).body(responseDto);
        } catch (IllegalArgumentException e) {
            // This could be for "email already exists" or "invalid DOB format" from service layer
            logger.warn("Failed to create user due to bad request: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        } catch (Exception e) { // Catch VaultException or other runtime issues from service
            logger.error("Internal error while creating user: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error creating user profile.", e);
        }
    }

    @Operation(summary = "Get user profile by reference ID",
               description = "Retrieves a user profile using their unique business reference ID. Sensitive fields are returned decrypted.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User profile found",
                         content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserDto.class))),
            @ApiResponse(responseCode = "404", description = "User not found with the given reference ID",
                         content = @Content(mediaType = "application/json", schema = @Schema(implementation = Map.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error (e.g., decryption failure)",
                         content = @Content(mediaType = "application/json", schema = @Schema(implementation = Map.class)))
    })
    @GetMapping("/{referenceId}")
    public ResponseEntity<UserDto> getUserByReferenceId(
            @Parameter(description = "Business reference ID of the user", required = true, example = "some-unique-ref-id-123")
            @PathVariable String referenceId) {
        try {
            logger.debug("Received request to get user by referenceId: {}", referenceId);
            UserEntity userEntity = userService.getByReferenceId(referenceId);
            UserDto responseDto = UserDto.from(userEntity);
            logger.debug("User found with referenceId: {}", referenceId);
            return ResponseEntity.ok(responseDto);
        } catch (EntityNotFoundException e) {
            logger.warn("User not found with referenceId {}: {}", referenceId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        } catch (Exception e) { // Catch VaultException or other runtime issues from service/converter
            logger.error("Internal error while retrieving user {}: {}", referenceId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error retrieving user profile.", e);
        }
    }

    @Operation(summary = "Update user profile by reference ID",
           description = "Updates an existing user's profile information. Email updates are generally not allowed via this endpoint.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "User profile updated successfully",
                     content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserDto.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request payload or business rule violation (e.g., trying to update email)",
                     content = @Content(mediaType = "application/json", schema = @Schema(implementation = Map.class))),
        @ApiResponse(responseCode = "404", description = "User not found with the given reference ID",
                     content = @Content(mediaType = "application/json", schema = @Schema(implementation = Map.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error (e.g., encryption/decryption failure)",
                     content = @Content(mediaType = "application/json", schema = @Schema(implementation = Map.class)))
    })
    @PutMapping("/{referenceId}")
    public ResponseEntity<UserDto> updateUserProfile(
        @Parameter(description = "Business reference ID of the user to update", required = true)
        @PathVariable String referenceId,
        @Parameter(description = "User data for updating the profile", required = true,
                   schema = @Schema(implementation = UserDto.class))
        @Valid @RequestBody UserDto userDto) {
        try {
            logger.info("Received request to update user with referenceId: {}", referenceId);
            UserEntity updatedUser = userService.updateUser(referenceId, userDto);
            UserDto responseDto = UserDto.from(updatedUser);
            logger.info("User updated successfully with referenceId: {}", referenceId);
            return ResponseEntity.ok(responseDto);
        } catch (EntityNotFoundException e) {
            logger.warn("User not found with referenceId {} for update: {}", referenceId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        } catch (IllegalArgumentException e) { // For validation errors from service like invalid DOB or trying to update email
            logger.warn("Failed to update user {} due to bad request: {}", referenceId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        } catch (UnsupportedOperationException e) { // e.g. if email update was attempted and denied
             logger.warn("Unsupported update operation for user {}: {}", referenceId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
        catch (Exception e) {
            logger.error("Internal error while updating user {}: {}", referenceId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error updating user profile.", e);
        }
    }


    /**
     * Get user sessions (placeholder implementation)
     */
    @GetMapping("/{referenceId}/sessions")
    @Operation(summary = "Get user sessions", description = "Retrieve all active sessions for a user")
    public ResponseEntity<?> getUserSessions(
            @Parameter(description = "Business reference ID of the user", required = true)
            @PathVariable String referenceId) {
        try {
            logger.debug("Received request to get sessions for user: {}", referenceId);
            // TODO: Implement session management service
            return ResponseEntity.ok(Map.of(
                "message", "Session management not yet implemented",
                "userReferenceId", referenceId,
                "sessions", List.of()
            ));
        } catch (Exception e) {
            logger.error("Error retrieving sessions for user {}: {}", referenceId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error retrieving user sessions", e);
        }
    }

    /**
     * Delete user (GDPR Right-to-Be-Forgotten)
     */
    @DeleteMapping("/{referenceId}")
    @Operation(summary = "Delete user", description = "Delete user data for GDPR compliance")
    @PreAuthorize("hasRole('ADMIN') or authentication.name == #referenceId")
    public ResponseEntity<?> deleteUser(
            @Parameter(description = "Business reference ID of the user to delete", required = true)
            @PathVariable String referenceId) {
        try {
            logger.info("Received request to delete user: {}", referenceId);
            // TODO: Implement proper GDPR deletion with audit trail
            return ResponseEntity.ok(Map.of(
                "message", "User deletion not yet implemented - requires GDPR compliance implementation",
                "userReferenceId", referenceId
            ));
        } catch (Exception e) {
            logger.error("Error deleting user {}: {}", referenceId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error deleting user", e);
        }
    }

    /**
     * Manage user addresses (placeholder implementation)
     */
    @PostMapping("/{referenceId}/addresses")
    @Operation(summary = "Add user address", description = "Add a new address for the user")
    public ResponseEntity<?> addUserAddress(
            @Parameter(description = "Business reference ID of the user", required = true)
            @PathVariable String referenceId,
            @RequestBody Map<String, Object> addressData) {
        try {
            logger.info("Received request to add address for user: {}", referenceId);
            // TODO: Implement address management
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message", "Address management not yet implemented",
                "userReferenceId", referenceId,
                "addressData", addressData
            ));
        } catch (Exception e) {
            logger.error("Error adding address for user {}: {}", referenceId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error adding user address", e);
        }
    }

    /**
     * Manage user payment info (placeholder implementation)
     */
    @PostMapping("/{referenceId}/payment-info")
    @Operation(summary = "Add user payment info", description = "Add payment information for the user")
    public ResponseEntity<?> addUserPaymentInfo(
            @Parameter(description = "Business reference ID of the user", required = true)
            @PathVariable String referenceId,
            @RequestBody Map<String, Object> paymentData) {
        try {
            logger.info("Received request to add payment info for user: {}", referenceId);
            // TODO: Implement payment info management with proper encryption
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message", "Payment info management not yet implemented",
                "userReferenceId", referenceId,
                "note", "Payment data will be encrypted when implemented"
            ));
        } catch (Exception e) {
            logger.error("Error adding payment info for user {}: {}", referenceId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error adding payment info", e);
        }
    }
}
