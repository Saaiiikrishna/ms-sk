package com.mysillydreams.userservice.dto.delivery;

import com.mysillydreams.userservice.domain.delivery.AssignmentStatus;
import com.mysillydreams.userservice.domain.delivery.AssignmentType;
import com.mysillydreams.userservice.domain.delivery.OrderAssignment;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
@Schema(description = "Data Transfer Object for Order Assignment details.")
public class OrderAssignmentDto {

    @Schema(description = "Unique identifier of the Order Assignment.")
    private UUID id;

    @Schema(description = "Identifier of the assigned Delivery Profile.")
    private UUID deliveryProfileId;

    @Schema(description = "Identifier of the Order (from Order Service).")
    private UUID orderId;

    @Schema(description = "Type of assignment (PICKUP or DELIVERY).")
    private AssignmentType type;

    @Schema(description = "Current status of the assignment.")
    private AssignmentStatus status;

    @Schema(description = "Timestamp when the order was assigned.")
    private Instant assignedAt;

    @Schema(description = "Timestamp of the last update to this assignment.")
    private Instant lastUpdatedAt;

    // Denormalized fields for client convenience, as per requirement "order address & GPS coords"
    // These would typically be fetched from an Order Service or a shared data store when constructing the DTO.
    // For now, they are placeholders.
    @Schema(description = "Full address for the pickup/delivery location (denormalized).")
    private String locationAddress;

    @Schema(description = "GPS latitude for the location (denormalized).")
    private Double gpsLatitude;

    @Schema(description = "GPS longitude for the location (denormalized).")
    private Double gpsLongitude;

    @Schema(description = "Customer name (denormalized).")
    private String customerName; // Example additional denormalized field

    @Schema(description = "Customer phone number (denormalized).")
    private String customerPhone; // Example additional denormalized field

    @Schema(description = "List of delivery events associated with this assignment.")
    private List<DeliveryEventDto> events;


    public static OrderAssignmentDto from(OrderAssignment assignment) {
        if (assignment == null) {
            return null;
        }
        OrderAssignmentDto dto = new OrderAssignmentDto();
        dto.setId(assignment.getId());
        if (assignment.getDeliveryProfile() != null) {
            dto.setDeliveryProfileId(assignment.getDeliveryProfile().getId());
        }
        dto.setOrderId(assignment.getOrderId());
        dto.setType(assignment.getType());
        dto.setStatus(assignment.getStatus());
        dto.setAssignedAt(assignment.getAssignedAt());
        dto.setLastUpdatedAt(assignment.getLastUpdatedAt());

        // Placeholder for fetching/setting denormalized address/GPS data
        // This data would typically come from an Order Service based on assignment.getOrderId()
        // For example:
        // OrderDetails orderDetails = orderServiceClient.getOrderDetails(assignment.getOrderId());
        // dto.setLocationAddress(orderDetails.getFullAddress());
        // dto.setGpsLatitude(orderDetails.getLatitude());
        // dto.setGpsLongitude(orderDetails.getLongitude());
        // dto.setCustomerName(orderDetails.getCustomerName());
        // dto.setCustomerPhone(orderDetails.getCustomerPhone());
        dto.setLocationAddress("Placeholder: 123 Main St, Anytown, USA"); // Placeholder
        dto.setGpsLatitude(34.0522);  // Placeholder
        dto.setGpsLongitude(-118.2437); // Placeholder
        dto.setCustomerName("Placeholder Customer");
        dto.setCustomerPhone("555-0100");


        if (assignment.getEvents() != null) {
            dto.setEvents(assignment.getEvents().stream().map(DeliveryEventDto::from).collect(Collectors.toList()));
        }

        return dto;
    }
}
