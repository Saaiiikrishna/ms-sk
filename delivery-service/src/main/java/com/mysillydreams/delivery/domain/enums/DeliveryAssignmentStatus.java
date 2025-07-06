package com.mysillydreams.delivery.domain.enums;

public enum DeliveryAssignmentStatus {
    PENDING_ASSIGNMENT, // Waiting for a courier to be assigned
    ASSIGNED,           // Courier assigned, awaiting courier action
    ARRIVED_AT_PICKUP,  // Courier arrived at pickup location
    PICKED_UP,          // Courier picked up the package
    IN_TRANSIT,         // Courier is en route to customer
    ARRIVED_AT_DROPOFF, // Courier arrived at customer's location
    DELIVERED,          // Package successfully delivered
    FAILED_DELIVERY,    // Delivery attempt failed (e.g., customer not available)
    CANCELLED           // Assignment cancelled (e.g., by admin, or order cancelled)
}
