package com.ecommerce.vendorfulfillmentservice.controller;

import com.ecommerce.vendorfulfillmentservice.controller.dto.ReassignAssignmentRequest;
import com.ecommerce.vendorfulfillmentservice.controller.dto.ShipAssignmentRequest;
import com.ecommerce.vendorfulfillmentservice.entity.AssignmentStatus; // For listAssignments
import com.ecommerce.vendorfulfillmentservice.entity.VendorOrderAssignment;
import com.ecommerce.vendorfulfillmentservice.controller.dto.VendorOrderAssignmentDto; // For GET responses
import com.ecommerce.vendorfulfillmentservice.service.VendorAssignmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

// TODO: Add Keycloak security annotations for roles (e.g., @PreAuthorize("hasRole('VENDOR')"))

@RestController
@RequestMapping("/fulfillment/assignments")
@RequiredArgsConstructor
@Slf4j
public class FulfillmentAssignmentController {

    private final VendorAssignmentService vendorAssignmentService;

    // POST /fulfillment/assignments/{id}/ack
    // Role: VENDOR
    @PostMapping("/{id}/ack")
    public ResponseEntity<VendorOrderAssignment> acknowledgeOrder(@PathVariable UUID id) {
        // TODO: Get vendorId from JWT/Principal and verify it matches assignment.vendorId
        log.info("Received request to ACKNOWLEDGE assignment ID: {}", id);
        VendorOrderAssignment assignment = vendorAssignmentService.acknowledgeOrder(id);
        return ResponseEntity.ok(assignment);
    }

    // POST /fulfillment/assignments/{id}/pack
    // Role: VENDOR
    @PostMapping("/{id}/pack")
    public ResponseEntity<VendorOrderAssignment> packOrder(@PathVariable UUID id) {
        // TODO: Get vendorId from JWT/Principal and verify it matches assignment.vendorId
        log.info("Received request to PACK assignment ID: {}", id);
        VendorOrderAssignment assignment = vendorAssignmentService.packOrder(id);
        return ResponseEntity.ok(assignment);
    }

    // POST /fulfillment/assignments/{id}/ship
    // Role: VENDOR
    // Accepts trackingNo in request body
    @PostMapping("/{id}/ship")
    public ResponseEntity<VendorOrderAssignment> shipAssignment(@PathVariable UUID id,
                                                              @Valid @RequestBody ShipAssignmentRequest shipRequest) {
        // TODO: Get vendorId from JWT/Principal and verify it matches assignment.vendorId
        log.info("Received request to SHIP assignment ID: {} with trackingNo: {}", id, shipRequest.getTrackingNo());
        VendorOrderAssignment assignment = vendorAssignmentService.shipAssignment(id, shipRequest.getTrackingNo());
        return ResponseEntity.ok(assignment);
    }

    // POST /fulfillment/assignments/{id}/complete
    // Role: VENDOR
    @PostMapping("/{id}/complete")
    public ResponseEntity<VendorOrderAssignment> completeFulfillment(@PathVariable UUID id) {
        // TODO: Get vendorId from JWT/Principal and verify it matches assignment.vendorId
        log.info("Received request to COMPLETE assignment ID: {}", id);
        VendorOrderAssignment assignment = vendorAssignmentService.completeFulfillment(id);
        return ResponseEntity.ok(assignment);
    }


    // TODO: Implement other GET endpoints from PRD in a later phase or as per plan.
import com.ecommerce.vendorfulfillmentservice.controller.dto.ReassignAssignmentRequest;
// ... other imports

// ... existing methods

    // GET /fulfillment/assignments?vendorId=&? : List own assignments (filterable) - VENDOR
    // GET /fulfillment/assignments/{id} : Fetch assignment + history - VENDOR, ADMIN

    // GET /fulfillment/assignments/{id}
    // Roles: VENDOR, ADMIN
    @GetMapping("/{id}")
    public ResponseEntity<VendorOrderAssignmentDto> getAssignmentById(@PathVariable UUID id) {
        // TODO: Add security - VENDOR can only see their own, ADMIN can see any.
        log.info("Received request to GET assignment ID: {}", id);
        VendorOrderAssignmentDto assignmentDto = vendorAssignmentService.findAssignmentByIdWithHistory(id);
        return ResponseEntity.ok(assignmentDto);
    }

    // GET /fulfillment/assignments
    // Role: VENDOR (primarily, to list own assignments)
    // PRD also implies ADMIN might use this or similar for oversight (could be different endpoint or param based access)
    // Filterable by vendorId, status. Supports pagination and sorting.
    @GetMapping
    public ResponseEntity<org.springframework.data.domain.Page<VendorOrderAssignmentDto>> listAssignments(
            @RequestParam(required = false) UUID vendorId, // For VENDOR, this should be enforced from JWT. For ADMIN, could be optional.
            @RequestParam(required = false) AssignmentStatus status,
            @org.springframework.data.web.PageableDefault(size = 20, sort = "createdAt,desc") org.springframework.data.domain.Pageable pageable) {

        // TODO: Security:
        // If user is VENDOR, vendorId param should be ignored and vendorId from JWT used.
        // If user is ADMIN, vendorId param can be used. If null, list for all vendors.
        log.info("Received request to LIST assignments. Filters: vendorId={}, status={}, pageable={}", vendorId, status, pageable);

        // For now, passing vendorId directly. Security layer would override/validate this.
        org.springframework.data.domain.Page<VendorOrderAssignmentDto> assignmentsPage =
                vendorAssignmentService.findAllAssignments(vendorId, status, pageable);
        return ResponseEntity.ok(assignmentsPage);
    }

    // PUT /fulfillment/assignments/{id}/reassign
    // Role: ADMIN
    @PutMapping("/{id}/reassign")
    public ResponseEntity<VendorOrderAssignmentDto> reassignAssignment(
            @PathVariable UUID id,
            @Valid @RequestBody ReassignAssignmentRequest reassignRequest) {
        // TODO: Get adminUserId from JWT/Principal for auditing
        // For now, passing null for adminUserId. In a real scenario, this would come from security context.
        UUID adminUserId = null; // Placeholder for admin user ID from JWT
        log.info("Received request to REASSIGN assignment ID: {} to new vendor ID: {} by admin: {}",
                id, reassignRequest.getNewVendorId(), adminUserId);

        VendorOrderAssignment assignment = vendorAssignmentService.reassignOrder(id, reassignRequest.getNewVendorId(), adminUserId);
        // Map to DTO, findAssignmentByIdWithHistory already does this and fetches history.
        VendorOrderAssignmentDto assignmentDto = vendorAssignmentService.findAssignmentByIdWithHistory(assignment.getId());
        return ResponseEntity.ok(assignmentDto);
    }
}
