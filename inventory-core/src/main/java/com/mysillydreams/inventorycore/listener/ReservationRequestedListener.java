package com.mysillydreams.inventorycore.listener;

import com.mysillydreams.inventorycore.dto.ReservationRequestedEvent;
import com.mysillydreams.inventorycore.service.ReservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReservationRequestedListener {

    private final ReservationService reservationService; // Corrected from 'svc' to match typical naming

    // The listener container factory name "reservationRequestedKafkaListenerContainerFactory"
    // should match the bean name in KafkaConfig.java
    @KafkaListener(
            topics = "${kafka.topics.reservationRequested}",
            containerFactory = "reservationRequestedKafkaListenerContainerFactory"
            // groupId is configured in ConsumerFactory, so not needed here explicitly
            // unless overriding.
    )
    public void onReservationRequested(@Payload ReservationRequestedEvent event,
                                       ConsumerRecord<String, ReservationRequestedEvent> record,
                                       Acknowledgment acknowledgment) {
        log.info("Received ReservationRequestedEvent: Order ID = {}, Key = {}, Partition = {}, Offset = {}",
                event.getOrderId(), record.key(), record.partition(), record.offset());
        try {
            reservationService.handleReservationRequest(event);
            acknowledgment.acknowledge(); // Acknowledge message after successful processing
            log.info("Successfully processed ReservationRequestedEvent for order ID: {}", event.getOrderId());
        } catch (Exception e) {
            // This catch block is for unexpected errors during service call or acknowledgment.
            // Business logic errors (like INSUFFICIENT_STOCK) are handled within ReservationServiceImpl
            // and result in a "failed" event being published, not an exception here.
            // If an exception IS thrown from handleReservationRequest (e.g., DB down, critical bug),
            // it will prevent acknowledgment if it happens before ack.acknowledge().
            // Spring Kafka's default ErrorHandler might retry.
            // Consider configuring a DeadLetterPublishingRecoverer for non-transient errors.
            log.error("Error processing ReservationRequestedEvent for order ID {}: {}", event.getOrderId(), e.getMessage(), e);
            // Not acknowledging, so message might be redelivered based on Kafka error handler config.
            // Or, if you have specific error handling (e.g., send to DLT immediately for certain errors):
            // acknowledgment.nack(0); // Example for non-redelivery, depending on broker config
            // For now, rely on default error handling (likely retry, then log/stop or DLT if configured).
        }
    }
}
