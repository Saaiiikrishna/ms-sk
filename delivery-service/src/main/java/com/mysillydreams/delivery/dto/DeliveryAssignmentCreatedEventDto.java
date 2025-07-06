package com.mysillydreams.delivery.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryAssignmentCreatedEventDto {
    private UUID assignmentId;
    private UUID orderId;
    private UUID courierId;
    // Could also include vendorId, customerId, basic address info if useful for consumers
}
