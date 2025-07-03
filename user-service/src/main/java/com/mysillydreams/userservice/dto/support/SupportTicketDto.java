package com.mysillydreams.userservice.dto.support;

import com.mysillydreams.userservice.domain.support.SupportTicket;
import com.mysillydreams.userservice.domain.support.TicketStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
@Schema(description = "Data Transfer Object for Support Ticket details, including its messages.")
public class SupportTicketDto {

    @Schema(description = "Unique identifier of the support ticket.")
    private UUID id;

    @Schema(description = "Identifier of the customer (User) who raised the ticket.")
    private UUID customerId;

    @Schema(description = "Identifier of the Support Profile (agent) this ticket is assigned to (if any).")
    private UUID assignedToSupportProfileId;

    // Could also include assigned agent's name for convenience
    // @Schema(description = "Name of the assigned support agent.")
    // private String assignedToSupportAgentName;

    @Schema(description = "Subject of the support ticket.", example = "Issue with my last order #ORD123")
    private String subject;

    @Schema(description = "Detailed description of the issue.")
    private String description; // Assumes decrypted if was encrypted in entity

    @Schema(description = "Current status of the ticket (e.g., OPEN, IN_PROGRESS, RESOLVED).")
    private TicketStatus status;

    @Schema(description = "Timestamp of when the ticket was created.")
    private Instant createdAt;

    @Schema(description = "Timestamp of the last update to the ticket.")
    private Instant updatedAt;

    @Schema(description = "Thread of messages associated with this ticket.")
    private List<SupportMessageDto> messages;

    public static SupportTicketDto from(SupportTicket ticket) {
        if (ticket == null) {
            return null;
        }
        SupportTicketDto dto = new SupportTicketDto();
        dto.setId(ticket.getId());
        dto.setCustomerId(ticket.getCustomerId());
        if (ticket.getAssignedTo() != null) {
            dto.setAssignedToSupportProfileId(ticket.getAssignedTo().getId());
            // if (ticket.getAssignedTo().getUser() != null) {
            //     dto.setAssignedToSupportAgentName(ticket.getAssignedTo().getUser().getName()); // Assuming UserEntity has getName()
            // }
        }
        dto.setSubject(ticket.getSubject()); // Assumes decrypted
        dto.setDescription(ticket.getDescription()); // Assumes decrypted
        dto.setStatus(ticket.getStatus());
        dto.setCreatedAt(ticket.getCreatedAt());
        dto.setUpdatedAt(ticket.getUpdatedAt());

        if (ticket.getMessages() != null) {
            dto.setMessages(ticket.getMessages().stream()
                    .map(SupportMessageDto::from)
                    .collect(Collectors.toList()));
        }
        return dto;
    }
}
