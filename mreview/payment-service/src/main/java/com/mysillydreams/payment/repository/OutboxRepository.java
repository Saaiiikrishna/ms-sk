package com.mysillydreams.payment.repository; // Changed package

import com.mysillydreams.payment.domain.OutboxEvent; // Changed import
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Finds a list of OutboxEvent entities that have not yet been processed,
     * ordered by their creation timestamp in ascending order.
     * This ensures that events are typically processed in the order they were created.
     *
     * @param pageable  The pagination information (e.g., page number, size).
     * @return A list of unprocessed OutboxEvent entities.
     */
    List<OutboxEvent> findByProcessedFalseOrderByCreatedAtAsc(Pageable pageable);

    /**
     * Counts the number of OutboxEvent entities that have not yet been processed.
     * Used for monitoring the outbox backlog size.
     *
     * @return The count of unprocessed outbox events.
     */
    long countByProcessedFalse();
}
