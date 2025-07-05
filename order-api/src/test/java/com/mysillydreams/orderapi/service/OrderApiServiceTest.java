package com.mysillydreams.orderapi.service;

import com.mysillydreams.orderapi.dto.CreateOrderRequest;
import com.mysillydreams.orderapi.dto.LineItemDto;
import com.mysillydreams.orderapi.dto.OrderCancelledEvent;
import com.mysillydreams.orderapi.dto.OrderCreatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderApiServiceTest {

    @Mock
    private KafkaOrderPublisher kafkaOrderPublisher;

    @InjectMocks
    private OrderApiService orderApiService;

    private CreateOrderRequest createOrderRequest;
    private UUID customerId;
    private UUID productId;

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();
        productId = UUID.randomUUID();

        LineItemDto lineItem = new LineItemDto(productId, 2, BigDecimal.TEN);
        createOrderRequest = new CreateOrderRequest(
                customerId, // This would be set by controller, but for service test, we set it directly or pass
                Collections.singletonList(lineItem),
                "USD"
        );
        // Ensure customerId is set in the request as the service expects it
        createOrderRequest.setCustomerId(customerId);
    }

    @Test
    void createOrder_shouldPublishOrderCreatedEvent() {
        // Given
        String idempotencyKey = UUID.randomUUID().toString();
        ArgumentCaptor<OrderCreatedEvent> eventCaptor = ArgumentCaptor.forClass(OrderCreatedEvent.class);

        // When
        UUID orderId = orderApiService.createOrder(createOrderRequest, idempotencyKey);

        // Then
        assertNotNull(orderId);
        verify(kafkaOrderPublisher, times(1)).publishOrderCreated(eventCaptor.capture());

        OrderCreatedEvent publishedEvent = eventCaptor.getValue();
        assertEquals(orderId, publishedEvent.getOrderId());
        assertEquals(customerId, publishedEvent.getCustomerId());
        assertEquals("USD", publishedEvent.getCurrency());
        assertEquals(1, publishedEvent.getItems().size());
        assertEquals(productId, publishedEvent.getItems().get(0).getProductId());
        assertEquals(2, publishedEvent.getItems().get(0).getQuantity());
        assertEquals(BigDecimal.TEN, publishedEvent.getItems().get(0).getPrice());
        assertEquals(new BigDecimal("20"), publishedEvent.getTotalAmount()); // 2 * 10
        assertNotNull(publishedEvent.getCreatedAt());
    }

    @Test
    void createOrder_withMultipleItems_calculatesCorrectTotal() {
        // Given
        String idempotencyKey = UUID.randomUUID().toString();
        LineItemDto item1 = new LineItemDto(UUID.randomUUID(), 1, new BigDecimal("10.50"));
        LineItemDto item2 = new LineItemDto(UUID.randomUUID(), 3, new BigDecimal("5.25"));
        createOrderRequest.setItems(List.of(item1, item2)); // total = 10.50 + (3 * 5.25) = 10.50 + 15.75 = 26.25

        ArgumentCaptor<OrderCreatedEvent> eventCaptor = ArgumentCaptor.forClass(OrderCreatedEvent.class);

        // When
        orderApiService.createOrder(createOrderRequest, idempotencyKey);

        // Then
        verify(kafkaOrderPublisher).publishOrderCreated(eventCaptor.capture());
        OrderCreatedEvent publishedEvent = eventCaptor.getValue();
        assertEquals(new BigDecimal("26.25"), publishedEvent.getTotalAmount());
    }

    @Test
    void createOrder_withEmptyItems_calculatesZeroTotal() {
        // Given
        String idempotencyKey = UUID.randomUUID().toString();
        createOrderRequest.setItems(Collections.emptyList());
        ArgumentCaptor<OrderCreatedEvent> eventCaptor = ArgumentCaptor.forClass(OrderCreatedEvent.class);

        // When
        orderApiService.createOrder(createOrderRequest, idempotencyKey);

        // Then
        verify(kafkaOrderPublisher).publishOrderCreated(eventCaptor.capture());
        OrderCreatedEvent publishedEvent = eventCaptor.getValue();
        assertEquals(BigDecimal.ZERO, publishedEvent.getTotalAmount());
    }


    @Test
    void cancelOrder_shouldPublishOrderCancelledEvent() {
        // Given
        UUID orderIdToCancel = UUID.randomUUID();
        String reason = "Test cancellation";
        ArgumentCaptor<OrderCancelledEvent> eventCaptor = ArgumentCaptor.forClass(OrderCancelledEvent.class);

        // When
        orderApiService.cancelOrder(orderIdToCancel, reason);

        // Then
        verify(kafkaOrderPublisher, times(1)).publishOrderCancelled(eventCaptor.capture());

        OrderCancelledEvent publishedEvent = eventCaptor.getValue();
        assertEquals(orderIdToCancel, publishedEvent.getOrderId());
        assertEquals(reason, publishedEvent.getReason());
        assertNotNull(publishedEvent.getCancelledAt());
    }
}
