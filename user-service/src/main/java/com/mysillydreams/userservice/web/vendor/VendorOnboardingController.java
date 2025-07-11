package com.mysillydreams.userservice.web.vendor;

import com.mysillydreams.userservice.domain.UserEntity;
import com.mysillydreams.userservice.domain.vendor.VendorProfile;
import com.mysillydreams.userservice.dto.vendor.PresignedUrlResponse;
import com.mysillydreams.userservice.dto.vendor.RegisterVendorRequest;
import com.mysillydreams.userservice.dto.vendor.VendorProfileDto;
import com.mysillydreams.userservice.service.UserService;
import com.mysillydreams.userservice.service.vendor.DocumentService;
import com.mysillydreams.userservice.service.vendor.VendorOnboardingService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.persistence.EntityNotFoundException; // Standard JPA
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/vendor-onboarding") // Base path for vendor onboarding related endpoints
@Validated
@Tag(name = "Vendor Onboarding API", description = "Endpoints for vendor registration and KYC document management.")
public class VendorOnboardingController {

    private static final Logger logger = LoggerFactory.getLogger(VendorOnboardingController.class);

    private final VendorOnboardingService vendorOnboardingService;
    private final DocumentService documentService;
    private final UserService userService; // To fetch UserEntity based on X-User-Id

    @Autowired
    public VendorOnboardingController(VendorOnboardingService vendorOnboardingService,
                                      DocumentService documentService,
                                      UserService userService) {
        this.vendorOnboardingService = vendorOnboardingService;
        this.documentService = documentService;
        this.userService = userService;
    }

