package com.mysillydreams.delivery.listener;

// Import the Avro generated class for the event this listener consumes
import com.mysillydreams.delivery.dto.avro.ShipmentRequestedEvent;
// Import DTO used by AssignmentService if it's different (it is, ShipmentRequestedDto vs Avro)
// Or update AssignmentService to take Avro ShipmentRequestedEvent directly.
// Import the Avro generated class for the event this listener consumes
// (Already imported as com.mysillydreams.delivery.dto.avro.ShipmentRequestedEvent via previous diff)
// No longer need AddressDto or old ShipmentRequestedDto here as AssignmentService now takes Avro event.

import com.mysillydreams.delivery.service.AssignmentService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional; // If createAssignment is transactional

@Component
@RequiredArgsConstructor
public class ShipmentRequestedListener {

    private static final Logger log = LoggerFactory.getLogger(ShipmentRequestedListener.class);
    private final AssignmentService assignmentService;

    // Topic name from application.yml: kafka.topics.orderShipmentRequested
    // The plan's topic list for Delivery Service: order.shipment.requested (Inbound)
    // Let's use a property for this topic name.
    @KafkaListener(
        topics = "${kafka.topics.orderShipmentRequested:order.shipment.requested}",
        groupId = "${kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactoryAvro" // Use Avro factory
    )
    public void onShipmentRequested(@Payload ShipmentRequestedEvent eventPayload) { // Consume Avro type
        log.info("Received Avro order.shipment.requested event for orderId: {}", eventPayload.getOrderId());
        try {
            // Basic validation for Avro object (non-null fields are usually enforced by schema if not optional)
            if (eventPayload.getOrderId() == null ||
                eventPayload.getVendorId() == null ||
                eventPayload.getCustomerId() == null ||
                eventPayload.getPickupAddress() == null ||
                eventPayload.getDropoffAddress() == null) {
                log.error("Invalid Avro ShipmentRequestedEvent received, missing required fields: {}", eventPayload);
                throw new IllegalArgumentException("Received Avro ShipmentRequestedEvent with missing required fields.");
            }

            // AssignmentService.createAssignment now takes Avro ShipmentRequestedEvent
            UUID assignmentId = assignmentService.createAssignment(eventPayload);
            log.info("Successfully processed Avro shipment request for orderId: {}. Delivery assignmentId: {}",
                     eventPayload.getOrderId(), assignmentId);

        } catch (IllegalStateException e) {
            log.warn("Could not create assignment for Avro orderId {}: {}", eventPayload.getOrderId(), e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error processing Avro shipment request for orderId {}: {}",
                      eventPayload.getOrderId(), e.getMessage(), e);
            throw e;
        }
    }
}
