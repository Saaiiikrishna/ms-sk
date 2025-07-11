package com.mysillydreams.userservice.web.inventory;

import com.mysillydreams.userservice.domain.inventory.InventoryProfile;
import com.mysillydreams.userservice.dto.inventory.InventoryProfileDto;
// Assuming RegisterInventoryUserRequest is empty as per scaffold, so not explicitly used if not needed
// import com.mysillydreams.userservice.dto.inventory.RegisterInventoryUserRequest;
import com.mysillydreams.userservice.service.inventory.InventoryOnboardingService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize; // Added
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/inventory-onboarding")
@Validated
@Tag(name = "Inventory Onboarding API", description = "Endpoints for onboarding users to the inventory system.")
// TODO: SECURITY - Add more fine-grained authorization.
// For example, only admins or users with a specific pre-onboarding role can call /register.
// Access to /profile should be limited to the user themselves or an admin.
// This currently relies on API Gateway to authenticate and pass a valid X-User-Id.
public class InventoryOnboardingController {

    private static final Logger logger = LoggerFactory.getLogger(InventoryOnboardingController.class);

    private final InventoryOnboardingService inventoryOnboardingService;

    @Autowired
    public InventoryOnboardingController(InventoryOnboardingService inventoryOnboardingService) {
        this.inventoryOnboardingService = inventoryOnboardingService;
    }

    @Operation(summary = "Register an existing user as an inventory user",
               description = "Creates an inventory profile for the user specified by 'X-User-Id', " +
                             "assigning them the ROLE_INVENTORY_USER. If the user already has an inventory profile, " +
                             "this operation might return the existing profile or an appropriate status.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Inventory user registered and profile created successfully.",
                         content = @Content(mediaType = "application/json", schema = @Schema(implementation = InventoryProfileDto.class))),
            @ApiResponse(responseCode = "200", description = "User is already an inventory user; existing profile returned."), // As per scaffold controller logic
            @ApiResponse(responseCode = "400", description = "Invalid request (e.g., user already has profile - if treated as error by service).",
                         content = @Content(mediaType = "application/json", schema = @Schema(implementation = Map.class))),
            @ApiResponse(responseCode = "404", description = "User not found for the given X-User-Id.",
                         content = @Content(mediaType = "application/json", schema = @Schema(implementation = Map.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error.",
                         content = @Content(mediaType = "application/json", schema = @Schema(implementation = Map.class)))
    })
    @PostMapping("/register") // Changed from @PostMapping to be specific based on scaffold
    public ResponseEntity<InventoryProfileDto> registerInventoryUser(
            @Parameter(description = "UUID of the authenticated user to be registered as an inventory user.", required = true)
            @RequestHeader("X-User-Id") UUID userId) {
        // The scaffold for InventoryOnboardingController.register has:
        // InventoryProfileDto dto = svc.getProfile(userId); if (dto!=null) return ok(dto);
        // This implies checking if profile exists first.
        // And then InventoryProfile p = svc.register(userId); return status(CREATED)...
        // This is slightly different from VendorOnboarding which threw an error if profile existed.
        // Let's follow the scaffold for InventoryOnboardingController.
        try {
            logger.info("Received inventory user registration request for User ID: {}", userId);
            try {
                InventoryProfileDto existingProfileDto = inventoryOnboardingService.getInventoryProfileByUserId(userId);
                // If getInventoryProfileByUserId doesn't throw an exception, a profile exists.
                logger.info("User ID: {} already has an inventory profile. Returning existing profile.", userId);
                return ResponseEntity.ok(existingProfileDto);
            } catch (EntityNotFoundException enfe) {
                // This is expected if the user is not yet an inventory user. Proceed to register.
                logger.info("No existing inventory profile for User ID: {}. Proceeding with registration.", userId);
                InventoryProfile newProfile = inventoryOnboardingService.registerInventoryUser(userId);
                InventoryProfileDto responseDto = InventoryProfileDto.from(newProfile);
                logger.info("Inventory user registered successfully for User ID: {}. Profile ID: {}", userId, responseDto.getId());
                return ResponseEntity.status(HttpStatus.CREATED).body(responseDto);
            }
        } catch (EntityNotFoundException e) { // This would be from userService.getById inside registerInventoryUser if user itself not found
            logger.warn("Inventory registration failed: User not found for ID {}. Error: {}", userId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + e.getMessage(), e);
        } catch (IllegalStateException e) { // This might be thrown by service if there's another check
            logger.warn("Inventory registration failed for User ID {}: {}", userId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Internal error during inventory user registration for User ID {}: {}", userId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error during inventory user registration.", e);
        }
    }

    @Operation(summary = "Get inventory profile for a user",
               description = "Retrieves the inventory profile for the user specified by 'X-User-Id'.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Inventory profile found.",
                         content = @Content(mediaType = "application/json", schema = @Schema(implementation = InventoryProfileDto.class))),
            @ApiResponse(responseCode = "404", description = "Inventory profile not found for the user.",
                         content = @Content(mediaType = "application/json", schema = @Schema(implementation = Map.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error.")
    })
    @GetMapping("/profile") // Changed from @GetMapping to be specific based on scaffold
    public ResponseEntity<InventoryProfileDto> getInventoryProfile(
            @Parameter(description = "UUID of the authenticated inventory user.", required = true)
            @RequestHeader("X-User-Id") UUID userId) {
        try {
            logger.debug("Received request to get inventory profile for User ID: {}", userId);
            InventoryProfileDto profileDto = inventoryOnboardingService.getInventoryProfileByUserId(userId);
            logger.debug("Inventory profile found for User ID: {}", userId);
            return ResponseEntity.ok(profileDto);
        } catch (EntityNotFoundException e) {
            logger.warn("Get inventory profile failed: Not found for User ID {}. Error: {}", userId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Internal error while fetching inventory profile for User ID {}: {}", userId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error fetching inventory profile.", e);
        }
    }
}
