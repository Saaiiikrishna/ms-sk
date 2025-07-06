package com.mysillydreams.ordercore.util;

// Import Avro generated classes that Order-Core might publish
import com.mysillydreams.ordercore.dto.avro.OrderCreatedEvent;
import com.mysillydreams.ordercore.dto.avro.OrderCancelledEvent;
import com.mysillydreams.ordercore.dto.avro.OrderStatusUpdatedEvent;
// Add other Avro event classes produced by Order-Core as needed

import java.util.HashMap;
import java.util.Map;

public class AvroClassMapper {

    private static final Map<String, Class<?>> eventTypeToAvroClassMap = new HashMap<>();

    static {
        // This mapping needs to be consistent with the 'eventType' string
        // used when events are saved by OutboxEventService.
        // Example: if OutboxEventService saves eventType as "order.created"
        eventTypeToAvroClassMap.put("order.created", OrderCreatedEvent.class);
        eventTypeToAvroClassMap.put("order.cancelled", OrderCancelledEvent.class);

        // For status updates, OrderServiceImpl uses "order.status." + newStatus.name().toLowerCase()
        // This means we might need to handle these dynamically or register each one.
        // For simplicity, let's assume OrderStatusUpdatedEvent is used for all status changes for now,
        // and the specific status is within its payload.
        // If different Avro types are used per status, this map needs more entries
        // or a more dynamic lookup.
        // Let's assume a generic eventType for all status updates that maps to OrderStatusUpdatedEvent.
        // For example, if OutboxEventService stores eventType as "order.status.updated" for all of them:
         eventTypeToAvroClassMap.put("order.status.updated", OrderStatusUpdatedEvent.class);
        // Or if eventType is like "order.status.paid", "order.status.confirmed":
        // This would require registering each status-specific event type if they map to OrderStatusUpdatedEvent.
        // This part needs careful alignment with how eventType strings are stored in OutboxEvent.
        // For the current OrderServiceImpl, it generates event types like "order.status.paid".
        // So, we should map these.
        // This is cumbersome; a single "order.status.updated" eventType might be better.
        // For now, let's assume OrderServiceImpl will be updated to use "order.status.updated"
        // as the eventType string for all status changes published via OrderStatusUpdatedEventDto/Avro.
    }

    public static Class<?> getClassForEventType(String eventType) {
        if (eventType == null) {
            return null;
        }
        // A simple direct mapping for now.
        // Could be more dynamic if eventType follows a pattern that implies the class.
        // For status updates like "order.status.paid", "order.status.confirmed", etc.,
        // if they all map to OrderStatusUpdatedEvent.class:
        if (eventType.startsWith("order.status.")) {
            return OrderStatusUpdatedEvent.class;
        }
        return eventTypeToAvroClassMap.get(eventType.toLowerCase());
    }

    // Call this method in OutboxPoller
    // Example:
    // if (eventType.startsWith("order.status.")) {
    //     targetClass = OrderStatusUpdatedEvent.class;
    // } else {
    //     targetClass = eventTypeToAvroClassMap.get(eventType);
    // }
}
