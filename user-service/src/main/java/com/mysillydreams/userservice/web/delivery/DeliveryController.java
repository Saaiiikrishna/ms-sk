package com.mysillydreams.userservice.web.delivery;

import com.mysillydreams.userservice.domain.delivery.DeliveryEvent;
import com.mysillydreams.userservice.domain.delivery.AssignmentStatus;
import com.mysillydreams.userservice.domain.delivery.OrderAssignment;
import com.mysillydreams.userservice.dto.delivery.DeliveryEventDto;
import com.mysillydreams.userservice.dto.delivery.GpsCoordinatesDto;
import com.mysillydreams.userservice.dto.delivery.OrderAssignmentDto;
import com.mysillydreams.userservice.dto.delivery.OtpVerificationRequestDto;
import com.mysillydreams.userservice.service.UserService; // To get UserEntity if needed for DeliveryProfile
import com.mysillydreams.userservice.service.delivery.*;
import com.mysillydreams.userservice.repository.delivery.OrderAssignmentRepository;
import com.mysillydreams.userservice.service.delivery.OtpVerificationService;
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
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication; // To get Principal
import org.springframework.security.core.context.SecurityContextHolder; // To get Principal
import org.springframework.security.oauth2.jwt.Jwt; // Assuming JWT principal
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;


import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/delivery")
@Validated
@Tag(name = "Delivery Operations API", description = "Endpoints for delivery users to manage assignments and record delivery events.")
@PreAuthorize("hasRole('ROLE_DELIVERY_USER') or hasRole('ROLE_ADMIN')") // Class-level security
@SecurityRequirement(name = "bearerAuthUser") // From User-Service OpenApiConfig
public class DeliveryController {

    private static final Logger logger = LoggerFactory.getLogger(DeliveryController.class);

    private final DeliveryOnboardingService deliveryOnboardingService; // To get DeliveryProfile from X-User-Id
    private final DeliveryAssignmentService deliveryAssignmentService;
    private final DeliveryEventService deliveryEventService;
    private final DeliveryDocumentStorageService deliveryDocumentStorageService; // For photo uploads
    private final OrderAssignmentRepository orderAssignmentRepository;

    private final OtpVerificationService otpVerificationService;

    @Autowired
    public DeliveryController(DeliveryOnboardingService deliveryOnboardingService,
                              DeliveryAssignmentService deliveryAssignmentService,
                              DeliveryEventService deliveryEventService,
                              DeliveryDocumentStorageService deliveryDocumentStorageService,
                              OrderAssignmentRepository orderAssignmentRepository,
                              OtpVerificationService otpVerificationService) {

        this.deliveryOnboardingService = deliveryOnboardingService;
        this.deliveryAssignmentService = deliveryAssignmentService;
        this.deliveryEventService = deliveryEventService;
        this.deliveryDocumentStorageService = deliveryDocumentStorageService;
        this.orderAssignmentRepository = orderAssignmentRepository;
        this.otpVerificationService = otpVerificationService;
    }

