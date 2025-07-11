package com.mysillydreams.ordercore.listener;

import com.mysillydreams.ordercore.domain.enums.OrderStatus;
import com.mysillydreams.ordercore.service.OrderService;

// Import placeholder Avro records used in OrderSagaService
import com.mysillydreams.ordercore.dto.avro.PaymentSucceededEvent;
import com.mysillydreams.ordercore.dto.avro.ReservationSucceededEvent;
// Import Avro record from Order-API (if OrderSagaService consumes it)
import com.mysillydreams.orderapi.dto.avro.OrderCreatedEvent as OrderApiOrderCreatedEvent;
import com.mysillydreams.orderapi.dto.avro.LineItem as OrderApiLineItem;


import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import com.mysillydreams.ordercore.dto.CreateOrderCommand;


@ExtendWith(MockitoExtension.class)
class OrderSagaServiceTest {

    @Mock
    private OrderService orderService;

    @InjectMocks
    private OrderSagaService orderSagaService;

    @Test
    void onReservationSucceeded_shouldUpdateOrderStatusToPaid() {
        // Given
        String orderIdStr = UUID.randomUUID().toString();
        ReservationSucceededEvent event = new ReservationSucceededEvent(orderIdStr, UUID.randomUUID().toString());

        // When
        orderSagaService.onReservationSucceeded(event);

        // Then
        verify(orderService, times(1)).updateOrderStatus(
            eq(UUID.fromString(orderIdStr)),
            eq(OrderStatus.PAID),
            eq("InventoryService")
        );
    }

    @Test
    void onPaymentSucceeded_shouldUpdateOrderStatusToConfirmed() {
        // Given
        String orderIdStr = UUID.randomUUID().toString();
        PaymentSucceededEvent event = new PaymentSucceededEvent(orderIdStr, UUID.randomUUID().toString(), BigDecimal.TEN);

        // When
        orderSagaService.onPaymentSucceeded(event);

        // Then
        verify(orderService, times(1)).updateOrderStatus(
            eq(UUID.fromString(orderIdStr)),
            eq(OrderStatus.CONFIRMED),
            eq("PaymentService")
        );
    }

    @Test
    void onOrderApiCreatedEvent_shouldCallCreateOrder() {
        // Given
        String apiOrderId = UUID.randomUUID().toString();
        String customerId = UUID.randomUUID().toString();
        String productId = UUID.randomUUID().toString();

        OrderApiLineItem apiLineItem = OrderApiLineItem.newBuilder()
            .setProductId(productId)
            .setProductSku("API-SKU-001")
            .setQuantity(1)
            .setPrice(123.45)
            .setDiscount(0.0)      // Assuming discount is non-null in schema or has default
            .setTotalPrice(123.45) // Assuming total price is also present
            .build();

        OrderApiOrderCreatedEvent event = OrderApiOrderCreatedEvent.newBuilder()
            .setOrderId(apiOrderId)
            .setCustomerId(customerId)
            .setOrderType(com.mysillydreams.orderapi.dto.avro.OrderTypeAvro.CUSTOMER) // Use the enum from order-api's Avro
            .setItems(Collections.singletonList(apiLineItem))
            .setTotalAmount(123.45)
            .setCurrency("EUR")
            .setCreatedAt(Instant.now().toEpochMilli())
            .build();

        // When
        orderSagaService.onOrderApiCreatedEvent(event);

        // Then
        ArgumentCaptor<CreateOrderCommand> commandCaptor = ArgumentCaptor.forClass(CreateOrderCommand.class);
        verify(orderService, times(1)).createOrder(commandCaptor.capture());

        CreateOrderCommand capturedCommand = commandCaptor.getValue();
        assertEquals(UUID.fromString(customerId), capturedCommand.getCustomerId());
        assertEquals(com.mysillydreams.ordercore.domain.enums.OrderType.CUSTOMER, capturedCommand.getOrderType());
        assertEquals("EUR", capturedCommand.getCurrency());
        assertEquals(1, capturedCommand.getItems().size());
        assertEquals(UUID.fromString(productId), capturedCommand.getItems().get(0).getProductId());
        assertEquals(1, capturedCommand.getItems().get(0).getQuantity());
        assertEquals(0, BigDecimal.valueOf(123.45).compareTo(capturedCommand.getItems().get(0).getUnitPrice()));
    }

    // TODO: Add tests for exception handling within listeners, e.g., if event payload is malformed
    // or if orderService throws an exception. This would depend on how Kafka error handlers are configured
    // and if listeners are expected to catch and handle specific exceptions or let them propagate.
}
