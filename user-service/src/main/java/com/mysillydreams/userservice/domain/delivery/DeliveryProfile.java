package com.mysillydreams.userservice.domain.delivery;

import com.mysillydreams.userservice.domain.UserEntity;
// import com.mysillydreams.userservice.converter.CryptoConverter; // If vehicleDetails needs encryption
import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "delivery_profiles")
@Getter
@Setter
public class DeliveryProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private UserEntity user;

    @Size(max = 255)
    @Column(length = 255)
    // TODO: SECURITY - Evaluate if vehicleDetails (e.g., license plate) constitutes sensitive PII
    // requiring field-level encryption with com.mysillydreams.userservice.converter.CryptoConverter.
    // If so, uncomment: @Convert(converter = com.mysillydreams.userservice.converter.CryptoConverter.class) @Column(length = 1024)
    private String vehicleDetails; // E.g., "Bike - Honda Activa KA01XY1234", "Car - Maruti Swift DL02AB5678"

    @Column(nullable = false)
    private boolean active = true; // Delivery user can be active or inactive

    @OneToMany(mappedBy = "deliveryProfile", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<OrderAssignment> assignments = new ArrayList<>();

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    // Convenience methods for managing assignments
    public void addOrderAssignment(OrderAssignment assignment) {
        assignments.add(assignment);
        assignment.setDeliveryProfile(this);
    }

    public void removeOrderAssignment(OrderAssignment assignment) {
        assignments.remove(assignment);
        assignment.setDeliveryProfile(null);
    }
}
