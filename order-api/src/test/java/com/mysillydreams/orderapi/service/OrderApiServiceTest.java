package com.mysillydreams.orderapi.service;

import com.mysillydreams.orderapi.dto.CreateOrderRequest;
import com.mysillydreams.orderapi.dto.LineItemDto;
// Import Avro generated event classes
import com.mysillydreams.orderapi.dto.avro.OrderCancelledEvent as AvroOrderCancelledEvent;
import com.mysillydreams.orderapi.dto.avro.OrderCreatedEvent as AvroOrderCreatedEvent;
import com.mysillydreams.orderapi.dto.avro.LineItem as AvroLineItem;
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
        ArgumentCaptor<AvroOrderCreatedEvent> eventCaptor = ArgumentCaptor.forClass(AvroOrderCreatedEvent.class);

        // When
        UUID orderId = orderApiService.createOrder(createOrderRequest, idempotencyKey);

        // Then
        assertNotNull(orderId);
        verify(kafkaOrderPublisher, times(1)).publishOrderCreated(eventCaptor.capture());

        AvroOrderCreatedEvent publishedEvent = eventCaptor.getValue();
        assertEquals(orderId.toString(), publishedEvent.getOrderId());
        assertEquals(customerId.toString(), publishedEvent.getCustomerId());
        assertEquals("USD", publishedEvent.getCurrency());
        assertEquals(1, publishedEvent.getItems().size());

        AvroLineItem publishedItem = publishedEvent.getItems().get(0);
        assertEquals(productId.toString(), publishedItem.getProductId());
        assertEquals(2, publishedItem.getQuantity());
        assertEquals(10.0, publishedItem.getPrice(), 0.001); // Compare doubles with delta

        assertEquals(20.0, publishedEvent.getTotalAmount(), 0.001); // 2 * 10.0
        assertNotNull(publishedEvent.getCreatedAt()); // This is now a long (epoch milli)
    }

    @Test
    void createOrder_withMultipleItems_calculatesCorrectTotal() {
        // Given
        String idempotencyKey = UUID.randomUUID().toString();
        UUID product1Id = UUID.randomUUID();
        UUID product2Id = UUID.randomUUID();
        LineItemDto item1 = new LineItemDto(product1Id, 1, new BigDecimal("10.50"));
        LineItemDto item2 = new LineItemDto(product2Id, 3, new BigDecimal("5.25"));
        createOrderRequest.setItems(List.of(item1, item2)); // total = 10.50 + (3 * 5.25) = 10.50 + 15.75 = 26.25

        ArgumentCaptor<AvroOrderCreatedEvent> eventCaptor = ArgumentCaptor.forClass(AvroOrderCreatedEvent.class);

        // When
        orderApiService.createOrder(createOrderRequest, idempotencyKey);

        // Then
        verify(kafkaOrderPublisher).publishOrderCreated(eventCaptor.capture());
        AvroOrderCreatedEvent publishedEvent = eventCaptor.getValue();
        assertEquals(26.25, publishedEvent.getTotalAmount(), 0.001);
    }

    @Test
    void createOrder_withEmptyItems_calculatesZeroTotal() {
        // Given
        String idempotencyKey = UUID.randomUUID().toString();
        createOrderRequest.setItems(Collections.emptyList());
        ArgumentCaptor<AvroOrderCreatedEvent> eventCaptor = ArgumentCaptor.forClass(AvroOrderCreatedEvent.class);

        // When
        orderApiService.createOrder(createOrderRequest, idempotencyKey);

        // Then
        verify(kafkaOrderPublisher).publishOrderCreated(eventCaptor.capture());
        AvroOrderCreatedEvent publishedEvent = eventCaptor.getValue();
        assertEquals(0.0, publishedEvent.getTotalAmount(), 0.001);
    }


    @Test
    void cancelOrder_shouldPublishOrderCancelledEvent() {
        // Given
        UUID orderIdToCancel = UUID.randomUUID();
        String reason = "Test cancellation";
        ArgumentCaptor<AvroOrderCancelledEvent> eventCaptor = ArgumentCaptor.forClass(AvroOrderCancelledEvent.class);

        // When
        orderApiService.cancelOrder(orderIdToCancel, reason);

        // Then
        verify(kafkaOrderPublisher, times(1)).publishOrderCancelled(eventCaptor.capture());

        AvroOrderCancelledEvent publishedEvent = eventCaptor.getValue();
        assertEquals(orderIdToCancel.toString(), publishedEvent.getOrderId());
        assertEquals(reason, publishedEvent.getReason());
        assertNotNull(publishedEvent.getCancelledAt()); // This is now a long (epoch milli)
    }
}
