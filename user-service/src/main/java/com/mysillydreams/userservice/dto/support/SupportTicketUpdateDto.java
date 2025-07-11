package com.mysillydreams.userservice.dto.support;

import com.mysillydreams.userservice.domain.support.TicketStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@Schema(description = "Request payload for updating a support ticket, typically for status changes or assignment.")
public class SupportTicketUpdateDto {

    @NotNull(message = "New status cannot be null.")
    @Schema(description = "The new status for the ticket.", example = "IN_PROGRESS", requiredMode = Schema.RequiredMode.REQUIRED)
    private TicketStatus status;

    @Schema(description = "Optional: UUID of the SupportProfile (agent) to assign this ticket to. " +
                          "Set to null or omit to unassign (if business logic allows).",
            example = "sp-a1b2c3d4-e5f6...")
    private UUID assignedToSupportProfileId; // To assign/reassign the ticket

    // Optional: Internal note added during status change or assignment
    // @Schema(description = "Optional internal note regarding this update.", example = "Escalated to Tier 2 due to complexity.")
    // private String internalNote;
}
