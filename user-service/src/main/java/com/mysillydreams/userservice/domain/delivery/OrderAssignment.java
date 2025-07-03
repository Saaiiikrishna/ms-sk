package com.mysillydreams.userservice.domain.delivery;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp; // For assignedAt
import org.hibernate.annotations.UpdateTimestamp;   // If status changes update a general timestamp

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "order_assignments", indexes = {
    @Index(name = "idx_orderassignment_status", columnList = "status"),
    @Index(name = "idx_orderassignment_orderid", columnList = "orderId", unique = true) // Assuming an order is assigned only once
})
@Getter
@Setter
public class OrderAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id; // Internal ID for the assignment

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_profile_id", nullable = false)
    private DeliveryProfile deliveryProfile;

    @NotNull
    @Column(nullable = false) // Assuming Order IDs are UUIDs from Order Service
    private UUID orderId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private AssignmentType type; // PICKUP or DELIVERY

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private AssignmentStatus status = AssignmentStatus.ASSIGNED;

    // Timestamps for various status changes could be individual fields or logged in DeliveryEvent
    // private Instant enRouteAt;
    // private Instant arrivedAtPickupAt;
    // ... etc.

    @CreationTimestamp // Represents when the assignment record was created (effectively assignedAt)
    @Column(nullable = false, updatable = false)
    private Instant assignedAt;

    @UpdateTimestamp // General timestamp for last update on this assignment record
    private Instant lastUpdatedAt;


    @OneToMany(mappedBy = "assignment", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("timestamp ASC") // Order events by their occurrence time
    private List<DeliveryEvent> events = new ArrayList<>();

    // Convenience methods for managing events
    public void addDeliveryEvent(DeliveryEvent event) {
        events.add(event);
        event.setAssignment(this);
    }

    public void removeDeliveryEvent(DeliveryEvent event) {
        events.remove(event);
        event.setAssignment(null);
    }
}
