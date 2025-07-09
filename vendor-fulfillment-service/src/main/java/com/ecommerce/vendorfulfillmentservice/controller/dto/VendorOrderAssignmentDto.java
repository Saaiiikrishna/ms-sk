package com.ecommerce.vendorfulfillmentservice.controller.dto;

import com.ecommerce.vendorfulfillmentservice.entity.AssignmentStatus;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class VendorOrderAssignmentDto {
    private UUID id;
    private UUID orderId;
    private UUID vendorId;
    private AssignmentStatus status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private String trackingNo;
    private List<VendorOrderStatusHistoryDto> statusHistory;
}