    // Helper to get current authenticated user's ID (assuming it's a UUID from JWT 'sub')
    private UUID getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt) {
            Jwt jwt = (Jwt) authentication.getPrincipal();
            try {
                return UUID.fromString(jwt.getSubject());
            } catch (IllegalArgumentException e) {
                logger.error("Authenticated user's subject claim is not a valid UUID: {}", jwt.getSubject());
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Invalid user identifier in token.");
            }
        }
        logger.warn("Could not extract user ID from security context. Not a JWT principal or unauthenticated.");
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User ID not determinable from token.");
    }


    @Operation(summary = "List active assignments for the delivery user",
               description = "Returns a list of active order assignments (e.g., ASSIGNED, EN_ROUTE) for the authenticated delivery user.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved assignments.",
                     content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = OrderAssignmentDto.class)))),
        @ApiResponse(responseCode = "404", description = "Delivery profile not found for the authenticated user."),
    })
    @GetMapping("/assignments")
    public ResponseEntity<List<OrderAssignmentDto>> getActiveAssignments() {
        try {
            UUID deliveryUserId = getCurrentUserId();
            // Need to get DeliveryProfile ID from deliveryUserId
            com.mysillydreams.userservice.domain.delivery.DeliveryProfile deliveryProfile = deliveryOnboardingService.getDeliveryProfileEntityByUserId(deliveryUserId);

            logger.info("Fetching active assignments for DeliveryProfile ID: {}", deliveryProfile.getId());
            List<OrderAssignmentDto> assignments = deliveryAssignmentService.listActiveAssignmentsForProfile(deliveryProfile.getId());
            return ResponseEntity.ok(assignments);
        } catch (EntityNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        }
    }

    @Operation(summary = "Mark assignment as ARRIVED",
               description = "Updates the assignment status to indicate arrival at pickup/dropoff and records GPS coordinates.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Assignment status updated to ARRIVED (or relevant arrival state).",
                     content = @Content(mediaType = "application/json", schema = @Schema(implementation = OrderAssignmentDto.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request (e.g., bad GPS data)."),
        @ApiResponse(responseCode = "404", description = "Assignment not found."),
        @ApiResponse(responseCode = "409", description = "Invalid status transition.")
    })
    @PostMapping("/assignments/{assignmentId}/arrive")
    public ResponseEntity<OrderAssignmentDto> markArrived(
            @Parameter(description = "ID of the order assignment.", required = true) @PathVariable UUID assignmentId,
            @Parameter(description = "GPS coordinates at arrival.", required = true) @Valid @RequestBody GpsCoordinatesDto gpsDto) {
        try {
            // TODO: Determine which status to transition to based on current status and assignment type (PICKUP/DELIVERY)
            // For simplicity, let's assume a generic "EN_ROUTE" or "ARRIVED_AT_LOCATION" logic.
            // This needs more sophisticated state management in DeliveryAssignmentService.
            // For now, just record event and let service decide on status.

            Map<String, Object> eventPayload = Map.of(
                "latitude", gpsDto.getLatitude(),
                "longitude", gpsDto.getLongitude(),
                "accuracy", gpsDto.getAccuracy() != null ? gpsDto.getAccuracy() : "N/A"
            );
            deliveryEventService.recordEvent(assignmentId, "ARRIVED_AT_LOCATION", eventPayload);

            // Example: Transition to EN_ROUTE if it was ASSIGNED, or ARRIVED_AT_DROPOFF if it was EN_ROUTE to customer.
            // This logic should be in DeliveryAssignmentService.
            // For now, this endpoint might just record the event, and another action updates status.
            // Or, based on scaffold, "Marks EN_ROUTE -> ARRIVED". This implies this updates status.
            // Let's assume a generic ARRIVED_AT_DESTINATION status or similar.
            // This needs refinement in DeliveryAssignmentService. For now, a placeholder status update.
            OrderAssignment updatedAssignment = deliveryAssignmentService.updateAssignmentStatus(assignmentId, AssignmentStatus.ARRIVED_AT_DROPOFF, eventPayload); // Placeholder new status

            logger.info("Assignment ID: {} marked as arrived/en-route. GPS: lat={}, lon={}", assignmentId, gpsDto.getLatitude(), gpsDto.getLongitude());
            return ResponseEntity.ok(OrderAssignmentDto.from(updatedAssignment));
        } catch (EntityNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        } catch (IllegalStateException e) { // For invalid status transition
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage(), e);
        }
    }

    @Operation(summary = "Record a call event", description = "Records that the delivery user made a call regarding the assignment.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Call event recorded.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = DeliveryEventDto.class))),
        @ApiResponse(responseCode = "404", description = "Assignment not found.")
    })
    @PostMapping("/assignments/{assignmentId}/call")
    public ResponseEntity<DeliveryEventDto> recordCall(
            @Parameter(description = "ID of the order assignment.", required = true) @PathVariable UUID assignmentId) {
        try {
            // Payload could include call duration, recipient type (customer/support) if needed
            DeliveryEvent event = deliveryEventService.recordEvent(assignmentId, "CALL_MADE", Map.of("callInitiatedBy", getCurrentUserId().toString()));
            logger.info("Call event recorded for Assignment ID: {}", assignmentId);
            return ResponseEntity.status(HttpStatus.CREATED).body(DeliveryEventDto.from(event));
        } catch (EntityNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        }
    }

    @Operation(summary = "Upload a delivery photo", description = "Uploads a photo related to the delivery (e.g., proof of delivery).")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Photo uploaded and event recorded. Returns S3 key.", content = @Content(mediaType = "application/json", schema = @Schema(example = "{\"s3Key\":\"delivery-photos/...\"}"))),
        @ApiResponse(responseCode = "400", description = "Invalid file or missing document type."),
        @ApiResponse(responseCode = "404", description = "Assignment not found.")
    })
    @PostMapping(value = "/assignments/{assignmentId}/upload-photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> uploadPhoto(
            @Parameter(description = "ID of the order assignment.", required = true) @PathVariable UUID assignmentId,
            @Parameter(description = "Type of photo (e.g., 'PROOF_OF_DELIVERY', 'DAMAGED_PACKAGE').", required = true) @RequestParam("docType") @NotBlank String docType,
            @Parameter(description = "The photo file to upload.", required = true) @RequestPart("file") MultipartFile file) {
        try {
            UUID deliveryUserId = getCurrentUserId(); // For S3 key organization
            String s3Key = deliveryDocumentStorageService.uploadDeliveryPhoto(assignmentId, deliveryUserId, file, docType);

            // Record PHOTO_TAKEN event
            Map<String, Object> eventPayload = Map.of(
                "s3Key", s3Key,
                "originalFilename", file.getOriginalFilename(),
                "contentType", file.getContentType(),
                "size", file.getSize()
            );
            deliveryEventService.recordEvent(assignmentId, "PHOTO_TAKEN", eventPayload);

            logger.info("Photo uploaded for Assignment ID: {}. S3 Key: {}", assignmentId, s3Key);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("s3Key", s3Key));
        } catch (EntityNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        } catch (IOException e) {
            logger.error("File upload failed for assignment {}: {}", assignmentId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "File upload failed.", e);
        } catch (RuntimeException e) { // Catch S3 upload errors from service
             logger.error("Error processing photo upload for assignment {}: {}", assignmentId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error processing photo upload.", e);
        }
    }

    @Operation(summary = "Verify delivery OTP", description = "Verifies the One-Time Password provided by the customer.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OTP verified successfully."),
        @ApiResponse(responseCode = "400", description = "Invalid OTP or request format."),
        @ApiResponse(responseCode = "404", description = "Assignment not found."),
        @ApiResponse(responseCode = "409", description = "Cannot verify OTP yet (e.g., prerequisites not met).")
    })
    @PostMapping("/assignments/{assignmentId}/verify-otp")
    public ResponseEntity<Map<String, String>> verifyOtp(
            @Parameter(description = "ID of the order assignment.", required = true) @PathVariable UUID assignmentId,
            @Parameter(description = "OTP details.", required = true) @Valid @RequestBody OtpVerificationRequestDto otpRequest) {
        try {
            OrderAssignment assignment = orderAssignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new EntityNotFoundException("OrderAssignment not found: " + assignmentId));

            boolean isOtpValid = otpVerificationService.verifyOtp(assignment.getOrderId(), otpRequest.getOtp());

            Map<String, Object> eventPayload = Map.of(
                "otpAttempt", otpRequest.getOtp(), // Be cautious logging actual OTPs
                "otpVerified", isOtpValid
            );
            deliveryEventService.recordEvent(assignmentId, "OTP_VERIFIED_ATTEMPT", eventPayload);

            if (isOtpValid) {
                // If OTP is valid, the actual "OTP_VERIFIED" event might be recorded by a service that then updates status.
                // Or, if this controller is responsible for that part of the flow:
                deliveryEventService.recordEvent(assignmentId, "OTP_VERIFIED", Map.of("otp", otpRequest.getOtp()));
                logger.info("OTP verified for Assignment ID: {}", assignmentId);
                return ResponseEntity.ok(Map.of("message", "OTP verified successfully."));
            } else {
                logger.warn("Invalid OTP for Assignment ID: {}", assignmentId);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid OTP.");
            }
        } catch (EntityNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        } catch (IllegalStateException e) { // From sequence checks
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage(), e);
        }
    }

    @Operation(summary = "Complete a delivery assignment",
               description = "Marks a delivery assignment as COMPLETED. Requires prior events like PHOTO_TAKEN and OTP_VERIFIED.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Assignment completed successfully.",
                     content = @Content(mediaType = "application/json", schema = @Schema(implementation = OrderAssignmentDto.class))),
        @ApiResponse(responseCode = "404", description = "Assignment not found."),
        @ApiResponse(responseCode = "409", description = "Cannot complete: prerequisite events missing (e.g., photo, OTP).")
    })
    @PostMapping("/assignments/{assignmentId}/complete")
    public ResponseEntity<OrderAssignmentDto> completeAssignment(
            @Parameter(description = "ID of the order assignment.", required = true) @PathVariable UUID assignmentId) {
        try {
            // DeliveryAssignmentService.updateAssignmentStatus will check prerequisites
            OrderAssignment updatedAssignment = deliveryAssignmentService.updateAssignmentStatus(assignmentId, AssignmentStatus.COMPLETED,
                Map.of("completedByUserId", getCurrentUserId().toString()));
            logger.info("Assignment ID: {} marked as COMPLETED.", assignmentId);
            return ResponseEntity.ok(OrderAssignmentDto.from(updatedAssignment));
        } catch (EntityNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        } catch (IllegalStateException e) { // For missing prerequisites or invalid transition
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage(), e);
        }
    }
}
