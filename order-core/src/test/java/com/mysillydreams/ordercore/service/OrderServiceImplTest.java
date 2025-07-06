package com.mysillydreams.ordercore.service;

import com.mysillydreams.ordercore.domain.Order;
import com.mysillydreams.ordercore.domain.OrderItem;
import com.mysillydreams.ordercore.domain.OrderStatusHistory;
import com.mysillydreams.ordercore.domain.enums.OrderStatus;
import com.mysillydreams.ordercore.domain.enums.OrderType;
import com.mysillydreams.ordercore.dto.CreateOrderCommand;
// Assuming Avro events are published via Outbox
import com.mysillydreams.ordercore.dto.avro.OrderCancelledEvent as AvroOrderCancelledEvent;
import com.mysillydreams.ordercore.dto.avro.OrderCreatedEvent as AvroOrderCreatedEvent;
import com.mysillydreams.ordercore.dto.avro.OrderStatusUpdatedEvent as AvroOrderStatusUpdatedEvent;
import com.mysillydreams.ordercore.dto.avro.LineItem as AvroLineItem;

import com.mysillydreams.ordercore.repository.OrderRepository;
import com.mysillydreams.ordercore.repository.OrderItemRepository;
import com.mysillydreams.ordercore.repository.OrderStatusHistoryRepository;
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
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderItemRepository orderItemRepository; // Added, though not directly verified in these tests yet
    @Mock
    private OrderStatusHistoryRepository orderStatusHistoryRepository;
    @Mock
    private OutboxEventService outboxEventService;

    @InjectMocks
    private OrderServiceImpl orderService;

    private CreateOrderCommand createOrderCommand;
    private UUID customerId;
    private UUID productId;

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();
        productId = UUID.randomUUID();

        CreateOrderCommand.LineItemCommand lineItemCmd = CreateOrderCommand.LineItemCommand.builder()
                .productId(productId)
                .productSku("SKU123")
                .quantity(2)
                .unitPrice(new BigDecimal("10.00"))
                .discount(new BigDecimal("1.00")) // Total discount for the line (2 units * 0.50 discount_per_unit)
                .build();

        createOrderCommand = CreateOrderCommand.builder()
                .customerId(customerId)
                .orderType(OrderType.CUSTOMER)
                .items(Collections.singletonList(lineItemCmd))
                .currency("USD")
                .build();
    }

    @Test
    void createOrder_shouldSaveOrderAndPublishAvroEvent() {
        // Given
        // Mocking what service logic would do before repository.save() and what repo returns
        Order orderToSave = new Order();
        // ID is set by service
        orderToSave.setCustomerId(customerId);
        orderToSave.setType(OrderType.CUSTOMER);
        orderToSave.setCurrency("USD");
        orderToSave.setCurrentStatus(OrderStatus.CREATED);

        // Mock the items and total amount calculation as done in the service
        OrderItem item = new OrderItem();
        item.setId(UUID.randomUUID()); // Service generates this
        item.setProductId(productId);
        item.setProductSku("SKU123");
        item.setQuantity(2);
        item.setUnitPrice(new BigDecimal("10.00"));
        item.setDiscount(new BigDecimal("1.00"));
        item.setTotalPrice(new BigDecimal("10.00").multiply(BigDecimal.valueOf(2)).subtract(new BigDecimal("1.00"))); // 19.00
        orderToSave.addItem(item); // This also sets item.setOrder(orderToSave) internally
        orderToSave.setTotalAmount(item.getTotalPrice());


        // When orderRepository.save is called, it will have an ID. We capture that argument.
        // For the return value of save, we can return the captured argument or a slightly modified one.
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        when(orderRepository.save(orderCaptor.capture())).thenAnswer(invocation -> {
            Order capturedOrder = invocation.getArgument(0);
            if (capturedOrder.getId() == null) capturedOrder.setId(UUID.randomUUID()); // Ensure ID is set like DB would
            capturedOrder.setCreatedAt(Instant.now()); // Simulate timestamp generation
            capturedOrder.setUpdatedAt(Instant.now());
            return capturedOrder;
        });


        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<String> eventTypeCaptor = ArgumentCaptor.forClass(String.class);

        // When
        UUID createdOrderId = orderService.createOrder(createOrderCommand);

        // Then
        assertNotNull(createdOrderId);
        Order savedOrder = orderCaptor.getValue(); // Get the order that was passed to repository.save()
        assertEquals(createdOrderId, savedOrder.getId());


        verify(orderRepository).save(any(Order.class));
        verify(orderStatusHistoryRepository).save(any(OrderStatusHistory.class));
        verify(outboxEventService).createAndSaveOutboxEvent(eq("Order"), eq(createdOrderId.toString()), eventTypeCaptor.capture(), payloadCaptor.capture());

        assertEquals("order.created", eventTypeCaptor.getValue());
        assertTrue(payloadCaptor.getValue() instanceof AvroOrderCreatedEvent);
        AvroOrderCreatedEvent publishedEvent = (AvroOrderCreatedEvent) payloadCaptor.getValue();

        assertEquals(savedOrder.getId().toString(), publishedEvent.getOrderId());
        assertEquals(customerId.toString(), publishedEvent.getCustomerId());
        // Ensure Avro enum name matches Java enum name if using .name()
        assertEquals(OrderType.CUSTOMER.name(), publishedEvent.getOrderType().name());
        assertEquals(1, publishedEvent.getItems().size());
        AvroLineItem publishedAvroItem = publishedEvent.getItems().get(0);
        assertEquals(productId.toString(), publishedAvroItem.getProductId());
        assertEquals("SKU123", publishedAvroItem.getProductSku());
        assertEquals(2, publishedAvroItem.getQuantity());
        assertEquals(10.00, publishedAvroItem.getUnitPrice(), 0.001);
        assertEquals(1.00, publishedAvroItem.getDiscount(), 0.001);
        assertEquals(19.00, publishedAvroItem.getTotalPrice(), 0.001);

        assertEquals(savedOrder.getTotalAmount().doubleValue(), publishedEvent.getTotalAmount(), 0.001);
    }

    @Test
    void updateOrderStatus_shouldUpdateAndPublishAvroEvent() {
        // Given
        UUID orderId = UUID.randomUUID();
        Order existingOrder = new Order();
        existingOrder.setId(orderId);
        existingOrder.setCurrentStatus(OrderStatus.CREATED);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(existingOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(existingOrder);

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<String> eventTypeCaptor = ArgumentCaptor.forClass(String.class);

        // When
        orderService.updateOrderStatus(orderId, OrderStatus.PAID, "PaymentService");

        // Then
        assertEquals(OrderStatus.PAID, existingOrder.getCurrentStatus());
        verify(orderRepository).save(existingOrder);
        verify(orderStatusHistoryRepository).save(any(OrderStatusHistory.class));
        verify(outboxEventService).createAndSaveOutboxEvent(eq("Order"), eq(orderId.toString()), eventTypeCaptor.capture(), payloadCaptor.capture());

        // Event type should be like "order.status.paid" based on OrderServiceImpl
        assertEquals("order.status.paid", eventTypeCaptor.getValue().toLowerCase());
        assertTrue(payloadCaptor.getValue() instanceof AvroOrderStatusUpdatedEvent);
        AvroOrderStatusUpdatedEvent statusEvent = (AvroOrderStatusUpdatedEvent) payloadCaptor.getValue();
        assertEquals(orderId.toString(), statusEvent.getOrderId());
        // Avro enums generated might not be same instance as Java enums, compare by name or value
        assertEquals(OrderStatus.CREATED.name(), statusEvent.getOldStatus().name());
        assertEquals(OrderStatus.PAID.name(), statusEvent.getNewStatus().name());
        assertEquals("PaymentService", statusEvent.getChangedBy());
    }

    @Test
    void cancelOrder_shouldUpdateStatusAndPublishTwoAvroEvents() {
        // Given
        UUID orderId = UUID.randomUUID();
        String reason = "Customer request";
        String changedBy = "CustomerService";
        Order existingOrder = new Order();
        existingOrder.setId(orderId);
        existingOrder.setCurrentStatus(OrderStatus.CREATED);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(existingOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(existingOrder);

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<String> eventTypeCaptor = ArgumentCaptor.forClass(String.class);

        // When
        orderService.cancelOrder(orderId, reason, changedBy);

        // Then
        assertEquals(OrderStatus.CANCELLED, existingOrder.getCurrentStatus());
        verify(orderRepository).save(existingOrder);
        verify(orderStatusHistoryRepository).save(any(OrderStatusHistory.class));

        verify(outboxEventService, times(2)).createAndSaveOutboxEvent(eq("Order"), eq(orderId.toString()), eventTypeCaptor.capture(), payloadCaptor.capture());

        List<String> capturedEventTypes = eventTypeCaptor.getAllValues();
        List<Object> capturedPayloads = payloadCaptor.getAllValues();

        // Check OrderStatusUpdatedEvent for "order.status.cancelled"
        assertTrue(capturedEventTypes.stream().anyMatch(et -> et.equalsIgnoreCase("order.status.cancelled")));
        Optional<AvroOrderStatusUpdatedEvent> statusUpdatePayloadOpt = capturedPayloads.stream()
            .filter(AvroOrderStatusUpdatedEvent.class::isInstance)
            .map(AvroOrderStatusUpdatedEvent.class::cast)
            .filter(event -> event.getNewStatus().name().equals(OrderStatus.CANCELLED.name()))
            .findFirst();
        assertTrue(statusUpdatePayloadOpt.isPresent());
        AvroOrderStatusUpdatedEvent statusEvent = statusUpdatePayloadOpt.get();
        assertEquals(orderId.toString(), statusEvent.getOrderId());

        // Check OrderCancelledEvent for "order.cancelled"
        assertTrue(capturedEventTypes.stream().anyMatch(et -> et.equalsIgnoreCase("order.cancelled")));
        Optional<AvroOrderCancelledEvent> cancelPayloadOpt = capturedPayloads.stream()
            .filter(AvroOrderCancelledEvent.class::isInstance)
            .findFirst();
        assertTrue(cancelPayloadOpt.isPresent());
        AvroOrderCancelledEvent cancelEvent = cancelPayloadOpt.get();
        assertEquals(orderId.toString(), cancelEvent.getOrderId());
        assertEquals(reason, cancelEvent.getReason());
    }
}
