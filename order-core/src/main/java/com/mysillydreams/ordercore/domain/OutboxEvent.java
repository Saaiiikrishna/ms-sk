package com.mysillydreams.ordercore.domain;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
// Using com.fasterxml.jackson.databind.JsonNode for payload to be flexible
// but this requires careful handling with @Type(JsonType.class)
// For simplicity with JsonType, often a Map<String, Object> or a specific serializable DTO is easier.
// If JsonNode is used, ensure the Jackson ObjectMapper used by JsonType is configured correctly.
// Let's stick to Map<String, Object> for payload if using JsonType directly for simplicity,
// or a String if we want to store the JSON as text and deserialize manually.
// The guide used "payload JSONB", implying structured JSON. JsonType with Map is good.
import com.fasterxml.jackson.databind.JsonNode;


@Entity
@Table(name = "outbox_events") // Matches Flyway script
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEvent {

    @Id
    private UUID id; // Application-assigned UUID

    @Column(name = "aggregate_type", nullable = false, length = 64)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 64)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 128)
    private String eventType;

    @Type(JsonType.class) // Using hibernate-types for JSONB
    @Column(columnDefinition = "jsonb", nullable = false)
    private JsonNode payload; // Storing payload as JsonNode, assuming appropriate ObjectMapper config for JsonType

    @Column(nullable = false)
    private boolean processed = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // Optional: version for optimistic locking during processing by poller
    // @Version
    // private Long version;

    @PrePersist
    protected void onCreate() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        this.createdAt = Instant.now();
    }
}
