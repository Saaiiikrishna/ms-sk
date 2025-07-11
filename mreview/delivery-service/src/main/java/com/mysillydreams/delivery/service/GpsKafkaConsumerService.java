package com.mysillydreams.delivery.service;

import com.mysillydreams.delivery.dto.avro.GpsUpdateEvent;
import com.mysillydreams.delivery.websocket.GpsWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GpsKafkaConsumerService {

    private static final Logger log = LoggerFactory.getLogger(GpsKafkaConsumerService.class);
    private final GpsWebSocketHandler gpsWebSocketHandler;

    @KafkaListener(
        topics = "${kafka.topics.deliveryGpsUpdates:delivery.gps.updates}",
        groupId = "${kafka.consumer.group-id}", // Same group or a dedicated one for GPS
        containerFactory = "kafkaListenerContainerFactoryAvro" // Use the Avro consumer factory
    )
    public void consumeGpsUpdate(@Payload GpsUpdateEvent event) {
        log.debug("Consumed GpsUpdateEvent from Kafka: assignmentId={}, lat={}, lon={}, ts={}",
                  event.getAssignmentId(), event.getLatitude(), event.getLongitude(), event.getTimestamp());
        try {
            gpsWebSocketHandler.sendGpsUpdateToSubscribers(event);
        } catch (Exception e) {
            log.error("Error forwarding GPS update (assignmentId: {}) to WebSocket subscribers: {}",
                      event.getAssignmentId(), e.getMessage(), e);
            // Depending on error handling strategy for WebSocket push, could re-throw or handle.
            // Since this is a non-critical, high-frequency stream, logging might be sufficient.
        }
    }
}
