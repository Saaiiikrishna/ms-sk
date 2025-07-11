package com.mysillydreams.userservice.web.admin;

import com.mysillydreams.userservice.domain.UserEntity;
import com.mysillydreams.userservice.domain.inventory.InventoryProfile;
import com.mysillydreams.userservice.domain.vendor.VendorProfile;
import com.mysillydreams.userservice.dto.UserDto;
import com.mysillydreams.userservice.dto.inventory.InventoryProfileDto;
import com.mysillydreams.userservice.dto.vendor.VendorProfileDto;
import com.mysillydreams.userservice.repository.inventory.InventoryProfileRepository;
import com.mysillydreams.userservice.repository.vendor.VendorProfileRepository;
import com.mysillydreams.userservice.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.data.domain.PageImpl; // Keep for helper DTO
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;


import java.util.Collections; // Keep for helper DTO
import java.util.List;
import java.util.UUID;
// import java.util.stream.Collectors; // Not needed if service returns Page<UserDto>

@RestController
@RequestMapping("/admin")
@PreAuthorize("hasRole('ROLE_ADMIN')")
@Tag(name = "Admin API (User Service)", description = "Administrative operations for managing users, vendors, and inventory.")
@SecurityRequirement(name = "bearerAuthUser")
public class AdminController {

    private static final Logger logger = LoggerFactory.getLogger(AdminController.class);

    private final UserService userService;
    private final VendorProfileRepository vendorProfileRepository;
    private final InventoryProfileRepository inventoryProfileRepository;

    @Autowired
    public AdminController(UserService userService,
                           VendorProfileRepository vendorProfileRepository,
                           InventoryProfileRepository inventoryProfileRepository) {
        this.userService = userService;
        this.vendorProfileRepository = vendorProfileRepository;
        this.inventoryProfileRepository = inventoryProfileRepository;
    }

    // --- User Management ---

