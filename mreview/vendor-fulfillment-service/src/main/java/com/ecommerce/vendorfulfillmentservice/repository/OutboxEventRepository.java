package com.ecommerce.vendorfulfillmentservice.repository;

import com.ecommerce.vendorfulfillmentservice.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    // Find unprocessed events, ordered by creation date for FIFO processing
    @Query("SELECT oe FROM OutboxEvent oe WHERE oe.processedAt IS NULL ORDER BY oe.createdAt ASC")
    List<OutboxEvent> findUnprocessedEvents();

    // Optional: if you need to find events by aggregate
    List<OutboxEvent> findByAggregateIdAndProcessedAtIsNull(UUID aggregateId);

    long countByProcessedAtIsNull(); // For outbox backlog gauge
}
