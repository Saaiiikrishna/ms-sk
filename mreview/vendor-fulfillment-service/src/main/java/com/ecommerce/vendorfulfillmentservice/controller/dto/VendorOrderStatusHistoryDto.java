package com.ecommerce.vendorfulfillmentservice.controller.dto;

import com.ecommerce.vendorfulfillmentservice.entity.AssignmentStatus;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class VendorOrderStatusHistoryDto {
    private UUID id;
    private AssignmentStatus status;
    private OffsetDateTime occurredAt;

    // Potential future fields:
    // private UUID changedByUserId;
    // private String notes;
}
