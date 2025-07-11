package com.mysillydreams.delivery.domain.enums;

public enum DeliveryEventType {
    ASSIGNMENT_CREATED,     // When a courier is assigned
    COURIER_ACCEPTED,       // Courier explicitly accepts the assignment
    COURIER_REJECTED,       // Courier rejects the assignment

    ARRIVED_AT_PICKUP,    // Courier arrived at vendor/warehouse
    PICKUP_STARTED,         // Courier started pickup process (e.g. photo, OTP)
    PICKUP_OTP_VERIFIED,
    PICKUP_PHOTO_UPLOADED,
    PICKED_UP,              // Package collected from vendor

    GPS_UPDATE,             // Regular GPS location update during transit
    IN_TRANSIT_ETA_UPDATED, // ETA for delivery updated

    ARRIVED_AT_DROPOFF,   // Courier arrived at customer location
    DELIVERY_STARTED,       // Courier started delivery process
    DELIVERY_OTP_VERIFIED,
    DELIVERY_PHOTO_UPLOADED,
    DELIVERED,              // Package delivered to customer

    DELIVERY_FAILED,        // Delivery attempt failed (e.g., customer not home, address issue)
    RETURN_INITIATED,       // If undelivered, package is being returned to sender/hub
    RETURNED_TO_HUB,        // Package returned to hub/vendor

    ASSIGNMENT_CANCELLED,   // Assignment cancelled by system or admin
    NOTE_ADDED              // A note was added to the assignment
}
