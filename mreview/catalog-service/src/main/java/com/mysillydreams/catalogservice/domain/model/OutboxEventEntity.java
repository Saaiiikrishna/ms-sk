package com.mysillydreams.catalogservice.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events", indexes = {
    @Index(name = "idx_outbox_processed_created", columnList = "processed, created_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "aggregate_type", nullable = false, length = 100)
    private String aggregateType; // e.g., "Category", "CatalogItem", "StockLevel", "Cart"

    @Column(name = "aggregate_id", nullable = false, length = 36) // Assuming UUID string for aggregate IDs
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType; // e.g., "category.created", "item.price.updated"

    @Column(name = "kafka_topic", nullable = false, length = 100)
    private String kafkaTopic; // Target Kafka topic

    @Lob // Use Lob for potentially large JSON payloads
    @Column(name = "payload", nullable = false, columnDefinition = "TEXT") // Or JSONB if DB supports and preferred
    private String payload; // JSON string of the event

    @Column(nullable = false)
    @Builder.Default
    private boolean processed = false;

    @Column(name = "processing_attempts", nullable = false)
    @Builder.Default
    private int processingAttempts = 0;

    @Column(name = "last_attempt_time")
    private Instant lastAttemptTime;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
