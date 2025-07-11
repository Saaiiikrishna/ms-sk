package com.mysillydreams.userservice.dto.inventory;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Schema(description = "Request payload for registering an existing user as an inventory user. " +
                  "The user ID is typically passed via a header (e.g., X-User-Id). This request has no body fields.")
public class RegisterInventoryUserRequest {
    // No body fields needed as per the provided scaffold; userId comes from header.
    // If, for example, a default warehouse or location needed to be specified at registration,
    // fields could be added here.
    // Example:
    // @Schema(description = "Default warehouse ID for this inventory user.", example = "wh-123")
    // private String defaultWarehouseId;
}
