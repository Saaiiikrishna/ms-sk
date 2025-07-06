package com.mysillydreams.ordercore.service;

import com.mysillydreams.ordercore.domain.enums.OrderStatus;
import com.mysillydreams.ordercore.dto.CreateOrderCommand; // Will create this DTO

import java.util.UUID;

public interface OrderService {

    /**
     * Creates a new order based on the provided command.
     * This typically involves validating the command, persisting the order and its items,
     * and initiating the order processing saga (e.g., by publishing an OrderCreated event).
     *
     * @param cmd The command object containing details for order creation.
     * @return The UUID of the newly created order.
     */
    UUID createOrder(CreateOrderCommand cmd);

    /**
     * Cancels an existing order.
     *
     * @param orderId The UUID of the order to cancel.
     * @param reason  The reason for cancellation.
     * @param changedBy Identifier for who initiated the cancellation (e.g., "customer", "system", user ID).
     */
    void cancelOrder(UUID orderId, String reason, String changedBy);

    /**
     * Updates the status of an order. This is an internal method typically called
     * by saga listeners or other internal processes.
     *
     * @param orderId   The UUID of the order to update.
     * @param newStatus The new status for the order.
     * @param changedBy Identifier for who/what triggered the status change (e.g., "payment_service", "inventory_listener").
     */
    void updateOrderStatus(UUID orderId, OrderStatus newStatus, String changedBy);

    // Potentially other methods for querying order status, etc., if not handled by a dedicated query service.
}
