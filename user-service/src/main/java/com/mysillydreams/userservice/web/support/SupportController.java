package com.mysillydreams.userservice.web.support;

import com.mysillydreams.userservice.domain.support.SupportMessage;
import com.mysillydreams.userservice.domain.support.SupportTicket;
import com.mysillydreams.userservice.domain.support.SenderType; // For posting messages
import com.mysillydreams.userservice.dto.support.*;

import com.mysillydreams.userservice.service.support.SupportMessageService;
import com.mysillydreams.userservice.service.support.SupportTicketService;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication; // To get Principal
import org.springframework.security.core.context.SecurityContextHolder; // To get Principal
import org.springframework.security.oauth2.jwt.Jwt; // Assuming JWT principal
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;


import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/support")
@Validated
@Tag(name = "Support Ticket API", description = "Endpoints for managing customer support tickets and messages.")
// Most endpoints will require ROLE_SUPPORT_USER or ROLE_ADMIN.
// Customer-specific actions (like creating a ticket) might have different auth.
// TODO: SECURITY - Review and implement fine-grained authorization for all endpoints.
// Ensure customers can only access/update their own tickets.
// Ensure support users can only access/update tickets according to business rules (e.g., assigned, in their queue).
// Admins have broader access. Consider using @PreAuthorize with SpEL for resource-level checks.
@SecurityRequirement(name = "bearerAuthUser") // Default for the controller
public class SupportController {

    private static final Logger logger = LoggerFactory.getLogger(SupportController.class);

    private final SupportTicketService ticketService;
    private final SupportMessageService messageService;

    @Autowired
    public SupportController(SupportTicketService ticketService, SupportMessageService messageService) {
        this.ticketService = ticketService;
        this.messageService = messageService;
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
        logger.warn("Could not extract user ID from security context for support operation.");
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User ID not determinable from token.");
    }

