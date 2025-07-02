package com.mysillydreams.userservice.web.admin;

import com.mysillydreams.userservice.domain.UserEntity;
import com.mysillydreams.userservice.domain.inventory.InventoryProfile;
import com.mysillydreams.userservice.domain.vendor.VendorProfile;
import com.mysillydreams.userservice.dto.UserDto;
import com.mysillydreams.userservice.dto.inventory.InventoryProfileDto;
import com.mysillydreams.userservice.dto.vendor.VendorProfileDto;
import com.mysillydreams.userservice.repository.UserRepository;
import com.mysillydreams.userservice.repository.inventory.InventoryProfileRepository;
import com.mysillydreams.userservice.repository.vendor.VendorProfileRepository;
import com.mysillydreams.userservice.service.UserService; // For getById to use its EntityNotFound

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;


import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin") // Base path for all admin operations
@PreAuthorize("hasRole('ROLE_ADMIN')") // Class-level protection: only admins can access
@Tag(name = "Admin API (User Service)", description = "Administrative operations for managing users, vendors, and inventory.")
@SecurityRequirement(name = "bearerAuthUser") // Assuming User Service uses this scheme name
public class AdminController {

    private static final Logger logger = LoggerFactory.getLogger(AdminController.class);

    private final UserRepository userRepository;
    private final UserService userService; // For consistent "getById" logic including not found handling
    private final VendorProfileRepository vendorProfileRepository;
    private final InventoryProfileRepository inventoryProfileRepository;

    @Autowired
    public AdminController(UserRepository userRepository,
                           UserService userService,
                           VendorProfileRepository vendorProfileRepository,
                           InventoryProfileRepository inventoryProfileRepository) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.vendorProfileRepository = vendorProfileRepository;
        this.inventoryProfileRepository = inventoryProfileRepository;
    }

    // --- User Management ---

    @Operation(summary = "List all users (Paginated)", description = "Retrieves a paginated list of all user profiles.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved list of users.",
                     content = @Content(mediaType = "application/json", schema = @Schema(implementation = PageUserDto.class)))
    })
    @GetMapping("/users")
    public ResponseEntity<Page<UserDto>> listAllUsers(Pageable pageable) {
        logger.info("Admin request to list all users. Page: {}, Size: {}", pageable.getPageNumber(), pageable.getPageSize());
        Page<UserEntity> userPage = userRepository.findAll(pageable);
        Page<UserDto> userDtoPage = userPage.map(UserDto::from);
        return ResponseEntity.ok(userDtoPage);
    }

    // Helper DTO for Page<UserDto> Swagger documentation
    private static class PageUserDto extends PageImpl<UserDto> {
        public PageUserDto(List<UserDto> content, Pageable pageable, long total) {
            super(content, pageable, total);
        }
    }


    @Operation(summary = "Get any user profile by ID", description = "Retrieves a specific user profile by their UUID.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "User profile found.",
                     content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserDto.class))),
        @ApiResponse(responseCode = "404", description = "User not found.")
    })
    @GetMapping("/users/{userId}")
    public ResponseEntity<UserDto> getUserById(
            @Parameter(description = "UUID of the user to retrieve.", required = true) @PathVariable UUID userId) {
        logger.info("Admin request to get user by ID: {}", userId);
        try {
            UserEntity userEntity = userService.getById(userId); // Uses service's not-found handling
            return ResponseEntity.ok(UserDto.from(userEntity));
        } catch (EntityNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        }
    }

    @Operation(summary = "Delete any user profile by ID (Conceptual)",
               description = "Deletes a specific user profile. **Note: Actual implementation should consider soft delete, data anonymization, and impact on related entities.**")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "User profile deleted successfully."),
        @ApiResponse(responseCode = "404", description = "User not found.")
    })
    @DeleteMapping("/users/{userId}")
    public ResponseEntity<Void> deleteUserById(
            @Parameter(description = "UUID of the user to delete.", required = true) @PathVariable UUID userId) {
        logger.warn("Admin request to DELETE user by ID: {}. This is a conceptual endpoint.", userId);
        if (!userRepository.existsById(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found with ID: " + userId);
        }
        // TODO: Implement actual deletion logic (soft delete, anonymization, etc.)
        // For now, just a placeholder acknowledging the request.
        // userRepository.deleteById(userId); // This would be a hard delete.
        logger.info("Conceptual deletion for user ID: {}. No actual data changed in this placeholder.", userId);
        return ResponseEntity.noContent().build();
    }

    // --- Vendor Profile Management ---

    @Operation(summary = "List all vendor profiles (Paginated)", description = "Retrieves a paginated list of all vendor profiles.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved list of vendor profiles.",
                     content = @Content(mediaType = "application/json", schema = @Schema(implementation = PageVendorProfileDto.class)))
    })
    @GetMapping("/vendor-profiles")
    public ResponseEntity<Page<VendorProfileDto>> listAllVendorProfiles(Pageable pageable) {
        logger.info("Admin request to list all vendor profiles. Page: {}, Size: {}", pageable.getPageNumber(), pageable.getPageSize());
        Page<VendorProfile> vpPage = vendorProfileRepository.findAll(pageable);
        Page<VendorProfileDto> vpDtoPage = vpPage.map(VendorProfileDto::from);
        return ResponseEntity.ok(vpDtoPage);
    }
    private static class PageVendorProfileDto extends PageImpl<VendorProfileDto> {
        public PageVendorProfileDto(List<VendorProfileDto> content, Pageable pageable, long total) { super(content, pageable, total); }
    }


    // --- Inventory Profile Management ---
    @Operation(summary = "List all inventory profiles (Paginated)", description = "Retrieves a paginated list of all inventory profiles.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved list of inventory profiles.",
                     content = @Content(mediaType = "application/json", schema = @Schema(implementation = PageInventoryProfileDto.class)))
    })
    @GetMapping("/inventory-profiles")
    public ResponseEntity<Page<InventoryProfileDto>> listAllInventoryProfiles(Pageable pageable) {
        logger.info("Admin request to list all inventory profiles. Page: {}, Size: {}", pageable.getPageNumber(), pageable.getPageSize());
        Page<InventoryProfile> ipPage = inventoryProfileRepository.findAll(pageable);
        Page<InventoryProfileDto> ipDtoPage = ipPage.map(InventoryProfileDto::from);
        return ResponseEntity.ok(ipDtoPage);
    }
     private static class PageInventoryProfileDto extends PageImpl<InventoryProfileDto> {
        public PageInventoryProfileDto(List<InventoryProfileDto> content, Pageable pageable, long total) { super(content, pageable, total); }
    }


    // TODO: Add more admin endpoints as needed:
    // - Update any user field (e.g., PUT /admin/users/{userId}/email)
    // - Manage roles for any user (e.g., POST /admin/users/{userId}/roles)
    // - View/manage specific vendor documents or inventory items directly by their IDs.
    // - Trigger or manage KYC/Inventory workflows.
    // - Modify system configurations if User-Service held any mutable config.
}

```

I created a new sub-package `web/admin` for this controller for better organization, which was not explicitly in the plan but is good practice.
`user-service/src/main/java/com/mysillydreams/userservice/web/admin/.gitkeep`
