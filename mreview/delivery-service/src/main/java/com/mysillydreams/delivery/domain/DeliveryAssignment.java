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
import java.util.Map; // For address JSONB
import java.util.UUID;
import com.mysillydreams.delivery.domain.enums.DeliveryAssignmentStatus; // Import the enum

@Entity
@Table(name = "delivery_assignments")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryAssignment {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @ManyToOne(fetch = FetchType.LAZY) // Or EAGER if profile info often needed with assignment
    @JoinColumn(name = "courier_id") // Nullable initially, set when assigned
    private DeliveryProfile courier; // Changed from courier_id to object reference

    @Column(name = "vendor_id", nullable = false)
    private UUID vendorId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Type(JsonType.class)
    @Column(name = "pickup_address", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> pickupAddress;

    @Type(JsonType.class)
    @Column(name = "dropoff_address", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> dropoffAddress;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DeliveryAssignmentStatus status;

    @Column(name = "estimated_pickup_time")
    private Instant estimatedPickupTime;

    @Column(name = "actual_pickup_time")
    private Instant actualPickupTime;

    @Column(name = "estimated_delivery_time")
    private Instant estimatedDeliveryTime;

    @Column(name = "actual_delivery_time")
    private Instant actualDeliveryTime;

    @Column(name = "pickup_photo_url")
    private String pickupPhotoUrl;

    @Column(name = "delivery_photo_url")
    private String deliveryPhotoUrl;

    @Column(name = "pickup_otp_verified")
    private Boolean pickupOtpVerified = false;

    @Column(name = "delivery_otp_verified")
    private Boolean deliveryOtpVerified = false;

    @Lob // For potentially longer text
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    @PrePersist
    protected void onCreate() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        if (this.status == null) {
            this.status = DeliveryAssignmentStatus.PENDING_ASSIGNMENT;
        }
    }
}
