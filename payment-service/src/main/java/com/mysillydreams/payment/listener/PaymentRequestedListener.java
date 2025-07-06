package com.mysillydreams.payment.listener;

import com.mysillydreams.payment.dto.PaymentRequestedEvent;
import com.mysillydreams.payment.service.PaymentService;
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
public class PaymentRequestedListener {

    private final PaymentService paymentService;

    @KafkaListener(
            topics = "${kafka.topics.paymentRequested}",
            containerFactory = "paymentRequestedKafkaListenerContainerFactory"
            // GroupId is set in ConsumerFactory
    )
    public void onPaymentRequested(@Payload PaymentRequestedEvent event,
                                   ConsumerRecord<String, PaymentRequestedEvent> record,
                                   Acknowledgment acknowledgment) {
        log.info("Received PaymentRequestedEvent: Order ID = {}, Amount = {} {}, Key = {}, Partition = {}, Offset = {}",
                event.getOrderId(), event.getAmount(), event.getCurrency(),
                record.key(), record.partition(), record.offset());
        try {
            paymentService.processPaymentRequest(event);
            acknowledgment.acknowledge(); // Acknowledge after successful processing
            log.info("Successfully processed PaymentRequestedEvent for order ID: {}", event.getOrderId());
        } catch (Exception e) {
            // This catches unexpected errors from service layer or acknowledgment.
            // Business errors (like payment gateway failure) are handled within PaymentServiceImpl,
            // resulting in a PaymentFailedEvent, not an exception here that prevents acknowledgment.
            // If an exception IS thrown (e.g. DB down, critical bug), it implies a systemic issue.
            // Spring Kafka's ErrorHandler (default or custom) will handle this (e.g. retry, DLT).
            log.error("Critical error processing PaymentRequestedEvent for order ID {}: {}", event.getOrderId(), e.getMessage(), e);
            // Do not acknowledge, let error handler deal with it.
            // acknowledgment.nack(0L); // Or specific nack handling if needed.
        }
    }
}
