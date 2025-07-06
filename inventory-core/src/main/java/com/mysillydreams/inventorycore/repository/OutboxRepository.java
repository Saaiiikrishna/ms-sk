package com.mysillydreams.inventorycore.repository;

import com.mysillydreams.inventorycore.domain.OutboxEvent;
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

    // The guide's OutboxPoller uses repo.findByProcessedFalse(PageRequest.of(0,50)).
    // If that exact method signature is required without explicit ordering in the name,
    // and if the default sort by the underlying database or JPA provider is not reliable,
    // an @Query annotation might be needed or rely on the poller to sort if necessary.
    // However, findByProcessedFalseOrderByCreatedAtAsc is more robust for this pattern.
    // The poller can call this method.

    // If strictly adhering to the poller's example repo.findByProcessedFalse(PageRequest.of(0,50)):
    // List<OutboxEvent> findByProcessedFalse(Pageable pageable);
    // And ensure data is inserted in a way that default retrieval order is acceptable, or handle in poller.
    // For robustness, OrderByCreatedAtAsc is preferred.
}
