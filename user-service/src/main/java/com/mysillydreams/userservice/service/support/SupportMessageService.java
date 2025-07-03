package com.mysillydreams.userservice.service.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mysillydreams.userservice.domain.support.SenderType;
import com.mysillydreams.userservice.domain.support.SupportMessage;
import com.mysillydreams.userservice.domain.support.SupportTicket;
import com.mysillydreams.userservice.domain.support.TicketStatus;
import com.mysillydreams.userservice.dto.support.CreateSupportMessageRequest;
import com.mysillydreams.userservice.dto.support.SupportMessageDto;
import com.mysillydreams.userservice.repository.support.SupportMessageRepository;
import com.mysillydreams.userservice.repository.support.SupportTicketRepository; // To update ticket status

import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class SupportMessageService {

    private static final Logger logger = LoggerFactory.getLogger(SupportMessageService.class);

    private final SupportMessageRepository messageRepository;
    private final SupportTicketRepository ticketRepository; // To fetch ticket and potentially update its status
    private final SupportKafkaClient supportKafkaClient; // To publish ticket updated event
    private final ObjectMapper objectMapper; // For attachments JSON

    @Autowired
    public SupportMessageService(SupportMessageRepository messageRepository,
                                 SupportTicketRepository ticketRepository,
                                 SupportKafkaClient supportKafkaClient,
                                 ObjectMapper objectMapper) {
        this.messageRepository = messageRepository;
        this.ticketRepository = ticketRepository;
        this.supportKafkaClient = supportKafkaClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Posts a new message to an existing support ticket.
     *
     * @param ticketId    The UUID of the support ticket.
     * @param senderType  The type of the sender (CUSTOMER, SUPPORT_USER, SYSTEM).
     * @param senderId    The UUID of the sender (User ID or system ID).
     * @param request     The DTO containing message content and optional attachment metadata.
     * @return The created SupportMessage.
     * @throws EntityNotFoundException if the ticket does not exist.
     */
    @Transactional
    public SupportMessage postMessageToTicket(UUID ticketId, SenderType senderType, UUID senderId, CreateSupportMessageRequest request) {
        Assert.notNull(ticketId, "Ticket ID cannot be null.");
        Assert.notNull(senderType, "Sender type cannot be null.");
        Assert.notNull(senderId, "Sender ID cannot be null.");
        Assert.notNull(request, "CreateSupportMessageRequest cannot be null.");
        Assert.hasText(request.getMessage(), "Message content cannot be blank.");

        logger.info("Attempting to post message to Ticket ID: {} by Sender ID: {} (Type: {})",
                ticketId, senderId, senderType);

        SupportTicket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new EntityNotFoundException("SupportTicket not found with ID: " + ticketId));

        SupportMessage message = new SupportMessage();
        message.setTicket(ticket);
        message.setSenderType(senderType);
        message.setSenderId(senderId);
        message.setMessage(request.getMessage()); // TODO: Consider if message content needs sanitization/validation

        if (request.getAttachments() != null && !request.getAttachments().isEmpty()) {
            try {
                message.setAttachments(objectMapper.writeValueAsString(request.getAttachments()));
            } catch (JsonProcessingException e) {
                logger.error("Failed to serialize attachments to JSON for new message on ticket {}: {}", ticketId, e.getMessage(), e);
                throw new RuntimeException("Failed to process message attachments.", e);
            }
        }

        SupportMessage savedMessage = messageRepository.save(message);
        logger.info("Message ID: {} posted to Ticket ID: {}", savedMessage.getId(), ticketId);

        // Optionally, update ticket status based on who sent the message
        String oldStatusString = ticket.getStatus().toString();
        boolean ticketUpdated = false;
        if (senderType == SenderType.CUSTOMER && (ticket.getStatus() == TicketStatus.PENDING_CUSTOMER_RESPONSE || ticket.getStatus() == TicketStatus.RESOLVED)) {
            // If customer replies to a pending or resolved ticket, re-open or move to in-progress
            ticket.setStatus(TicketStatus.OPEN); // Or IN_PROGRESS if an agent is assigned
            logger.info("Ticket ID {} status changed to {} due to customer reply.", ticketId, ticket.getStatus());
            ticketUpdated = true;
        } else if (senderType == SenderType.SUPPORT_USER && ticket.getStatus() == TicketStatus.OPEN) {
            ticket.setStatus(TicketStatus.IN_PROGRESS); // Support agent replied to an open ticket
            logger.info("Ticket ID {} status changed to {} due to support reply.", ticketId, ticket.getStatus());
            ticketUpdated = true;
        }

        if (ticketUpdated) {
            SupportTicket updatedTicket = ticketRepository.save(ticket);
            supportKafkaClient.publishSupportTicketUpdated(updatedTicket, oldStatusString, savedMessage.getId());
        } else {
            // Publish update event even if status didn't change, because a new message was added.
            // The event payload can indicate "newMessageId".
            supportKafkaClient.publishSupportTicketUpdated(ticket, oldStatusString, savedMessage.getId());
        }


        return savedMessage;
    }

    /**
     * Retrieves all messages for a given support ticket, sorted by timestamp.
     *
     * @param ticketId The UUID of the support ticket.
     * @return A list of {@link SupportMessageDto}.
     * @throws EntityNotFoundException if the ticket does not exist.
     */
    @Transactional(readOnly = true)
    public List<SupportMessageDto> getMessagesForTicket(UUID ticketId) {
        Assert.notNull(ticketId, "Ticket ID cannot be null.");
        logger.debug("Fetching messages for Ticket ID: {}", ticketId);

        // Ensure ticket exists first (or let repository query handle it, though explicit check is clearer)
        if (!ticketRepository.existsById(ticketId)) {
            throw new EntityNotFoundException("SupportTicket not found with ID: " + ticketId);
        }

        List<SupportMessage> messages = messageRepository.findByTicketId(ticketId, Sort.by(Sort.Direction.ASC, "timestamp"));
        return messages.stream().map(SupportMessageDto::from).collect(Collectors.toList());
    }
}
