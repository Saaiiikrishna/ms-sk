package com.mysillydreams.delivery.domain;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.Type;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Map; // For payload JSONB
import java.util.UUID;
import com.mysillydreams.delivery.domain.enums.DeliveryEventType; // Import the enum


@Entity
@Table(name = "delivery_events")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(exclude = "assignment") // Exclude parent from hashCode/equals
public class DeliveryEvent {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignment_id", nullable = false)
    private DeliveryAssignment assignment;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50) // Increased length for potentially longer enum names
    private DeliveryEventType eventType;

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> payload; // e.g., {"latitude": 12.34, "longitude": 56.78}, {"photoUrl": "...", "otp": "1234"}

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "actor_id") // Can be courier_id, system_user_id, etc.
    private UUID actorId;

    @Column(name = "actor_type", length = 30)
    private String actorType; // e.g., "COURIER", "SYSTEM", "ADMIN"

    @PrePersist
    protected void onCreate() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        if (this.occurredAt == null) {
            this.occurredAt = Instant.now();
        }
    }

    // Convenience constructor
    public DeliveryEvent(DeliveryAssignment assignment, DeliveryEventType eventType, Map<String, Object> payload, UUID actorId, String actorType) {
        this(); // Calls no-args constructor to set defaults via @PrePersist if id/occurredAt are null
        this.assignment = assignment;
        this.eventType = eventType;
        this.payload = payload;
        this.actorId = actorId;
        this.actorType = actorType;
    }
}
