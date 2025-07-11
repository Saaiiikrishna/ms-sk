package com.mysillydreams.userservice.dto.delivery;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mysillydreams.userservice.domain.delivery.DeliveryEvent;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
@Schema(description = "Data Transfer Object for Delivery Event details.")
public class DeliveryEventDto {
    private static final Logger logger = LoggerFactory.getLogger(DeliveryEventDto.class);
    private static final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules(); // For JSON payload parsing

    @Schema(description = "Unique identifier of the Delivery Event.")
    private UUID id;

    @Schema(description = "Identifier of the Order Assignment this event belongs to.")
    private UUID assignmentId;

    @Schema(description = "Type of the event (e.g., ARRIVED, PHOTO_TAKEN).", example = "ARRIVED_AT_DROPOFF")
    private String eventType;

    @Schema(description = "JSON payload containing event-specific data. Structure varies by eventType.",
            example = "{\"latitude\": 34.0522, \"longitude\": -118.2437, \"accuracy\": 5.0}")
    private Map<String, Object> payload; // Representing JSON payload as a Map

    @Schema(description = "Timestamp when the event occurred.")
    private Instant timestamp;

    public static DeliveryEventDto from(DeliveryEvent event) {
        if (event == null) {
            return null;
        }
        DeliveryEventDto dto = new DeliveryEventDto();
        dto.setId(event.getId());
        if (event.getAssignment() != null) {
            dto.setAssignmentId(event.getAssignment().getId());
        }
        dto.setEventType(event.getEventType());
        dto.setTimestamp(event.getTimestamp());

        // Parse JSON string payload from entity into Map for DTO
        if (event.getPayload() != null && !event.getPayload().isEmpty()) {
            try {
                dto.setPayload(objectMapper.readValue(event.getPayload(), new TypeReference<Map<String, Object>>() {}));
            } catch (JsonProcessingException e) {
                logger.error("Failed to parse DeliveryEvent payload JSON for event ID {}: {}", event.getId(), e.getMessage());
                // Depending on requirements, might set payload to null, an error map, or rethrow
                dto.setPayload(Map.of("error", "Failed to parse payload", "originalPayload", event.getPayload()));
            }
        }
        return dto;
    }
}
