package com.ecommerce.vendorfulfillmentservice.kafka;

import com.ecommerce.vendorfulfillmentservice.event.OrderReservationSucceededEvent;
import com.ecommerce.vendorfulfillmentservice.service.VendorAssignmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventConsumer {

    private final VendorAssignmentService vendorAssignmentService;

    @Value("${app.kafka.topic.order-reservation-succeeded}")
    private String orderReservationSucceededTopic;

    @KafkaListener(
            topics = "${app.kafka.topic.order-reservation-succeeded}",
            containerFactory = "orderReservationKafkaListenerContainerFactory", // Referencing the factory bean
            groupId = "${spring.kafka.consumer.group-id}.order-reservation" // Matches the group ID in KafkaConfig for this consumer
    )
    public void listenOrderReservationSucceeded(@Payload Object payload, // Changed to Object
                                                ConsumerRecord<String, Object> record, // Changed to Object
                                                Acknowledgment acknowledgment) {

        // This check and casting is needed because the factory is now <String, Object>
        if (payload instanceof com.ecommerce.vendorfulfillmentservice.event.OrderReservationSucceededEvent) {
            com.ecommerce.vendorfulfillmentservice.event.OrderReservationSucceededEvent event =
                    (com.ecommerce.vendorfulfillmentservice.event.OrderReservationSucceededEvent) payload;

            log.info("Received OrderReservationSucceededEvent (JSON DTO): message_key={}, event_id={}, order_id={}, topic={}, partition={}, offset={}",
                    record.key(), event.getEventId(), event.getOrderId(), record.topic(), record.partition(), record.offset());
            try {
                if (event.getEventId() == null || event.getOrderId() == null) {
                    log.error("Received event with missing eventId or orderId. Skipping. Event: {}", event);
                    acknowledgment.acknowledge();
                    return;
                }
                vendorAssignmentService.processOrderReservation(event); // Assumes processOrderReservation takes the DTO
                acknowledgment.acknowledge();
                log.info("Successfully processed and acknowledged JSON DTO event_id={}", event.getEventId());
            } catch (Exception e) {
                log.error("Error processing JSON DTO event_id={} from topic={}: {}. Message will be retried.",
                        event.getEventId(), record.topic(), e.getMessage(), e);
            }
        }
        // UNCOMMENT THIS BLOCK WHEN USING AVRO (Option 2 in KafkaConfig)
        /*
        else if (payload instanceof com.ecommerce.ordercore.event.avro.OrderReservationSucceededEventAvro) {
            com.ecommerce.ordercore.event.avro.OrderReservationSucceededEventAvro avroEvent =
                    (com.ecommerce.ordercore.event.avro.OrderReservationSucceededEventAvro) payload;

            log.info("Received OrderReservationSucceededEvent (AVRO): message_key={}, event_id={}, order_id={}, topic={}, partition={}, offset={}",
                    record.key(), avroEvent.getEventId(), avroEvent.getOrderId(), record.topic(), record.partition(), record.offset());
            try {
                // TODO: Map AvroEvent to internal DTO or change service method signature
                // For now, assuming a mapper or direct use:
                // Example: Convert to internal DTO if service expects it
                com.ecommerce.vendorfulfillmentservice.event.OrderReservationSucceededEvent internalEventDto =
                    mapAvroToDto(avroEvent); // Placeholder for mapping logic

                if (internalEventDto.getEventId() == null || internalEventDto.getOrderId() == null) {
                    log.error("Received Avro event with missing critical fields after mapping. Skipping. Avro Event: {}", avroEvent);
                    acknowledgment.acknowledge();
                    return;
                }
                vendorAssignmentService.processOrderReservation(internalEventDto);
                acknowledgment.acknowledge();
                log.info("Successfully processed and acknowledged AVRO event_id={}", internalEventDto.getEventId());

            } catch (Exception e) {
                log.error("Error processing AVRO event_id={} from topic={}: {}. Message will be retried.",
                        avroEvent.getEventId(), record.topic(), e.getMessage(), e);
            }
        }
        */
        else {
            log.error("Received unknown payload type: {}. Skipping message from topic={}, partition={}, offset={}",
                    payload.getClass().getName(), record.topic(), record.partition(), record.offset());
            acknowledgment.acknowledge(); // Acknowledge to prevent retries for unprocessable types
        }
    }

    // Placeholder for Avro to DTO mapping logic - needed if Option 2 (AVRO) is used
    /*
    private com.ecommerce.vendorfulfillmentservice.event.OrderReservationSucceededEvent mapAvroToDto(
            com.ecommerce.ordercore.event.avro.OrderReservationSucceededEventAvro avroEvent) {
        // Implement mapping logic here
        // For example:
        return new com.ecommerce.vendorfulfillmentservice.event.OrderReservationSucceededEvent(
            avroEvent.getEventId(),
            java.util.UUID.fromString(avroEvent.getOrderId().toString()), // Avro might use CharSequence
            java.util.UUID.fromString(avroEvent.getCustomerId().toString()),
            java.time.OffsetDateTime.ofInstant(java.time.Instant.ofEpochMilli(avroEvent.getReservationTimestamp()), java.time.ZoneOffset.UTC)
        );
    }
    */
}
        try {
            if (event.getEventId() == null || event.getOrderId() == null) {
                log.error("Received event with missing eventId or orderId. Skipping. Event: {}", event);
                // Acknowledge to prevent retries for malformed critical data, could send to DLQ
                acknowledgment.acknowledge();
                return;
            }
            vendorAssignmentService.processOrderReservation(event);
            acknowledgment.acknowledge(); // Acknowledge message after successful processing
            log.info("Successfully processed and acknowledged event_id={}", event.getEventId());
        } catch (Exception e) {
            // PRD: Idempotent consumption with manual offset commit and outbox dedupe.
            // If processOrderReservation fails (e.g., DB down, unexpected error),
            // not acknowledging will cause Kafka to redeliver the message.
            // The transactional nature of processOrderReservation (including idempotency check)
            // should handle retries gracefully.
            // For non-transient errors or after too many retries, a Dead Letter Queue (DLQ) strategy would be needed.
            log.error("Error processing event_id={} from topic={}: {}. Message will be retried.",
                    event.getEventId(), record.topic(), e.getMessage(), e);
            // Not acknowledging, so Kafka will redeliver.
            // Consider adding a specific error handler in Kafka config for more sophisticated retry/DLQ.
        }
    }
}
