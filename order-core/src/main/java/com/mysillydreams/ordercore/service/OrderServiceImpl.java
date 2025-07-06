package com.mysillydreams.ordercore.service;

import com.mysillydreams.ordercore.domain.Order;
import com.mysillydreams.ordercore.domain.OrderItem;
import com.mysillydreams.ordercore.domain.OrderStatusHistory;
import com.mysillydreams.ordercore.domain.enums.OrderStatus;
import com.mysillydreams.ordercore.dto.CreateOrderCommand;
import com.mysillydreams.ordercore.dto.OrderCancelledEventDto;
import com.mysillydreams.ordercore.dto.OrderCreatedEventDto;
import com.mysillydreams.ordercore.dto.OrderStatusUpdatedEventDto; // Create this DTO
import com.mysillydreams.ordercore.repository.OrderRepository;
import com.mysillydreams.ordercore.repository.OrderItemRepository; // Not used in guide's snippet directly but likely needed
import com.mysillydreams.ordercore.repository.OrderStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityNotFoundException; // Standard JPA exception

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional // Apply transactionality to all public methods by default
public class OrderServiceImpl implements OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository; // Added for saving items
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final OutboxEventService outboxEventService;

    @Override
    public UUID createOrder(CreateOrderCommand cmd) {
        log.info("Attempting to create order for customer: {}", cmd.getCustomerId());

        Order order = new Order();
        order.setId(UUID.randomUUID()); // Generate new Order ID
        order.setCustomerId(cmd.getCustomerId());
        order.setType(cmd.getOrderType());
        order.setCurrency(cmd.getCurrency());
        order.setCurrentStatus(OrderStatus.CREATED); // Initial status
        // version, createdAt, updatedAt will be set by JPA/Hibernate

        // Calculate total amount and map items
        BigDecimal totalOrderAmount = BigDecimal.ZERO;
        if (cmd.getItems() != null) {
            for (CreateOrderCommand.LineItemCommand itemCmd : cmd.getItems()) {
                OrderItem orderItem = new OrderItem();
                orderItem.setId(UUID.randomUUID());
                orderItem.setProductId(itemCmd.getProductId());
                orderItem.setProductSku(itemCmd.getProductSku()); // Assuming SKU is passed or can be fetched
                orderItem.setQuantity(itemCmd.getQuantity());
                orderItem.setUnitPrice(itemCmd.getUnitPrice());

                BigDecimal itemDiscount = itemCmd.getDiscount() != null ? itemCmd.getDiscount() : BigDecimal.ZERO;
                orderItem.setDiscount(itemDiscount);

                BigDecimal itemSubTotal = itemCmd.getUnitPrice().multiply(BigDecimal.valueOf(itemCmd.getQuantity()));
                BigDecimal itemTotal = itemSubTotal.subtract(itemDiscount);
                orderItem.setTotalPrice(itemTotal);

                order.addItem(orderItem); // This also sets orderItem.setOrder(this)
                totalOrderAmount = totalOrderAmount.add(itemTotal);
            }
        }
        order.setTotalAmount(totalOrderAmount);

        Order savedOrder = orderRepository.save(order);
        // Note: orderItemRepository.saveAll(order.getItems()) might be needed if CascadeType.ALL isn't effective
        // or if items are managed separately before being added to order.
        // With CascadeType.ALL on Order.items, saving Order should cascade to OrderItems.

        // Record initial status history
        addOrderStatusHistory(savedOrder, null, OrderStatus.CREATED, "system", null);

        // Publish OrderCreatedEvent via Outbox
        List<OrderCreatedEventDto.LineItemDto> eventItems = savedOrder.getItems().stream()
            .map(oi -> new OrderCreatedEventDto.LineItemDto(
                oi.getProductId(), oi.getProductSku(), oi.getQuantity(), oi.getUnitPrice(), oi.getDiscount(), oi.getTotalPrice()))
            .collect(Collectors.toList());

        OrderCreatedEventDto eventDto = new OrderCreatedEventDto(
            savedOrder.getId(),
            savedOrder.getCustomerId(),
            savedOrder.getType(),
            eventItems,
            savedOrder.getTotalAmount(),
            savedOrder.getCurrency(),
            savedOrder.getCreatedAt()
        );
        outboxEventService.createAndSaveOutboxEvent("Order", savedOrder.getId().toString(), "order.created", eventDto);

        log.info("Order created successfully with ID: {}", savedOrder.getId());
        return savedOrder.getId();
    }

    @Override
    public void updateOrderStatus(UUID orderId, OrderStatus newStatus, String changedBy) {
        log.info("Updating status for orderId: {} to {} by {}", orderId, newStatus, changedBy);
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found with ID: " + orderId));

        OrderStatus oldStatus = order.getCurrentStatus();
        if (oldStatus == newStatus) {
            log.warn("Order {} is already in status {}. No update performed.", orderId, newStatus);
            return;
        }

        order.setCurrentStatus(newStatus);
        orderRepository.save(order); // Version will be incremented

        addOrderStatusHistory(order, oldStatus, newStatus, changedBy, null); // Metadata can be added if needed

        // Publish OrderStatusUpdatedEvent via Outbox
        // Example: eventType could be "order.status.paid", "order.status.confirmed"
        String eventType = "order.status." + newStatus.name().toLowerCase();
        OrderStatusUpdatedEventDto eventDto = new OrderStatusUpdatedEventDto(orderId, oldStatus, newStatus, changedBy, Instant.now());
        outboxEventService.createAndSaveOutboxEvent("Order", orderId.toString(), eventType, eventDto);

        log.info("Successfully updated status for orderId: {} from {} to {} by {}", orderId, oldStatus, newStatus, changedBy);
    }

    @Override
    public void cancelOrder(UUID orderId, String reason, String changedBy) {
        log.info("Attempting to cancel orderId: {} by {} with reason: {}", orderId, changedBy, reason);
        // First, update status to CANCELLED. This will also publish a generic status update event.
        updateOrderStatus(orderId, OrderStatus.CANCELLED, changedBy);

        // Then, publish a specific OrderCancelledEvent as per the guide's snippet for OrderServiceImpl.
        // This might be redundant if the generic status update is sufficient, or it might carry more specific cancellation details.
        // The guide's example implies a separate specific event.
        OrderCancelledEventDto eventDto = new OrderCancelledEventDto(orderId, reason, Instant.now());
        outboxEventService.createAndSaveOutboxEvent("Order", orderId.toString(), "order.cancelled", eventDto);

        log.info("Order cancellation process completed for orderId: {}", orderId);
    }

    private void addOrderStatusHistory(Order order, OrderStatus oldStatus, OrderStatus newStatus, String changedBy, Map<String, Object> metadata) {
        OrderStatusHistory historyEntry = new OrderStatusHistory(order, oldStatus, newStatus, changedBy, metadata);
        // order.addStatusHistory(historyEntry); // This would add to list but not save directly if Order manages collection
        orderStatusHistoryRepository.save(historyEntry); // Explicit save
    }
}
