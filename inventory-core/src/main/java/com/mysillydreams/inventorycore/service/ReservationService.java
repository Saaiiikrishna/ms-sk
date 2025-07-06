package com.mysillydreams.inventorycore.service;

import com.mysillydreams.inventorycore.dto.ReservationRequestedEvent;

public interface ReservationService {
    /**
     * Handles an incoming reservation request event.
     * This typically involves checking stock, updating stock levels,
     * and publishing success or failure events via the outbox pattern.
     *
     * @param event The reservation request event.
     */
    void handleReservationRequest(ReservationRequestedEvent event);
}