    @Operation(summary = "List all users (Paginated, including archived)",
               description = "Retrieves a paginated list of all user profiles, including those that are soft-deleted/archived.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved list of users.",
                     content = @Content(mediaType = "application/json", schema = @Schema(implementation = PageUserDto.class)))
    })
    @GetMapping("/users/all-including-archived")
    public ResponseEntity<Page<UserDto>> listAllUsersIncludingArchived(Pageable pageable) {
        logger.info("Admin request to list all users including archived. Page: {}, Size: {}", pageable.getPageNumber(), pageable.getPageSize());
        Page<UserDto> userDtoPage = userService.listAllUsersIncludingArchived(pageable);
        return ResponseEntity.ok(userDtoPage);
    }

    @Operation(summary = "List only active users (Paginated)",
               description = "Retrieves a paginated list of active user profiles (not soft-deleted).")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved list of active users.",
                     content = @Content(mediaType = "application/json", schema = @Schema(implementation = PageUserDto.class)))
    })
    @GetMapping("/users")
    public ResponseEntity<Page<UserDto>> listActiveUsers(Pageable pageable) {
        logger.info("Admin request to list active users. Page: {}, Size: {}", pageable.getPageNumber(), pageable.getPageSize());
        Page<UserDto> userDtoPage = userService.listActiveUsers(pageable); // Service already returns Page<UserDto>
        return ResponseEntity.ok(userDtoPage);
    }

    @Operation(summary = "List only archived users (Paginated)",
               description = "Retrieves a paginated list of soft-deleted/archived user profiles.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved list of archived users.",
                     content = @Content(mediaType = "application/json", schema = @Schema(implementation = PageUserDto.class)))
    })
    @GetMapping("/users/archived")
    public ResponseEntity<Page<UserDto>> listArchivedUsers(Pageable pageable) {
        logger.info("Admin request to list archived users. Page: {}, Size: {}", pageable.getPageNumber(), pageable.getPageSize());
        Page<UserDto> userDtoPage = userService.listArchivedUsers(pageable);
        return ResponseEntity.ok(userDtoPage);
    }

    private static class PageUserDto extends PageImpl<UserDto> { // For Swagger documentation
        public PageUserDto(List<UserDto> content, Pageable pageable, long total) { super(content, pageable, total); }
        public PageUserDto() { super(Collections.emptyList()); }
    }

    @Operation(summary = "Get any user profile by ID (includes archived)",
               description = "Retrieves a specific user profile by their UUID, including soft-deleted/archived users.")
    @GetMapping("/users/{userId}")
    public ResponseEntity<UserDto> getUserByIdIncludingArchived(
            @Parameter(description = "UUID of the user to retrieve.", required = true) @PathVariable UUID userId) {
        logger.info("Admin request to get user by ID (including archived): {}", userId);
        try {
            UserDto userDto = userService.getUserByIdIncludingArchived(userId);
            return ResponseEntity.ok(userDto);
        } catch (EntityNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        }
    }

    @Operation(summary = "Soft delete a user profile by ID",
               description = "Marks a specific user profile as inactive and archived. Related profiles (vendor, inventory, etc.) are also intended to be deactivated.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "User profile soft-deleted successfully.",
                     content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserDto.class))),
        @ApiResponse(responseCode = "404", description = "User not found.")
    })
    @DeleteMapping("/users/{userId}/soft")
    public ResponseEntity<UserDto> softDeleteUserById(
            @Parameter(description = "UUID of the user to soft-delete.", required = true) @PathVariable UUID userId) {
        logger.info("Admin request to soft-delete user by ID: {}", userId);
        try {
            UserDto userToSoftDelete = userService.getUserByIdIncludingArchived(userId); // Fetch by UUID
            userService.softDeleteUserByReferenceId(userToSoftDelete.getReferenceId()); // Soft delete by Ref ID (void method)
            // Return the user DTO with updated status
            UserDto updatedUser = userService.getUserByIdIncludingArchived(userId);
            return ResponseEntity.ok(updatedUser);
        } catch (EntityNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        }
    }

    @Operation(summary = "Force delete any user profile by ID (Conceptual - DANGEROUS)",
               description = "**DANGEROUS**: Permanently deletes a specific user profile and potentially related data. " +
                             "This is a conceptual endpoint. Actual implementation requires extreme care due to data integrity and audit trail implications.")
    @DeleteMapping("/users/{userId}/force")
    public ResponseEntity<Void> forceDeleteUserById(
            @Parameter(description = "UUID of the user to permanently delete.", required = true) @PathVariable UUID userId) {
        logger.warn("Admin request to FORCE DELETE user by ID: {}. This is a DANGEROUS operation and is currently a placeholder.", userId);
        if (!userService.userExistsIncludingArchived(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found with ID: " + userId + " for force delete.");
        }
        // TODO: Implement actual permanent deletion logic by calling a new service method e.g. userService.forceDeleteUser(userId);
        logger.info("Conceptual force deletion for user ID: {}. No actual data changed in this placeholder.", userId);
        return ResponseEntity.noContent().build();
    }

    // --- Vendor Profile Management ---
    @Operation(summary = "List all vendor profiles (Paginated)")
    @GetMapping("/vendor-profiles")
    public ResponseEntity<Page<VendorProfileDto>> listAllVendorProfiles(Pageable pageable) {
        logger.info("Admin request to list all vendor profiles. Page: {}, Size: {}", pageable.getPageNumber(), pageable.getPageSize());
        Page<VendorProfile> vpPage = vendorProfileRepository.findAll(pageable);
        Page<VendorProfileDto> vpDtoPage = vpPage.map(VendorProfileDto::from);
        return ResponseEntity.ok(vpDtoPage);
    }
    private static class PageVendorProfileDto extends PageImpl<VendorProfileDto> {
        public PageVendorProfileDto(List<VendorProfileDto> content, Pageable pageable, long total) { super(content, pageable, total); }
        public PageVendorProfileDto() {super(Collections.emptyList());}
    }

    // --- Inventory Profile Management ---
    @Operation(summary = "List all inventory profiles (Paginated)")
    @GetMapping("/inventory-profiles")
    public ResponseEntity<Page<InventoryProfileDto>> listAllInventoryProfiles(Pageable pageable) {
        logger.info("Admin request to list all inventory profiles. Page: {}, Size: {}", pageable.getPageNumber(), pageable.getPageSize());
        Page<InventoryProfile> ipPage = inventoryProfileRepository.findAll(pageable);
        Page<InventoryProfileDto> ipDtoPage = ipPage.map(InventoryProfileDto::from);
        return ResponseEntity.ok(ipDtoPage);
    }
     private static class PageInventoryProfileDto extends PageImpl<InventoryProfileDto> {
        public PageInventoryProfileDto(List<InventoryProfileDto> content, Pageable pageable, long total) { super(content, pageable, total); }
        public PageInventoryProfileDto() {super(Collections.emptyList());}
    }
}
