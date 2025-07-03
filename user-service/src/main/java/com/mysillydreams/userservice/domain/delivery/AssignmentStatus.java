package com.mysillydreams.userservice.domain.delivery;

public enum AssignmentStatus {
    ASSIGNED,       // Order has been assigned to a delivery user, awaiting action
    EN_ROUTE,       // Delivery user is on the way (e.g., to pickup or to customer)
    ARRIVED_AT_PICKUP, // Arrived at pickup location (if applicable)
    PICKED_UP,       // Order picked up (if applicable)
    ARRIVED_AT_DROPOFF, // Arrived at customer/dropoff location
    COMPLETED,      // Delivery successfully completed
    FAILED,         // Delivery failed (e.g., customer not available, issue with order)
    CANCELLED       // Assignment was cancelled
}
