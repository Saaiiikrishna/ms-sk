package com.mysillydreams.ordercore.repository;

import com.mysillydreams.ordercore.domain.OutboxEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    // Find unprocessed events, ordered by creation time to ensure FIFO processing.
    // The Pageable parameter allows fetching in batches.
    List<OutboxEvent> findByProcessedFalseOrderByCreatedAtAsc(Pageable pageable);

    // Alternative to the guide's findByProcessedFalse(Pageable p) if default ordering is not by CreatedAt.
    // The guide's example implies default sort by ID or something else if not specified.
    // Explicitly ordering by CreatedAtAsc is generally better for outbox pollers.

    // Example of a bulk update if needed, though individual updates are safer with optimistic locking
    // if versioning was added to OutboxEvent.
    // @Modifying
    // @Query("UPDATE OutboxEvent oe SET oe.processed = true WHERE oe.id IN :ids")
    // int markAsProcessed(@Param("ids") List<UUID> ids);
}
