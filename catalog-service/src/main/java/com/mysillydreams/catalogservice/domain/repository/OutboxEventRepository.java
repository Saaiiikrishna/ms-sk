package com.mysillydreams.catalogservice.domain.repository;

import com.mysillydreams.catalogservice.domain.model.OutboxEventEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;


import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, UUID> {

    // Find unprocessed events, ordered by creation time, with a limit (for batch processing)
    List<OutboxEventEntity> findByProcessedFalseOrderByCreatedAtAsc(Pageable pageable);

    // Find unprocessed events that haven't been attempted recently or have few attempts
    // (for more sophisticated retry logic in poller)
    @Query("SELECT o FROM OutboxEventEntity o WHERE o.processed = false AND (o.lastAttemptTime IS NULL OR o.lastAttemptTime < :retryThresholdTime) AND o.processingAttempts < :maxAttempts ORDER BY o.createdAt ASC")
    List<OutboxEventEntity> findUnprocessedEventsForRetry(
            @Param("retryThresholdTime") Instant retryThresholdTime,
            @Param("maxAttempts") int maxAttempts,
            Pageable pageable
    );

    // Could add methods for cleanup of old, processed events if needed:
    @Modifying
    @Transactional // Ensure this runs in its own transaction or is called from a transactional context
    @Query("DELETE FROM OutboxEventEntity o WHERE o.processed = true AND o.createdAt < :cutoffTime")
    int deleteProcessedEventsOlderThan(@Param("cutoffTime") Instant cutoffTime);
}
