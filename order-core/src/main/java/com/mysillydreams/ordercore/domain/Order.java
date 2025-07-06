package com.mysillydreams.ordercore.domain;

import com.mysillydreams.ordercore.domain.enums.OrderStatus;
import com.mysillydreams.ordercore.domain.enums.OrderType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.*; // JPA standard annotations
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders") // Matches Flyway script
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    @Id
    // @GeneratedValue(generator = "UUID") // Not needed if UUIDs are assigned manually or via @Column(columnDefinition = "UUID default gen_random_uuid()") in DB
    // For UUIDs assigned by application, just @Id is often enough if not using DB generation.
    // If DB generates UUID, then @GeneratedValue(strategy = GenerationType.UUID) or specific generator.
    // Let's assume UUID is assigned by the application before save for now.
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20) // Matches VARCHAR(20) in Flyway
    private OrderType type;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2) // Matches NUMERIC(12,2)
    private BigDecimal totalAmount;

    @Column(nullable = false, length = 3) // Matches CHAR(3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_status", nullable = false, length = 30) // Matches VARCHAR(30)
    private OrderStatus currentStatus;

    @Version // For optimistic locking, matches BIGINT version column
    private Long version;

    @CreationTimestamp // Automatically set on creation by Hibernate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp // Automatically set on update by Hibernate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Relationships
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<OrderItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("timestamp DESC") // Show latest status changes first
    private List<OrderStatusHistory> statusHistory = new ArrayList<>();

    // Convenience methods for managing bidirectional relationships
    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }

    public void removeItem(OrderItem item) {
        items.remove(item);
        item.setOrder(null);
    }

    public void addStatusHistory(OrderStatusHistory historyEntry) {
        statusHistory.add(historyEntry);
        historyEntry.setOrder(this);
    }
}
