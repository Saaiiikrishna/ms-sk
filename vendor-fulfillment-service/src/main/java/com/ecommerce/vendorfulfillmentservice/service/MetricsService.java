package com.ecommerce.vendorfulfillmentservice.service;

import com.ecommerce.vendorfulfillmentservice.entity.AssignmentStatus;
import com.ecommerce.vendorfulfillmentservice.repository.OutboxEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class MetricsService {

    private final MeterRegistry meterRegistry;
    private final OutboxEventRepository outboxEventRepository;

    // Counters for status changes
    private static final String STATUS_CHANGE_COUNTER_NAME = "vendor.assignment.status.changes";
    private static final String ACTION_COUNTER_NAME = "vendor.assignment.actions"; // For non-status specific actions
    // Gauge for outbox backlog
    private static final String OUTBOX_BACKLOG_GAUGE_NAME = "vendor.assignment.outbox.backlog.size";
    // Counter for notification requests
    private static final String NOTIFICATION_REQUEST_COUNTER_NAME = "vendor.assignment.notification.requests";


    private final AtomicLong outboxBacklog = new AtomicLong(0);


    public MetricsService(MeterRegistry meterRegistry, OutboxEventRepository outboxEventRepository) {
        this.meterRegistry = meterRegistry;
        this.outboxEventRepository = outboxEventRepository;

        // Initialize counters for all relevant statuses
        Arrays.stream(AssignmentStatus.values()).forEach(status ->
            Counter.builder(STATUS_CHANGE_COUNTER_NAME)
                .tag("status", status.name().toLowerCase())
                .description("Counts the number of times vendor assignments have changed to a specific status.")
                .register(meterRegistry)
        );

        // Initialize other event counters (e.g., reassign is not a status but an action)
        Counter.builder(ACTION_COUNTER_NAME)
            .tag("type", "reassigned")
            .description("Counts the number of times vendor assignments have been reassigned.")
            .register(meterRegistry);

        // Initialize counter for shipment notification requests
        Counter.builder(NOTIFICATION_REQUEST_COUNTER_NAME)
            .tag("type", "shipment_confirmation")
            .description("Counts the number of shipment confirmation notifications requested.")
            .register(meterRegistry);

        // Gauge for outbox backlog size
        Gauge.builder(OUTBOX_BACKLOG_GAUGE_NAME, outboxBacklog, AtomicLong::get)
            .description("Current number of unprocessed events in the outbox.")
            .register(meterRegistry);
    }

    public void incrementStatusChangeCounter(AssignmentStatus status) {
        meterRegistry.counter(STATUS_CHANGE_COUNTER_NAME, "status", status.name().toLowerCase()).increment();
    }

    public void incrementReassignmentCounter() {
        meterRegistry.counter(ACTION_COUNTER_NAME, "type", "reassigned").increment();
    }

    public void incrementShipmentNotificationRequestedCounter() {
        meterRegistry.counter(NOTIFICATION_REQUEST_COUNTER_NAME, "type", "shipment_confirmation").increment();
    }

    // This method would be called periodically by a scheduler or triggered by outbox poller.
    // For simplicity, the OutboxPollerService can call this.
    public void updateOutboxBacklogGauge() {
        try {
            // This could be a more efficient count query if performance is critical
            long count = outboxEventRepository.countByProcessedAtIsNull();
            outboxBacklog.set(count);
        } catch (Exception e) {
            // Log error, don't let metrics updates fail application logic
            System.err.println("Failed to update outbox backlog gauge: " + e.getMessage());
        }
    }
}
