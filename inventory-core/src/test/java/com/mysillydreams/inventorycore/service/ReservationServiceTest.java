package com.mysillydreams.inventorycore.service;

import com.mysillydreams.inventorycore.domain.StockLevel;
import com.mysillydreams.inventorycore.dto.LineItem;
import com.mysillydreams.inventorycore.dto.ReservationRequestedEvent;
import com.mysillydreams.inventorycore.repository.StockLevelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock
    private StockLevelRepository stockLevelRepository;

    @Mock
    private OutboxEventService outboxEventService;

    @InjectMocks
    private ReservationServiceImpl reservationService;

    private final String SUCCEEDED_TOPIC = "order.reservation.succeeded";
    private final String FAILED_TOPIC = "order.reservation.failed";

    @BeforeEach
    void setUp() {
        // Inject topic names using ReflectionTestUtils as @Value won't work directly in this unit test setup
        // without bringing up more Spring context.
        ReflectionTestUtils.setField(reservationService, "reservationSucceededTopic", SUCCEEDED_TOPIC);
        ReflectionTestUtils.setField(reservationService, "reservationFailedTopic", FAILED_TOPIC);
    }

    @Test
    void handleReservationRequest_whenStockIsAvailable_shouldReserveAndPublishSuccess() {
        // Arrange
        String sku = "SKU123";
        String orderId = "ORDER789";
        int quantityToReserve = 5;
        int initialAvailable = 10;

        ReservationRequestedEvent event = new ReservationRequestedEvent(orderId,
                List.of(new LineItem(sku, quantityToReserve)));

        StockLevel stockLevel = new StockLevel(sku, initialAvailable, 0, 0L, Instant.now());
        when(stockLevelRepository.findById(sku)).thenReturn(Optional.of(stockLevel));
        // outboxEventService.publish is void, so no when(..).thenReturn(..) needed unless verifying interactions

        // Act
        reservationService.handleReservationRequest(event);

        // Assert
        // Verify stock level updated and saved
        ArgumentCaptor<StockLevel> stockLevelCaptor = ArgumentCaptor.forClass(StockLevel.class);
        verify(stockLevelRepository).save(stockLevelCaptor.capture());
        StockLevel savedStockLevel = stockLevelCaptor.getValue();
        assertEquals(initialAvailable - quantityToReserve, savedStockLevel.getAvailable());
        assertEquals(quantityToReserve, savedStockLevel.getReserved());

        // Verify success event published to outbox
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(outboxEventService).publish(
                eq("Inventory"),
                eq(sku),
                eq(SUCCEEDED_TOPIC),
                payloadCaptor.capture()
        );
        assertEquals(orderId, payloadCaptor.getValue().get("orderId"));
        verifyNoMoreInteractions(outboxEventService); // Ensure only one event published for this item
    }

    @Test
    void handleReservationRequest_whenStockIsNotAvailable_shouldPublishFailure() {
        // Arrange
        String sku = "SKU456";
        String orderId = "ORDER123";
        int quantityToReserve = 10;
        int initialAvailable = 5; // Not enough

        ReservationRequestedEvent event = new ReservationRequestedEvent(orderId,
                List.of(new LineItem(sku, quantityToReserve)));
        StockLevel stockLevel = new StockLevel(sku, initialAvailable, 0, 0L, Instant.now());
        when(stockLevelRepository.findById(sku)).thenReturn(Optional.of(stockLevel));

        // Act
        reservationService.handleReservationRequest(event);

        // Assert
        // Verify stock level NOT saved (no change)
        verify(stockLevelRepository, never()).save(any(StockLevel.class));

        // Verify failure event published
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(outboxEventService).publish(
                eq("Inventory"),
                eq(sku),
                eq(FAILED_TOPIC),
                payloadCaptor.capture()
        );
        Map<String, Object> capturedPayload = payloadCaptor.getValue();
        assertEquals(orderId, capturedPayload.get("orderId"));
        assertEquals("INSUFFICIENT_STOCK", capturedPayload.get("reason"));
        assertEquals(sku, capturedPayload.get("sku"));
        assertEquals(quantityToReserve, capturedPayload.get("requestedQuantity"));
        assertEquals(initialAvailable, capturedPayload.get("availableQuantity"));
        verifyNoMoreInteractions(outboxEventService);
    }

    @Test
    void handleReservationRequest_whenSkuIsUnknown_shouldPublishFailure() {
        // Arrange
        String sku = "UNKNOWN_SKU";
        String orderId = "ORDER_X";
        int quantityToReserve = 2;

        ReservationRequestedEvent event = new ReservationRequestedEvent(orderId,
                List.of(new LineItem(sku, quantityToReserve)));
        when(stockLevelRepository.findById(sku)).thenReturn(Optional.empty());

        // Act
        reservationService.handleReservationRequest(event);

        // Assert
        verify(stockLevelRepository, never()).save(any(StockLevel.class));
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(outboxEventService).publish(
                eq("Inventory"),
                eq(sku),
                eq(FAILED_TOPIC),
                payloadCaptor.capture()
        );
        Map<String, Object> capturedPayload = payloadCaptor.getValue();
        assertEquals(orderId, capturedPayload.get("orderId"));
        assertEquals("UNKNOWN_SKU", capturedPayload.get("reason"));
        assertEquals(sku, capturedPayload.get("sku"));
        verifyNoMoreInteractions(outboxEventService);
    }

    @Test
    void handleReservationRequest_withZeroQuantityItem_shouldSkipAndNotPublish() {
        // Arrange
        String sku = "SKU_ZERO_QTY";
        String orderId = "ORDER_Z";
        ReservationRequestedEvent event = new ReservationRequestedEvent(orderId,
                List.of(new LineItem(sku, 0))); // Zero quantity

        // Act
        reservationService.handleReservationRequest(event);

        // Assert
        verify(stockLevelRepository, never()).findById(anyString());
        verify(stockLevelRepository, never()).save(any(StockLevel.class));
        verifyNoInteractions(outboxEventService); // Nothing should be published
    }

    @Test
    void handleReservationRequest_withNegativeQuantityItem_shouldSkipAndNotPublish() {
        // Arrange
        String sku = "SKU_NEG_QTY";
        String orderId = "ORDER_N";
        ReservationRequestedEvent event = new ReservationRequestedEvent(orderId,
                List.of(new LineItem(sku, -1))); // Negative quantity

        // Act
        reservationService.handleReservationRequest(event);

        // Assert
        verify(stockLevelRepository, never()).findById(anyString());
        verify(stockLevelRepository, never()).save(any(StockLevel.class));
        verifyNoInteractions(outboxEventService);
    }


    @Test
    void handleReservationRequest_withEmptyItemList_shouldDoNothing() {
        // Arrange
        String orderId = "ORDER_EMPTY";
        ReservationRequestedEvent event = new ReservationRequestedEvent(orderId, Collections.emptyList());

        // Act
        reservationService.handleReservationRequest(event);

        // Assert
        verifyNoInteractions(stockLevelRepository);
        verifyNoInteractions(outboxEventService);
    }

    @Test
    void handleReservationRequest_whenRepositorySaveFails_shouldPublishGenericFailure() {
        // Arrange
        String sku = "SKU_SAVE_FAIL";
        String orderId = "ORDER_SAVE_FAIL";
        int quantityToReserve = 1;
        ReservationRequestedEvent event = new ReservationRequestedEvent(orderId,
                List.of(new LineItem(sku, quantityToReserve)));

        StockLevel stockLevel = new StockLevel(sku, 1, 0, 0L, Instant.now());
        when(stockLevelRepository.findById(sku)).thenReturn(Optional.of(stockLevel));
        // Simulate OptimisticLockException or other save failure
        doThrow(new RuntimeException("DB save failed")).when(stockLevelRepository).save(any(StockLevel.class));

        // Act
        reservationService.handleReservationRequest(event);

        // Assert
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(outboxEventService).publish(
                eq("Inventory"),
                eq(sku),
                eq(FAILED_TOPIC),
                payloadCaptor.capture()
        );
        Map<String, Object> capturedPayload = payloadCaptor.getValue();
        assertEquals(orderId, capturedPayload.get("orderId"));
        assertEquals("RESERVATION_PROCESSING_ERROR", capturedPayload.get("reason"));
        assertEquals("DB save failed", capturedPayload.get("detail"));
    }
}
