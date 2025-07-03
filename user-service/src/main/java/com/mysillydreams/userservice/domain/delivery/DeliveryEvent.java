package com.mysillydreams.userservice.domain.delivery;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "delivery_events", indexes = {
    @Index(name = "idx_deliveryevent_eventtype", columnList = "eventType")
})
@Getter
@Setter
public class DeliveryEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignment_id", nullable = false)
    private OrderAssignment assignment;

    @NotBlank(message = "Event type cannot be blank.")
    @Column(nullable = false, length = 100)
    private String eventType; // E.g., "ARRIVED", "PHOTO_TAKEN", "OTP_VERIFIED", "CALL_MADE"
                              // Could also be an Enum if the set of event types is fixed and known.

    @Column(columnDefinition = "TEXT") // For JSON payload, TEXT is suitable
    // TODO: SECURITY - If 'payload' can contain sensitive PII, it should either be structured to avoid PII,
    // or the entire JSON string should be encrypted using CryptoConverter (if appropriate),
    // or individual sensitive fields within the JSON should be encrypted before serialization.
    private String payload; // JSON string containing event-specific data (e.g., GPS coordinates, photo S3 key, OTP attempt details)

    @CreationTimestamp // This will automatically set the timestamp when the entity is persisted.
    @Column(nullable = false, updatable = false)
    private Instant timestamp;

    // Optional: Actor who triggered the event, if different from the assigned delivery user
    // or if system events occur.
    // private String actorId;
    // private String actorType; // e.g., "DELIVERY_USER", "SYSTEM", "ADMIN"
}
