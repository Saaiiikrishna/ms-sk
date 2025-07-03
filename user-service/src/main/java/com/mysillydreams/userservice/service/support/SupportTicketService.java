package com.mysillydreams.userservice.service.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mysillydreams.userservice.domain.support.*;
import com.mysillydreams.userservice.dto.support.CreateSupportTicketRequest;
import com.mysillydreams.userservice.dto.support.SupportTicketDto; // For return types
import com.mysillydreams.userservice.repository.support.SupportProfileRepository;
import com.mysillydreams.userservice.repository.support.SupportTicketRepository;
import com.mysillydreams.userservice.service.UserService; // To validate customerId if needed

import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class SupportTicketService {

    private static final Logger logger = LoggerFactory.getLogger(SupportTicketService.class);

    private final SupportTicketRepository ticketRepository;
    private final SupportProfileRepository supportProfileRepository; // To assign tickets
    private final UserService userService; // To validate customerId exists as a user
    private final SupportKafkaClient supportKafkaClient;
    private final SupportMessageService supportMessageService; // To add initial message
    private final ObjectMapper objectMapper;


    // Statuses considered open/active for a support agent's queue
    private static final List<TicketStatus> ACTIVE_TICKET_STATUSES_FOR_AGENT = Arrays.asList(
            TicketStatus.OPEN, TicketStatus.IN_PROGRESS, TicketStatus.PENDING_CUSTOMER_RESPONSE, TicketStatus.ESCALATED
    );


    @Autowired
    public SupportTicketService(SupportTicketRepository ticketRepository,
                                SupportProfileRepository supportProfileRepository,
                                UserService userService,
                                SupportKafkaClient supportKafkaClient,
                                SupportMessageService supportMessageService,
                                ObjectMapper objectMapper) {
        this.ticketRepository = ticketRepository;
        this.supportProfileRepository = supportProfileRepository;
        this.userService = userService;
        this.supportKafkaClient = supportKafkaClient;
        this.supportMessageService = supportMessageService;
        this.objectMapper = objectMapper;
    }

    /**
     * Creates a new support ticket.
     *
     * @param customerId The ID of the user (customer) creating the ticket.
     * @param request    The DTO containing ticket subject and description.
     * @return The created SupportTicket.
     */
    @Transactional
    public SupportTicket createTicket(UUID customerId, CreateSupportTicketRequest request) {
        Assert.notNull(customerId, "Customer ID cannot be null.");
        Assert.notNull(request, "CreateSupportTicketRequest cannot be null.");
        Assert.hasText(request.getSubject(), "Ticket subject cannot be blank.");
        Assert.hasText(request.getDescription(), "Ticket description cannot be blank.");

        logger.info("Attempting to create support ticket for Customer ID: {} with subject: {}", customerId, request.getSubject());

        // Validate customerId exists (optional, depends on if UserEntity FK is used or just UUID)
        // userService.getById(customerId); // Throws EntityNotFoundException if customer user doesn't exist

        SupportTicket ticket = new SupportTicket();
        ticket.setCustomerId(customerId);
        ticket.setSubject(request.getSubject());
        ticket.setDescription(request.getDescription());
        ticket.setStatus(TicketStatus.OPEN); // Initial status

        // TODO: Logic for auto-assignment to a support agent/queue if applicable
        // For now, created unassigned.

        SupportTicket savedTicket = ticketRepository.save(ticket);
        logger.info("SupportTicket created with ID: {} for Customer ID: {}", savedTicket.getId(), customerId);

        // If there was an initial message/description, add it as the first message
        // The current scaffold uses ticket.description for the initial message content.
        // If a separate initial message was in CreateSupportTicketRequest, add it here:
        // supportMessageService.postMessageToTicket(savedTicket.getId(), SenderType.CUSTOMER, customerId, request.getInitialMessage(), null);

        supportKafkaClient.publishSupportTicketCreated(savedTicket);
        return savedTicket;
    }

    /**
     * Retrieves a support ticket by its ID.
     *
     * @param ticketId The UUID of the support ticket.
     * @return The SupportTicket entity.
     * @throws EntityNotFoundException if the ticket is not found.
     */
    @Transactional(readOnly = true)
    public SupportTicket getTicketById(UUID ticketId) {
        Assert.notNull(ticketId, "Ticket ID cannot be null.");
        return ticketRepository.findById(ticketId)
                .orElseThrow(() -> new EntityNotFoundException("SupportTicket not found with ID: " + ticketId));
    }


    /**
     * Updates the status of a support ticket and optionally assigns it.
     *
     * @param ticketId                   The UUID of the ticket to update.
     * @param newStatus                  The new status for the ticket.
     * @param assignedToSupportProfileId Optional: UUID of the SupportProfile to assign/reassign the ticket to.
     *                                   Pass null to unassign (if business logic allows).
     * @return The updated SupportTicket.
     */
    @Transactional
    public SupportTicket updateTicketStatus(UUID ticketId, TicketStatus newStatus, UUID assignedToSupportProfileId) {
        Assert.notNull(ticketId, "Ticket ID cannot be null.");
        Assert.notNull(newStatus, "New status cannot be null.");

        logger.info("Attempting to update status of Ticket ID: {} to {}. Assign to Profile ID: {}", ticketId, newStatus, assignedToSupportProfileId);

        SupportTicket ticket = getTicketById(ticketId); // Ensures ticket exists
        String oldStatus = ticket.getStatus().toString();

        ticket.setStatus(newStatus);

        if (assignedToSupportProfileId != null) {
            SupportProfile assignedTo = supportProfileRepository.findById(assignedToSupportProfileId)
                    .orElseThrow(() -> new EntityNotFoundException("SupportProfile (assignee) not found with ID: " + assignedToSupportProfileId));
            ticket.setAssignedTo(assignedTo);
        } else {
            // If null is passed and you want to unassign
            // ticket.setAssignedTo(null); // This is allowed if assignedTo can be null
        }

        SupportTicket updatedTicket = ticketRepository.save(ticket);
        logger.info("SupportTicket ID: {} status updated to {}. Assignment updated.", ticketId, newStatus);

        supportKafkaClient.publishSupportTicketUpdated(updatedTicket, oldStatus, null); // null for messageId as this is status update
        return updatedTicket;
    }

    /**
     * Lists tickets for a specific customer.
     *
     * @param customerId The UUID of the customer.
     * @param pageable   Pagination information.
     * @return A page of {@link SupportTicketDto}.
     */
    @Transactional(readOnly = true)
    public Page<SupportTicketDto> listTicketsByCustomerId(UUID customerId, Pageable pageable) {
        Assert.notNull(customerId, "Customer ID cannot be null.");
        Page<SupportTicket> ticketPage = ticketRepository.findByCustomerId(customerId, pageable);
        return ticketPage.map(SupportTicketDto::from);
    }

    /**
     * Lists open/active tickets, optionally filtered by assigned support agent.
     *
     * @param assignedToSupportProfileId Optional: UUID of the support agent. If null, lists unassigned active tickets.
     * @param pageable                   Pagination information.
     * @return A page of {@link SupportTicketDto}.
     */
    @Transactional(readOnly = true)
    public Page<SupportTicketDto> listActiveTickets(UUID assignedToSupportProfileId, Pageable pageable) {
        Page<SupportTicket> ticketPage;
        if (assignedToSupportProfileId != null) {
            ticketPage = ticketRepository.findByAssignedToIdAndStatusIn(assignedToSupportProfileId, ACTIVE_TICKET_STATUSES_FOR_AGENT, pageable);
        } else {
            // This lists tickets that are unassigned AND in active statuses
            ticketPage = ticketRepository.findByAssignedToIsNullAndStatusIn(ACTIVE_TICKET_STATUSES_FOR_AGENT, pageable);
            // Or use the combined query:
            // ticketPage = ticketRepository.findActiveTicketsForAgentOrUnassigned(ACTIVE_TICKET_STATUSES_FOR_AGENT, null, pageable);
        }
        return ticketPage.map(SupportTicketDto::from);
    }

    /**
     * Lists all tickets (regardless of status or assignment) - for admin overview.
     * @param pageable Pagination information.
     * @return A page of {@link SupportTicketDto}.
     */
    @Transactional(readOnly = true)
    public Page<SupportTicketDto> listAllTickets(Pageable pageable) {
        Page<SupportTicket> ticketPage = ticketRepository.findAll(pageable);
        return ticketPage.map(SupportTicketDto::from);
    }
}
