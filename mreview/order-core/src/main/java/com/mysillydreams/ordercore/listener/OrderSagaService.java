package com.mysillydreams.ordercore.listener;

// Assuming consumed events are also Avro.
// These would be Avro classes from other domains/services if they publish Avro.
// For example, if Inventory service publishes Avro ReservationSucceededEvent:
// import com.mysillydreams.inventory.dto.avro.ReservationSucceededEvent;
// For now, we'll use placeholders or assume JsonNode if external events are not Avro yet.
// The plan implies Order-Core consumes Avro, so let's define some example Avro DTOs for consumed events.
// These would need their own .avsc files if Order-Core defines them, or Order-Core would depend on
// other services' Avro-generated class JARs.
// For this step, let's assume the consumed events are also Avro and we have placeholder classes.

// Placeholder Avro event classes for consumed messages (these would be generated from schemas)
// These would typically come from other microservices' contracts/schemas.
// For now, let's assume we have local Avro definitions for these for testing, or they are shared.
// If Order-API produces Avro OrderCreatedEvent, we'd use that.
import com.mysillydreams.orderapi.dto.avro.OrderCreatedEvent as OrderApiOrderCreatedEvent; // Example from Order-API

// Let's define simplified Avro-like classes for events consumed by saga for now
// These would be replaced by actual Avro generated classes.
// This is just to make the listener signatures work with Avro types.
// We would need .avsc for these and generate them.
// For now, I'll create simple records to represent the expected structure.
// These are NOT the final Avro classes but stand-ins for the signatures.

// Import the placeholder Avro event DTOs for consumed messages
import com.mysillydreams.ordercore.dto.avro.PaymentSucceededEvent;
import com.mysillydreams.ordercore.dto.avro.ReservationSucceededEvent;

