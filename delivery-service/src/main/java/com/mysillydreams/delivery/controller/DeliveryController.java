package com.mysillydreams.delivery.controller;

import com.mysillydreams.delivery.dto.GpsUpdateDto;
import com.mysillydreams.delivery.dto.PhotoOtpDto;
import com.mysillydreams.delivery.service.AssignmentService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid; // For @Valid annotation
import jakarta.persistence.EntityNotFoundException; // For specific exception handling

import java.util.UUID;

@RestController
@RequestMapping("/delivery/assignments") // Base path as per plan
@RequiredArgsConstructor
// Add security at class level if all endpoints require the same role, e.g., "DELIVERY"
// @PreAuthorize("hasRole('DELIVERY')") // Or hasAuthority('ROLE_DELIVERY')
public class DeliveryController {

    private static final Logger log = LoggerFactory.getLogger(DeliveryController.class);
    private final AssignmentService assignmentService;

    @PostMapping("/{assignmentId}/arrive-pickup") // Clarified endpoint name from "arrive"
    @PreAuthorize("hasRole('ROLE_DELIVERY') or hasAuthority('ROLE_DELIVERY')") // Assuming DELIVERY role from Keycloak
    public ResponseEntity<Void> markArrivedAtPickup(@PathVariable UUID assignmentId) {
        log.info("Received request: Courier arrived at pickup for assignment {}", assignmentId);
        try {
            assignmentService.markArrivedAtPickup(assignmentId);
            return ResponseEntity.ok().build();
        } catch (EntityNotFoundException e) {
            log.warn("Assignment not found for arrival at pickup: {}", assignmentId, e);
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) { // E.g., if status transition is invalid
            log.warn("Invalid state transition for arrival at pickup, assignment {}: {}", assignmentId, e.getMessage());
            return ResponseEntity.status(409).build(); // Conflict
        }
    }

    @PostMapping("/{assignmentId}/pickup-photo")
    @PreAuthorize("hasRole('ROLE_DELIVERY') or hasAuthority('ROLE_DELIVERY')")
    public ResponseEntity<Void> recordPickupDetails(
            @PathVariable UUID assignmentId,
            @Valid @RequestBody PhotoOtpDto dto) {
        log.info("Received request: Record pickup details for assignment {}", assignmentId);
        try {
            assignmentService.markPickedUp(assignmentId, dto);
            return ResponseEntity.ok().build();
        } catch (EntityNotFoundException e) {
            log.warn("Assignment not found for pickup details: {}", assignmentId, e);
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
             log.warn("Invalid state transition for pickup, assignment {}: {}", assignmentId, e.getMessage());
            return ResponseEntity.status(409).build();
        }
        // Add more specific exception handling, e.g., for OTP validation failure
    }

    @PostMapping("/{assignmentId}/gps")
    @PreAuthorize("hasRole('ROLE_DELIVERY') or hasAuthority('ROLE_DELIVERY')")
    public ResponseEntity<Void> postGpsUpdate(
            @PathVariable UUID assignmentId,
            @Valid @RequestBody GpsUpdateDto dto) {
        // No significant state change here, just publishing.
        // Could return 202 Accepted if it's truly async fire-and-forget.
        // For simplicity, 200 OK if publish call itself succeeds.
        log.debug("Received GPS update for assignment {}", assignmentId); // Debug level for high frequency
        try {
            assignmentService.publishGpsUpdate(assignmentId, dto);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            // Log error, but usually GPS updates are not critical enough to return 500 to client.
            // However, if Kafka publish fails synchronously here, an error might be appropriate.
            log.error("Failed to publish GPS update for assignment {}: {}", assignmentId, e.getMessage(), e);
            return ResponseEntity.internalServerError().build(); // Or a more specific error
        }
    }

    @PostMapping("/{assignmentId}/arrive-dropoff") // Added this endpoint for symmetry with arrive-pickup
    @PreAuthorize("hasRole('ROLE_DELIVERY') or hasAuthority('ROLE_DELIVERY')")
    public ResponseEntity<Void> markArrivedAtDropoff(@PathVariable UUID assignmentId) {
        log.info("Received request: Courier arrived at dropoff for assignment {}", assignmentId);
        try {
            assignmentService.markArrivedAtDropoff(assignmentId);
            return ResponseEntity.ok().build();
        } catch (EntityNotFoundException e) {
            log.warn("Assignment not found for arrival at dropoff: {}", assignmentId, e);
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            log.warn("Invalid state transition for arrival at dropoff, assignment {}: {}", assignmentId, e.getMessage());
            return ResponseEntity.status(409).build();
        }
    }


    @PostMapping("/{assignmentId}/deliver")
    @PreAuthorize("hasRole('ROLE_DELIVERY') or hasAuthority('ROLE_DELIVERY')")
    public ResponseEntity<Void> recordDeliveryDetails(
            @PathVariable UUID assignmentId,
            @Valid @RequestBody PhotoOtpDto dto) {
        log.info("Received request: Record delivery details for assignment {}", assignmentId);
        try {
            assignmentService.markDelivered(assignmentId, dto);
            return ResponseEntity.ok().build();
        } catch (EntityNotFoundException e) {
            log.warn("Assignment not found for delivery details: {}", assignmentId, e);
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            log.warn("Invalid state transition for delivery, assignment {}: {}", assignmentId, e.getMessage());
            return ResponseEntity.status(409).build();
        }
        // Add more specific exception handling
    }

    // Optional: Endpoint for courier to report a failed delivery attempt
    // @PostMapping("/{assignmentId}/fail-delivery")
    // @PreAuthorize("hasRole('ROLE_DELIVERY') or hasAuthority('ROLE_DELIVERY')")
    // public ResponseEntity<Void> failDelivery(@PathVariable UUID assignmentId, @RequestBody DeliveryFailureDto dto) { ... }
}
