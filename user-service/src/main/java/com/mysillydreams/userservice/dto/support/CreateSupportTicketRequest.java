package com.mysillydreams.userservice.dto.support;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

// import java.util.List; // If allowing attachments during creation
// import org.springframework.web.multipart.MultipartFile; // If handling file uploads directly

@Data
@NoArgsConstructor
@Schema(description = "Request payload for creating a new support ticket.")
public class CreateSupportTicketRequest {

    // customerId will typically be derived from the authenticated user's context (e.g., JWT)
    // and not part of the request body from the customer.
    // If an admin/support creates a ticket ON BEHALF of a customer, then customerId might be in request.
    // For now, assuming customer creates their own ticket.

    @NotBlank(message = "Subject cannot be blank.")
    @Size(min = 5, max = 255, message = "Subject must be between 5 and 255 characters.")
    @Schema(description = "Subject of the support ticket.", example = "Problem with order #ORD12345", requiredMode = Schema.RequiredMode.REQUIRED)
    private String subject;

    @NotBlank(message = "Description cannot be blank.")
    @Size(min = 10, message = "Description must be at least 10 characters long.")
    @Schema(description = "Detailed description of the issue or query.", example = "My order arrived damaged...", requiredMode = Schema.RequiredMode.REQUIRED)
    private String description;

    // Optional: Allow initial message or attachments during ticket creation
    // @Schema(description = "Initial message from the customer.")
    // private String initialMessage; // Could be merged with description or separate

    // @Schema(description = "List of attachment metadata if files are uploaded separately or links provided.")
    // private List<Map<String, String>> attachments; // e.g., [{"filename": "photo.jpg", "s3Key": "temp/key1"}, ...]
                                                   // Actual file upload would be a separate multipart request.
}