import com.mysillydreams.ordercore.domain.enums.OrderStatus;
import com.mysillydreams.ordercore.service.OrderService;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OrderSagaService {

    private static final Logger log = LoggerFactory.getLogger(OrderSagaService.class);
    private final OrderService orderService;
    // private final OutboxEventService outboxEventService; // If listeners directly publish next saga step

    // Listener for OrderCreatedEvent from Order-API (if this is the flow)
    // The plan implies Order-Core's OrderService.createOrder is the entry point,
    // which then publishes its own "order.created" via outbox.
    // If Order-Core consumes an event from Order-API to *trigger* order creation,
    // that would be a different listener, e.g., on "api.order.requested"
    // Let's assume the listeners here are for downstream saga events AFTER Order-Core has created an order.

    @KafkaListener(
        topics = "${kafka.topics.inventoryReservationSucceeded}",
        groupId = "${kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactoryAvro" // Use Avro factory
    )
    @Transactional
    public void onReservationSucceeded(@Payload ReservationSucceededEvent event) { // Consume Avro type
        try {
            UUID orderId = UUID.fromString(event.orderId()); // Access field from record/Avro object
            log.info("Received ReservationSucceeded event for orderId: {}", orderId);

            // Update order status to PAID
            // The guide states: "orderSvc.updateStatus(id, OrderStatus.PAID, "inventory");"
            // This implies that after reservation, the order is considered ready for payment/paid.
            // This might vary based on exact saga flow (e.g., might go to RESERVED, then trigger payment).
            // Following guide: update to PAID, then trigger payment.
            orderService.updateOrderStatus(orderId, OrderStatus.PAID, "InventoryService");

            // Next step in saga: Trigger payment request
            // This would typically involve publishing an event like RequestPaymentCommand or PaymentRequestedEvent
            // via the outbox.
            // Example:
            // PaymentRequestPayload paymentRequest = new PaymentRequestPayload(orderId, amount, ...);
            // outboxEventService.createAndSaveOutboxEvent("Order", orderId.toString(), "payment.request", paymentRequest);
            log.info("Order {} status updated to PAID after inventory reservation. Next: Trigger payment.", orderId);

        } catch (Exception e) {
            log.error("Error processing ReservationSucceeded event for payload {}: {}", event.toString(), e.getMessage(), e);
            // Error handling: depends on KafkaConsumerConfig's error handler (e.g., retry, DLT)
            throw e; // Re-throw to engage Kafka error handler
        }
    }

    @KafkaListener(
        topics = "${kafka.topics.paymentSucceeded}",
        groupId = "${kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactoryAvro" // Use Avro factory
    )
    @Transactional
    public void onPaymentSucceeded(@Payload PaymentSucceededEvent event) { // Consume Avro type
        try {
            UUID orderId = UUID.fromString(event.orderId());
            log.info("Received PaymentSucceeded event for orderId: {}", orderId);

            // Update order status to CONFIRMED
            orderService.updateOrderStatus(orderId, OrderStatus.CONFIRMED, "PaymentService");

            // Next step in saga: e.g., notify fulfillment, vendor assignment
            log.info("Order {} status updated to CONFIRMED after payment. Next: Trigger fulfillment.", orderId);

        } catch (Exception e) {
            log.error("Error processing PaymentSucceeded event for payload {}: {}", event.toString(), e.getMessage(), e);
            throw e; // Re-throw
        }
    }

    // Listener for OrderCreatedEvent from Order-API (assuming it's Avro)
    // This listener would be responsible for initiating the order creation in Order-Core
    // if the Order-API `order.created` event is the trigger.
    @KafkaListener(
        // Assuming Order-API produces to a topic like "order.api.created" or "order.created"
        // and Order-Core consumes this. The topic name needs to be configured.
        topics = "${kafka.topics.orderApiCreated:order.api.created}",
        groupId = "${kafka.consumer.group-id}", // Can be the same group or a specific one
        containerFactory = "kafkaListenerContainerFactoryAvro" // Use Avro factory
    )
    @Transactional
    public void onOrderApiCreatedEvent(@Payload OrderApiOrderCreatedEvent event) { // Consumes Avro from Order-API
        try {
            log.info("Received OrderApiOrderCreatedEvent for potential new order: API OrderId {}", event.getOrderId());

            // Here, you would map the fields from OrderApiOrderCreatedEvent
            // to a CreateOrderCommand for Order-Core's OrderService.
            // This is where the translation between Order-API's event and Order-Core's command happens.

            // Example mapping (adjust field names based on actual Avro schema from Order-API)
            List<CreateOrderCommand.LineItemCommand> itemCommands = event.getItems().stream()
                .map(apiItem -> CreateOrderCommand.LineItemCommand.builder()
                    .productId(UUID.fromString(apiItem.getProductId())) // Assuming apiItem.getProductId() is String UUID
                    // .productSku(apiItem.getSku()) // If SKU is present in Order-API's LineItem Avro
                    .quantity(apiItem.getQuantity())
                    .unitPrice(BigDecimal.valueOf(apiItem.getPrice())) // Assuming apiItem.getPrice() is double
                    // .discount(apiItem.getDiscount() != null ? BigDecimal.valueOf(apiItem.getDiscount()) : BigDecimal.ZERO)
                    .build())
                .collect(Collectors.toList());

            CreateOrderCommand command = CreateOrderCommand.builder()
                .customerId(UUID.fromString(event.getCustomerId()))
                // .orderType(OrderType.CUSTOMER) // Assuming Order-API events are always CUSTOMER type
                // The OrderType in Order-API's event might be a string or enum. Map accordingly.
                // For simplicity, let's assume CUSTOMER type for events from Order-API.
                .orderType(com.mysillydreams.ordercore.domain.enums.OrderType.CUSTOMER)
                .items(itemCommands)
                .currency(event.getCurrency())
                .build();

            UUID coreOrderId = orderService.createOrder(command);
            log.info("Order-Core successfully processed OrderApiOrderCreatedEvent and created internal order ID: {}", coreOrderId);

        } catch (Exception e) {
            log.error("Error processing OrderApiOrderCreatedEvent for payload {}: {}", event.toString(), e.getMessage(), e);
            throw e; // Re-throw to engage Kafka error handler
        }
    }

    // Add more listeners for other saga steps:
    // - InventoryFailed/ReservationFailed -> Handle compensation (e.g., notify customer, cancel order)
    // - PaymentFailed -> Handle compensation (e.g., retry payment, notify customer, cancel order)
    // - FulfillmentUpdate (e.g., order.shipped from FulfillmentService) -> Update order status in Order-Core
    // - etc.
}
