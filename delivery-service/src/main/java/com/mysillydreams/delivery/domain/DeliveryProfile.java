package com.mysillydreams.delivery.domain;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Map; // For vehicle_info JSONB
import java.util.UUID;

@Entity
@Table(name = "delivery_profiles")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryProfile {

    @Id
    private UUID id; // Application-assigned or DB-generated if configured

    private String name;
    private String phone;

    @Type(JsonType.class)
    @Column(name = "vehicle_info", columnDefinition = "jsonb")
    private Map<String, Object> vehicleInfo; // e.g., {"type": "BIKE", "plate": "XYZ123", "capacityKg": 10}

    @Column(nullable = false, length = 30)
    private String status; // e.g., ACTIVE, INACTIVE, ON_BREAK (Consider an Enum here later)

    @Column(name = "current_latitude")
    private Double currentLatitude;

    @Column(name = "current_longitude")
    private Double currentLongitude;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        if (this.status == null) {
            this.status = "ACTIVE"; // Default status
        }
    }
}