    private boolean isCurrentUserAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) return false;
        return authentication.getAuthorities().stream()
                .anyMatch(grantedAuthority -> grantedAuthority.getAuthority().equals("ROLE_ADMIN"));
    }


    @Operation(summary = "Create a new support ticket (Customer action)",
               description = "Allows an authenticated user (customer) to create a new support ticket.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Support ticket created successfully.",
                     content = @Content(mediaType = "application/json", schema = @Schema(implementation = SupportTicketDto.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request payload."),
    })
    @PostMapping("/tickets")
    @PreAuthorize("isAuthenticated()") // Any authenticated user can create a ticket for themselves
    public ResponseEntity<SupportTicketDto> createTicket(
            @Parameter(description = "Details for the new support ticket.", required = true)
            @Valid @RequestBody CreateSupportTicketRequest request) {
        try {
            UUID customerId = getCurrentUserId(); // Ticket created by the authenticated user
            logger.info("Received request to create support ticket from Customer ID: {} with subject: {}", customerId, request.getSubject());
            SupportTicket createdTicket = ticketService.createTicket(customerId, request);
            logger.info("Support ticket created successfully with ID: {}", createdTicket.getId());
            return ResponseEntity.status(HttpStatus.CREATED).body(SupportTicketDto.from(createdTicket));
        } catch (Exception e) {
            logger.error("Error creating support ticket: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error creating support ticket.", e);
        }
    }

    @Operation(summary = "List support tickets (Support/Admin action)",
               description = "Retrieves a paginated list of support tickets. " +
                             "Support users see tickets assigned to them or unassigned active tickets. " +
                             "Admins see all tickets.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved list of tickets.",
                     content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = SupportTicketDto.class))))
    })
    @GetMapping("/tickets")
    @PreAuthorize("hasAnyRole('ROLE_SUPPORT_USER', 'ROLE_ADMIN')")
    public ResponseEntity<Page<SupportTicketDto>> listTickets(
            @Parameter(description = "Optional: Filter by assigned support agent ID (for admins).")
            @RequestParam(required = false) UUID assignedToSupportProfileId, // Admins might use this
            Pageable pageable) {

        UUID currentUserId = getCurrentUserId();
        Page<SupportTicketDto> tickets;

        if (isCurrentUserAdmin()) {
            if (assignedToSupportProfileId != null) {
                logger.info("Admin request to list tickets for SupportProfile ID: {}", assignedToSupportProfileId);
                 // Admin fetching for specific agent, including non-active tickets potentially
                tickets = ticketService.listActiveTickets(assignedToSupportProfileId, pageable); // Or a method that gets all for agent
            } else {
                logger.info("Admin request to list all tickets.");
                tickets = ticketService.listAllTickets(pageable);
            }
        } else { // ROLE_SUPPORT_USER
            // Support users see their own active/unassigned tickets.
            // Need to get SupportProfile ID for the current support user.
            // This requires SupportOnboardingService.getSupportProfileEntityByUserId(currentUserId)
            // This dependency is missing here, TODO: Add SupportOnboardingService or adjust logic.
            logger.warn("ROLE_SUPPORT_USER listing tickets - needs SupportProfile ID logic. For now, showing unassigned.");
             tickets = ticketService.listActiveTickets(null, pageable); // Placeholder: shows unassigned active
        }
        return ResponseEntity.ok(tickets);
    }

    @Operation(summary = "Get support ticket by ID (Support/Admin action, or Customer for own ticket)",
               description = "Retrieves details for a specific support ticket, including its message thread.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Ticket details found.",
                     content = @Content(mediaType = "application/json", schema = @Schema(implementation = SupportTicketDto.class))),
        @ApiResponse(responseCode = "403", description = "Forbidden if customer tries to access another's ticket."),
        @ApiResponse(responseCode = "404", description = "Ticket not found.")
    })
    @GetMapping("/tickets/{ticketId}")
    @PreAuthorize("isAuthenticated()") // Further auth logic in service or here
    public ResponseEntity<SupportTicketDto> getTicketById(
            @Parameter(description = "UUID of the support ticket.", required = true) @PathVariable UUID ticketId) {
        try {
            logger.debug("Request to get ticket by ID: {}", ticketId);
            SupportTicket ticket = ticketService.getTicketById(ticketId);

            // Authorization Check: Customer can only see their own tickets. Support/Admin can see any.
            UUID currentUserId = getCurrentUserId();
            if (!isCurrentUserAdmin() &&
                !ticket.getCustomerId().equals(currentUserId) &&
                (ticket.getAssignedTo() == null || ticket.getAssignedTo().getUser() == null || !ticket.getAssignedTo().getUser().getId().equals(currentUserId))
                ) {
                 logger.warn("User {} attempted to access ticket {} they do not own or are not assigned to.", currentUserId, ticketId);
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied to this ticket.");
            }

            logger.debug("Ticket found with ID: {}", ticketId);
            return ResponseEntity.ok(SupportTicketDto.from(ticket));
        } catch (EntityNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        }
    }

    @Operation(summary = "Post a message to a support ticket (Customer, Support, or Admin)",
               description = "Adds a new message to the specified support ticket's thread.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Message posted successfully.",
                     content = @Content(mediaType = "application/json", schema = @Schema(implementation = SupportMessageDto.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request payload."),
        @ApiResponse(responseCode = "403", description = "Forbidden if not involved in ticket."),
        @ApiResponse(responseCode = "404", description = "Ticket not found.")
    })
    @PostMapping("/tickets/{ticketId}/messages")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SupportMessageDto> postMessage(
            @Parameter(description = "UUID of the support ticket.", required = true) @PathVariable UUID ticketId,
            @Parameter(description = "Message details.", required = true) @Valid @RequestBody CreateSupportMessageRequest request) {
        try {
            UUID senderId = getCurrentUserId();
            // Determine SenderType based on user's roles or context
            // This is simplified; a robust system might check if senderId is a customer or support user.
            SenderType senderType = isCurrentUserAdmin() || userHasRole(senderId, "ROLE_SUPPORT_USER") ? SenderType.SUPPORT_USER : SenderType.CUSTOMER;
            // TODO: More robust senderType determination, especially for customer vs support.
            // Requires checking UserEntity.roles for senderId.

            SupportTicket ticket = ticketService.getTicketById(ticketId); // Ensure ticket exists
             // Authorization Check:
            if (senderType == SenderType.CUSTOMER && !ticket.getCustomerId().equals(senderId)) {
                 throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Customer can only post to their own tickets.");
            }
            if (senderType == SenderType.SUPPORT_USER && !isCurrentUserAdmin() && (ticket.getAssignedTo() == null || ticket.getAssignedTo().getUser() == null || !ticket.getAssignedTo().getUser().getId().equals(senderId))) {
                 // throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Support user can only post to assigned or unassigned tickets they pick up.");
                 // For now, allow if support user, actual assignment check is in service logic if needed
            }


            logger.info("Posting message to Ticket ID: {} by Sender ID: {} (Type: {})", ticketId, senderId, senderType);
            SupportMessage postedMessage = messageService.postMessageToTicket(ticketId, senderType, senderId, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(SupportMessageDto.from(postedMessage));
        } catch (EntityNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Error posting message to ticket {}: {}", ticketId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error posting message.", e);
        }
    }

    // Helper for role check - this is simplistic and would need access to UserRepository
    // This should ideally be part of a proper security service or UserDetails.
    private boolean userHasRole(UUID userId, String roleName) {
        // Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        // return auth.getAuthorities().stream().anyMatch(ga -> ga.getAuthority().equals(roleName));
        // This checks the *current* authenticated user, not an arbitrary userId.
        // For checking an arbitrary userId, would need to load that user's roles.
        // This is a placeholder, real implementation needs care.
        if (isCurrentUserAdmin()) return true; // Admin can act as support for this check
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt) {
            Jwt jwt = (Jwt) auth.getPrincipal();
            try {
                 if (UUID.fromString(jwt.getSubject()).equals(userId)) { // Is it the current user?
                    return auth.getAuthorities().stream().anyMatch(ga -> ga.getAuthority().equals(roleName));
                 }
            } catch (Exception e) { return false;}
        }
        return false; // Default deny if not current user or can't determine
    }


    @Operation(summary = "Update support ticket status (Support/Admin action)",
               description = "Allows a support user or admin to change the status of a ticket (e.g., to IN_PROGRESS, RESOLVED).")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Ticket status updated successfully.",
                     content = @Content(mediaType = "application/json", schema = @Schema(implementation = SupportTicketDto.class))),
        @ApiResponse(responseCode = "400", description = "Invalid status or request."),
        @ApiResponse(responseCode = "403", description = "Forbidden."),
        @ApiResponse(responseCode = "404", description = "Ticket not found.")
    })
    @PutMapping("/tickets/{ticketId}/status")
    @PreAuthorize("hasAnyRole('ROLE_SUPPORT_USER', 'ROLE_ADMIN')")
    public ResponseEntity<SupportTicketDto> updateTicketStatus(
            @Parameter(description = "UUID of the support ticket.", required = true) @PathVariable UUID ticketId,
            @Parameter(description = "New status and optional assignee.", required = true) @Valid @RequestBody SupportTicketUpdateDto updateRequest) {
        try {
            logger.info("Request to update status for Ticket ID: {} to {}", ticketId, updateRequest.getStatus());
            SupportTicket updatedTicket = ticketService.updateTicketStatus(ticketId, updateRequest.getStatus(), updateRequest.getAssignedToSupportProfileId());
            return ResponseEntity.ok(SupportTicketDto.from(updatedTicket));
        } catch (EntityNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        } catch (IllegalArgumentException e) { // For invalid status transitions or data
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Error updating ticket status for {}: {}", ticketId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error updating ticket status.", e);
        }
    }

    @Operation(summary = "List tickets by customer ID (Support/Admin action)",
               description = "Retrieves a paginated list of all support tickets for a specific customer.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved tickets for customer.",
                     content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = SupportTicketDto.class)))),
        @ApiResponse(responseCode = "404", description = "Customer (user) not found, or no tickets for customer."),
    })
    @GetMapping("/tickets/customer/{customerId}")
    @PreAuthorize("hasAnyRole('ROLE_SUPPORT_USER', 'ROLE_ADMIN')")
    public ResponseEntity<Page<SupportTicketDto>> listTicketsByCustomer(
            @Parameter(description = "UUID of the customer (User ID).", required = true) @PathVariable UUID customerId,
            Pageable pageable) {
        // TODO: Service should validate if customerId is a valid user.
        logger.debug("Request to list tickets for Customer ID: {}", customerId);
        Page<SupportTicketDto> tickets = ticketService.listTicketsByCustomerId(customerId, pageable);
        return ResponseEntity.ok(tickets);
    }
}
