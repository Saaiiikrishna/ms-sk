package com.mysillydreams.ordercore.domain.enums;

public enum OrderStatus {
    // Initial and intermediate states
    CREATED,                // Order received by the system (e.g., from Order API)
    VALIDATION_PENDING,     // Order details are being validated (e.g., fraud checks, stock availability pre-check)
    VALIDATED,              // Order passed initial validation

    // Payment related states
    PAYMENT_PENDING,        // Awaiting payment processing
    PAID,                   // Payment successful

    // Inventory/Reservation states
    RESERVATION_PENDING,    // Inventory reservation is in progress
    RESERVED,               // Inventory successfully reserved (renamed from guide's PAID to RESERVED for clarity if PAID is a separate step)

    // Fulfillment states
    CONFIRMED,              // Order confirmed, ready for fulfillment processing (can come after payment and reservation)
    ASSIGNED_TO_FULFILLMENT_CENTER, // Assigned to a specific fulfillment center/vendor
    ACKNOWLEDGED_BY_FULFILLMENT_CENTER, // Fulfillment center acknowledged receipt of the order

    PICKING_ITEMS,          // Items are being picked from shelves
    PACKING_ORDER,          // Order is being packed
    FULFILLING,             // General state for in-progress fulfillment (can encompass picking/packing)

    READY_FOR_SHIPMENT,     // Packed and ready to be shipped
    FULFILLED,              // All items in the order have been processed by fulfillment (could mean ready for shipment or shipped)

    // Shipping states
    PICKUP_ASSIGNED,        // Carrier assigned for pickup
    AWAITING_PICKUP,        // Waiting for carrier to pick up the package
    SHIPPED,                // Order has been shipped by the carrier (guide had this earlier, moving it after FULFILLED)
    IN_TRANSIT,             // Order is currently in transit with the carrier
    DELIVERY_ATTEMPTED,     // Carrier attempted delivery but failed
    DELIVERED,              // Order successfully delivered to the customer

    // Post-delivery states
    COMPLETED,              // Order considered fully completed (e.g., after return window closes or all post-delivery actions done)

    // Cancellation states
    CANCELLATION_REQUESTED, // Customer or system requested cancellation
    CANCELLED,              // Order has been cancelled

    // Return states
    RETURN_REQUESTED,       // Customer requested a return for one or more items
    RETURN_APPROVED,        // Return request approved
    RETURN_REJECTED,        // Return request rejected
    AWAITING_RETURN_SHIPMENT, // Waiting for customer to ship the returned items
    RETURN_IN_TRANSIT,      // Returned items are in transit back to facility
    RETURN_RECEIVED,        // Returned items received at facility
    RETURN_INSPECTION,      // Returned items under inspection
    RETURNED,               // Return processed (e.g., refund issued)

    // Exception/Hold states
    ON_HOLD,                // Order is temporarily on hold (e.g., due to fraud alert, customer request)
    ACTION_REQUIRED;        // Order requires manual intervention or some action

    // The guide's list was:
    // CREATED, VALIDATED, PAID, RESERVATION_PENDING,
    // CONFIRMED, ASSIGNED, ACKNOWLEDGED, FULFILLING,
    // FULFILLED, PICKUP_ASSIGNED, IN_TRANSIT,
    // SHIPPED, DELIVERED, RETURN_REQUESTED, RETURNED, CANCELLED
    // I've expanded it slightly for more granularity often seen in real systems.
    // The core flow from the guide is still covered.
}