    @Operation(summary = "Register a new vendor",
               description = "Registers a new vendor profile associated with an existing authenticated user. " +
                             "The user ID must be passed in the 'X-User-Id' header.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Vendor registered successfully, KYC process initiated.",
                         content = @Content(mediaType = "application/json", schema = @Schema(implementation = VendorProfileDto.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request (e.g., validation error, user already a vendor).",
                         content = @Content(mediaType = "application/json", schema = @Schema(implementation = Map.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized (if X-User-Id is missing or invalid, though this should be caught by gateway/auth filter ideally)."),
            @ApiResponse(responseCode = "404", description = "User not found for the given X-User-Id.",
                         content = @Content(mediaType = "application/json", schema = @Schema(implementation = Map.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error.",
                         content = @Content(mediaType = "application/json", schema = @Schema(implementation = Map.class)))
    })
    @PostMapping("/register") // Changed from @PostMapping to be more specific
    public ResponseEntity<VendorProfileDto> registerVendor(
            @Parameter(description = "Vendor registration details.", required = true)
            @Valid @RequestBody RegisterVendorRequest request,
            @Parameter(description = "UUID of the authenticated user registering as a vendor.", required = true, example = "d290f1ee-6c54-4b01-90e6-d701748f0851")
            @RequestHeader("X-User-Id") UUID userId) { // Assuming X-User-Id is validated upstream (e.g., by API Gateway after token auth)
        try {
            logger.info("Received vendor registration request for User ID: {}", userId);
            UserEntity user = userService.getById(userId); // Fetches the UserEntity
            VendorProfile vendorProfile = vendorOnboardingService.registerVendor(request, user);
            logger.info("Vendor successfully registered for User ID: {}. VendorProfile ID: {}", userId, vendorProfile.getId());
            return ResponseEntity.status(HttpStatus.CREATED).body(VendorProfileDto.from(vendorProfile));
        } catch (EntityNotFoundException e) {
            logger.warn("Vendor registration failed: User not found for ID {}. Error: {}", userId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + e.getMessage(), e);
        } catch (IllegalStateException e) { // e.g., user already a vendor
            logger.warn("Vendor registration failed for User ID {}: {}", userId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Internal error during vendor registration for User ID {}: {}", userId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error during vendor registration.", e);
        }
    }

    @Operation(summary = "Get vendor profile",
               description = "Retrieves the vendor profile for the authenticated user specified by 'X-User-Id' header.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Vendor profile found.",
                         content = @Content(mediaType = "application/json", schema = @Schema(implementation = VendorProfileDto.class))),
            @ApiResponse(responseCode = "404", description = "Vendor profile not found for the user.",
                         content = @Content(mediaType = "application/json", schema = @Schema(implementation = Map.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error.")
    })
    @GetMapping("/profile") // Changed from @GetMapping to be more specific
    public ResponseEntity<VendorProfileDto> getVendorProfile(
            @Parameter(description = "UUID of the authenticated vendor user.", required = true)
            @RequestHeader("X-User-Id") UUID userId) {
        try {
            logger.debug("Received request to get vendor profile for User ID: {}", userId);
            VendorProfileDto profileDto = vendorOnboardingService.getProfileByUserId(userId);
            logger.debug("Vendor profile found for User ID: {}", userId);
            return ResponseEntity.ok(profileDto);
        } catch (EntityNotFoundException e) {
            logger.warn("Get vendor profile failed: Not found for User ID {}. Error: {}", userId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Internal error while fetching vendor profile for User ID {}: {}", userId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error fetching vendor profile.", e);
        }
    }

    @Operation(summary = "Generate pre-signed URL for document upload",
               description = "Generates a pre-signed S3 URL that can be used to upload a KYC document. User identified by 'X-User-Id'.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Pre-signed URL generated successfully.",
                         content = @Content(mediaType = "application/json", schema = @Schema(implementation = PresignedUrlResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request (e.g., missing docType)."),
            @ApiResponse(responseCode = "404", description = "Vendor profile not found for the user."),
            @ApiResponse(responseCode = "500", description = "Internal server error (e.g., S3 error).")
    })
    @PostMapping("/documents/upload-url") // Changed from @PostMapping("/documents") to be more specific
    public ResponseEntity<PresignedUrlResponse> generateDocumentUploadUrl(
            @Parameter(description = "UUID of the authenticated vendor user.", required = true)
            @RequestHeader("X-User-Id") UUID userId,
            @Parameter(description = "Type of the document to be uploaded (e.g., 'PAN', 'GSTIN').", required = true, example = "PAN_CARD")
            @RequestParam @NotBlank String docType) {
        try {
            logger.info("Received request to generate upload URL for User ID: {}, DocType: {}", userId, docType);
            // VendorOnboardingService scaffold had vRepo.findByUser(userSvc.getById(userId)).orElseThrow();
            // This implies we need the VendorProfile entity, not just its ID.
            // Let's use a method in VendorOnboardingService to get the profile entity.
            VendorProfile vendorProfile = vendorOnboardingService.getVendorProfileEntityByUserId(userId);
            if (vendorProfile == null) { // Should be caught by EntityNotFoundException from service
                throw new EntityNotFoundException("Vendor profile not found for user ID: " + userId);
            }
            PresignedUrlResponse response = documentService.generateUploadUrl(vendorProfile.getId(), docType);
            logger.info("Pre-signed URL generated for User ID: {}, DocType: {}, S3Key: {}", userId, docType, response.getKey());
            return ResponseEntity.ok(response);
        } catch (EntityNotFoundException e) {
            logger.warn("Generate upload URL failed: Not found for User ID {}. Error: {}", userId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Error generating pre-signed URL for User ID {}: {}", userId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error generating upload URL.", e);
        }
    }

    // TODO: Endpoint for S3 upload callback (if S3 events trigger an HTTP endpoint on this service)
    // For example: POST /vendor-onboarding/documents/callback
    // This endpoint would receive notification from S3 (e.g. via SNS -> SQS -> HTTP or direct Lambda -> HTTP)
    // and then call documentService.handleUploadCallback(s3Key, checksum);
    // The PRD DocumentService.handleUploadCallback implies it's called, but not how.
    // If it's an internal trigger or direct call after client upload confirmation, no new endpoint is needed here.
    // Assuming for now it's an internal process or client confirms upload and provides key/checksum.
}
