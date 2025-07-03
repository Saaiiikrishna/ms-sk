package com.mysillydreams.userservice.dto.support;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@Schema(description = "Request payload for posting a new message to a support ticket.")
public class CreateSupportMessageRequest {

    @NotBlank(message = "Message content cannot be blank.")
    @Size(min = 1, message = "Message content cannot be empty.") // Max size might be large, TEXT column
    @Schema(description = "The content of the support message.", example = "I've tried restarting the device, but the issue persists.",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String message;

    @Schema(description = "Optional list of attachment metadata. Each map could contain 'filename' and 's3Key' " +
                          "if files were uploaded separately (e.g., via a generic attachment upload endpoint first) " +
                          "and their keys are provided here. Direct file upload with message is also an option (multipart).",
            example = "[{\"filename\":\"error_screenshot.png\", \"s3Key\":\"support-attachments/ticketId/userId/uuid.png\"}]")
    private List<Map<String, String>> attachments; // List of maps, e.g. {"filename": "...", "s3Key": "..."}
                                                   // For simplicity, not handling multipart file uploads directly in this DTO.
                                                   // A separate endpoint for attachments might be cleaner.
}
