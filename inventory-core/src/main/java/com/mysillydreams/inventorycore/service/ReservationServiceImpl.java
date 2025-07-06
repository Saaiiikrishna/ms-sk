package com.mysillydreams.inventorycore.service;

import com.mysillydreams.inventorycore.domain.StockLevel;
import com.mysillydreams.inventorycore.dto.LineItem; // This DTO is part of ReservationRequestedEvent
import com.mysillydreams.inventorycore.dto.ReservationRequestedEvent;
import com.mysillydreams.inventorycore.repository.StockLevelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;


import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional // Make all public methods transactional by default
@Slf4j
public class ReservationServiceImpl implements ReservationService {

    private final StockLevelRepository stockLevelRepository;
    private final OutboxEventService outboxEventService; // Renamed from 'outbox' in guide to match class name

    @Value("${kafka.topics.reservationSucceeded}")
    private String reservationSucceededTopic;

    @Value("${kafka.topics.reservationFailed}")
    private String reservationFailedTopic;

    @Override
    public void handleReservationRequest(ReservationRequestedEvent event) {
        log.info("Handling reservation request for order ID: {}", event.getOrderId());
        if (event.getItems() == null || event.getItems().isEmpty()) {
            log.warn("Reservation request for order ID {} has no items.", event.getOrderId());
            // Optionally, publish a generic failure or ignore
            return;
        }

        // This logic processes each item individually.
        // A multi-item request might need all-or-nothing semantics,
        // which would require a different approach (e.g., check all first, then reserve all).
        // The current implementation emits one success/failure event PER item.
        // The guide's outbox.publish calls imply eventType is the topic name.
        // Let's use the eventType as a logical name and the OutboxPoller can map to topics.
        // The guide for OutboxPoller uses ev.getEventType().contains("succeeded") ? succTopic : failTopic;
        // So, event types like "InventoryReservationSucceeded" / "InventoryReservationFailed" would work.
        // The guide's example for ReservationServiceImpl uses "order.reservation.succeeded" and "order.reservation.failed"
        // as event types which are also the topic names. This is simpler.

        for (LineItem item : event.getItems()) {
            String sku = item.getSku();
            int quantityToReserve = item.getQuantity();
            log.debug("Processing item: SKU={}, Quantity={}", sku, quantityToReserve);

            if (quantityToReserve <= 0) {
                log.warn("Skipping item with SKU {} due to non-positive quantity: {}", sku, quantityToReserve);
                // Optionally publish a specific failure event for this item if needed
                continue;
            }

            try {
                StockLevel stockLevel = stockLevelRepository.findById(sku)
                        .orElseThrow(() -> new IllegalArgumentException("Unknown SKU: " + sku));

                if (stockLevel.getAvailable() >= quantityToReserve) {
                    stockLevel.setAvailable(stockLevel.getAvailable() - quantityToReserve);
                    stockLevel.setReserved(stockLevel.getReserved() + quantityToReserve);
                    stockLevelRepository.save(stockLevel); // JPA handles optimistic locking via @Version

                    log.info("Stock reserved for SKU {}: {} units. New available: {}, New reserved: {}",
                            sku, quantityToReserve, stockLevel.getAvailable(), stockLevel.getReserved());

                    outboxEventService.publish(
                            "Inventory", // Aggregate Type
                            sku,         // Aggregate ID (SKU)
                            reservationSucceededTopic, // Event Type (using topic name as per guide)
                            Map.of("orderId", event.getOrderId()) // Payload (orderId is already String)
                    );
                } else {
                    log.warn("Insufficient stock for SKU {}: requested {}, available {}. Order ID: {}",
                            sku, quantityToReserve, stockLevel.getAvailable(), event.getOrderId());
                    outboxEventService.publish(
                            "Inventory", // Aggregate Type
                            sku,         // Aggregate ID (SKU)
                            reservationFailedTopic, // Event Type (using topic name as per guide)
                            Map.of(
                                    "orderId", event.getOrderId(),
                                    "reason", "INSUFFICIENT_STOCK",
                                    "sku", sku,
                                    "requestedQuantity", quantityToReserve,
                                    "availableQuantity", stockLevel.getAvailable()
                            )
                    );
                }
            } catch (IllegalArgumentException e) {
                log.error("Error processing reservation for SKU {}: {}. Order ID: {}", sku, e.getMessage(), event.getOrderId());
                outboxEventService.publish(
                        "Inventory",
                        sku,
                        reservationFailedTopic,
                        Map.of(
                                "orderId", event.getOrderId(),
                                "reason", "UNKNOWN_SKU",
                                "sku", sku
                        )
                );
            } catch (Exception e) { // Catching other potential exceptions like OptimisticLockException
                log.error("Failed to reserve stock for SKU {} due to: {}. Order ID: {}", sku, e.getMessage(), event.getOrderId(), e);
                // This is a more generic failure
                outboxEventService.publish(
                        "Inventory",
                        sku,
                        reservationFailedTopic,
                        Map.of(
                                "orderId", event.getOrderId(),
                                "reason", "RESERVATION_PROCESSING_ERROR",
                                "sku", sku,
                                "detail", e.getMessage()
                        )
                );
                // Depending on policy, might rethrow to ensure transaction rollback if not already by a runtime ex.
                // if (!(e instanceof OptimisticLockException)) { throw e; }
            }
        }
    }
}
