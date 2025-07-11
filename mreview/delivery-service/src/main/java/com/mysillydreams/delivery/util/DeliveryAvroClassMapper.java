package com.mysillydreams.delivery.util;

// Import Avro generated classes that Delivery Service might PUBLISH
import com.mysillydreams.delivery.dto.avro.DeliveryAssignmentCreatedEvent;
import com.mysillydreams.delivery.dto.avro.DeliveryPickedUpEvent;
import com.mysillydreams.delivery.dto.avro.DeliveryDeliveredEvent;
// Note: GpsUpdateEvent is published directly by AssignmentService, not typically via outbox,
// but if it were, it would be listed here.

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component // Make it a Spring managed bean
public class DeliveryAvroClassMapper {
    private static final Logger log = LoggerFactory.getLogger(DeliveryAvroClassMapper.class);
    // Making the map non-static if this is a bean, or keep static if preferred for shared map.
    // If it's a bean, instance map is fine.
    private final Map<String, Class<?>> eventTypeToAvroClassMapInstance = new HashMap<>();

    // Static initializer block to populate the map.
    // This should map the 'eventType' strings (which are typically Kafka topic names)
    // that are stored in the OutboxEvent entity to their corresponding Avro SpecificRecord classes.
    // If map is instance member, use constructor or @PostConstruct to populate
    public DeliveryAvroClassMapper() {
        eventTypeToAvroClassMapInstance.put("delivery.assignment.created", DeliveryAssignmentCreatedEvent.class);
        eventTypeToAvroClassMapInstance.put("delivery.picked_up", DeliveryPickedUpEvent.class);
        eventTypeToAvroClassMapInstance.put("delivery.delivered", DeliveryDeliveredEvent.class);
        log.info("DeliveryAvroClassMapper (instance) initialized with {} mappings.", eventTypeToAvroClassMapInstance.size());
    }


    public Class<?> getClassForEventType(String eventType) {
        if (eventType == null || eventType.trim().isEmpty()) {
            log.warn("Attempted to get Avro class for null or empty eventType.");
            return null;
        }
        Class<?> clazz = eventTypeToAvroClassMapInstance.get(eventType.toLowerCase()); // Use instance map
        if (clazz == null) {
            log.warn("No Avro class mapping found for eventType: {}", eventType);
        }
        return clazz;
    }
}

    // Static initializer block to populate the map.
    // This should map the 'eventType' strings (which are typically Kafka topic names)
    // that are stored in the OutboxEvent entity to their corresponding Avro SpecificRecord classes.
    static {
        // These eventType strings must match what OutboxEventService uses when saving events.
        // For example, if OutboxEventService saves eventType as "delivery.assignment.created"
        eventTypeToAvroClassMap.put("delivery.assignment.created", DeliveryAssignmentCreatedEvent.class);
        eventTypeToAvroClassMap.put("delivery.picked_up", DeliveryPickedUpEvent.class);
        eventTypeToAvroClassMap.put("delivery.delivered", DeliveryDeliveredEvent.class);

        // Add mappings for any other Avro events published by Delivery Service via outbox.
        // Example: eventTypeToAvroClassMap.put("delivery.assignment.cancelled", DeliveryAssignmentCancelledEvent.class);

        log.info("DeliveryAvroClassMapper initialized with {} mappings.", eventTypeToAvroClassMap.size());
    }

    public Class<?> getClassForEventType(String eventType) {
        if (eventType == null || eventType.trim().isEmpty()) {
            log.warn("Attempted to get Avro class for null or empty eventType.");
            return null;
        }
        Class<?> clazz = eventTypeToAvroClassMap.get(eventType.toLowerCase());
        if (clazz == null) {
            log.warn("No Avro class mapping found for eventType: {}", eventType);
        }
        return clazz;
    }
}
