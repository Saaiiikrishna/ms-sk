package com.mysillydreams.userservice.dto.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mysillydreams.userservice.domain.support.SenderType;
import com.mysillydreams.userservice.domain.support.SupportMessage;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
@Schema(description = "Data Transfer Object for a message within a support ticket.")
public class SupportMessageDto {
    private static final Logger logger = LoggerFactory.getLogger(SupportMessageDto.class);
    private static final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Schema(description = "Unique identifier of the support message.")
    private UUID id;

    @Schema(description = "Identifier of the support ticket this message belongs to.")
    private UUID ticketId;

    @Schema(description = "Type of the sender (CUSTOMER, SUPPORT_USER, SYSTEM).")
    private SenderType senderType;

    @Schema(description = "Identifier of the sender (e.g., User ID or system identifier).")
    private UUID senderId;

    @Schema(description = "The content of the message.")
    private String message; // Assumes message is not encrypted for DTO, or already decrypted

    @Schema(description = "List of attachments associated with the message. Each map could contain 's3Key', 'filename'.")
    private List<Map<String, String>> attachments; // Representing JSON array of attachment metadata

    @Schema(description = "Timestamp when the message was created.")
    private Instant timestamp;

    public static SupportMessageDto from(SupportMessage messageEntity) {
        if (messageEntity == null) {
            return null;
        }
        SupportMessageDto dto = new SupportMessageDto();
        dto.setId(messageEntity.getId());
        if (messageEntity.getTicket() != null) {
            dto.setTicketId(messageEntity.getTicket().getId());
        }
        dto.setSenderType(messageEntity.getSenderType());
        dto.setSenderId(messageEntity.getSenderId());
        dto.setMessage(messageEntity.getMessage()); // Assumes message is decrypted if it was encrypted in entity
        dto.setTimestamp(messageEntity.getTimestamp());

        if (messageEntity.getAttachments() != null && !messageEntity.getAttachments().isEmpty()) {
            try {
                dto.setAttachments(objectMapper.readValue(messageEntity.getAttachments(), new TypeReference<List<Map<String, String>>>() {}));
            } catch (JsonProcessingException e) {
                logger.error("Failed to parse attachments JSON for message ID {}: {}", messageEntity.getId(), e.getMessage());
                dto.setAttachments(List.of(Map.of("error", "Failed to parse attachments", "originalData", messageEntity.getAttachments())));
            }
        }
        return dto;
    }
}
