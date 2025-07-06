package com.mysillydreams.ordercore.domain;

import com.mysillydreams.ordercore.domain.enums.OrderStatus;
import io.hypersistence.utils.hibernate.type.json.JsonType; // For JSONB mapping
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.Type;


import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import java.util.Map;

@Entity
@Table(name = "order_status_history") // Matches Flyway script
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(exclude = "order") // Exclude parent from hashCode/equals
public class OrderStatusHistory {

    @Id
    private UUID id; // Application-assigned UUID

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(name = "old_status", length = 30) // Matches VARCHAR(30)
    private OrderStatus oldStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 30) // Matches VARCHAR(30)
    private OrderStatus newStatus;

    @Column(name = "changed_by", length = 64) // Matches VARCHAR(64)
    private String changedBy;

    @Column(nullable = false) // Defaulted by DB (now()) but good to set in application too
    private Instant timestamp;

    @Type(JsonType.class) // Using hibernate-types for JSONB
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    // Constructors, getters, setters by Lombok
    // Convenience constructor
    public OrderStatusHistory(Order order, OrderStatus oldStatus, OrderStatus newStatus, String changedBy, Map<String, Object> metadata) {
        this.id = UUID.randomUUID(); // Generate ID on creation
        this.order = order;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
        this.changedBy = changedBy;
        this.metadata = metadata;
        this.timestamp = Instant.now(); // Set timestamp on creation
    }
}
