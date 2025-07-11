package com.ecommerce.vendorfulfillmentservice.entity;

public enum AssignmentStatus {
    ASSIGNED,       // Initial state when an order is assigned to a vendor
    ACKNOWLEDGED,   // Vendor has acknowledged receipt of the order
    PACKED,         // Vendor has packed the order
    SHIPPED,        // Vendor has shipped the order
    FULFILLED,      // Order fulfillment is complete (e.g., delivered or picked up by courier) - Renamed from 'COMPLETE' in PRD for clarity.
    REASSIGNED,     // Order has been reassigned to a different vendor
    CANCELLED       // Order fulfillment has been cancelled (though not explicitly in PRD, good to consider)
    // Add other statuses as needed
}
