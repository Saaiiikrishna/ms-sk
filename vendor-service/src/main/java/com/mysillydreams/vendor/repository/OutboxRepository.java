package com.mysillydreams.vendor.repository;

import com.mysillydreams.vendor.domain.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    // Find unprocessed events, ordered by creation time to ensure FIFO processing
    List<OutboxEvent> findByProcessedFalseOrderByCreatedAtAsc();

    // Optional: if you need to find events by aggregate
    List<OutboxEvent> findByAggregateIdAndAggregateTypeOrderByCreatedAtAsc(String aggregateId, String aggregateType);

    // Optional: if you need to find events by type
    List<OutboxEvent> findByEventTypeOrderByCreatedAtAsc(String eventType);

    // Optional: if you need to delete old, processed events
    void deleteByProcessedTrueAndCreatedAtBefore(Instant cutoffDate);
}
