package com.mysillydreams.userservice.domain.inventory;

import com.mysillydreams.userservice.domain.UserEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "inventory_profiles")
@Getter
@Setter
public class InventoryProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true) // Each user can have at most one inventory profile
    private UserEntity user;

    // This profile might have specific attributes later, e.g., default warehouse, location, permissions within inventory system.
    // For now, it's primarily a link.

    @OneToMany(mappedBy = "owner", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<InventoryItem> items = new ArrayList<>();

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    // Convenience constructor for service layer if only ID is needed for reference
    public InventoryProfile() {} // JPA requirement

    public InventoryProfile(UUID id) {
        this.id = id;
    }


    // Convenience methods for managing items
    public void addItem(InventoryItem item) {
        items.add(item);
        item.setOwner(this);
    }

    public void removeItem(InventoryItem item) {
        items.remove(item);
        item.setOwner(null);
    }
}
