package com.mysillydreams.vendor.domain;

import com.fasterxml.jackson.databind.JsonNode;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;


import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEvent {

    @Id
    private UUID id;

    @Column(name = "aggregate_type", nullable = false)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId; // Usually string representation of ID, e.g. UUID.toString()

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb", nullable = false)
    @JdbcTypeCode(SqlTypes.JSON) // Recommended for Hibernate 6+
    private JsonNode payload;

    @Column(nullable = false)
    private Boolean processed = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
