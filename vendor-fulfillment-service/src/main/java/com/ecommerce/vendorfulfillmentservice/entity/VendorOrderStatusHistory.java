package com.ecommerce.vendorfulfillmentservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "vendor_order_status_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VendorOrderStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "UUID")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignment_id", nullable = false)
    private VendorOrderAssignment assignment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32) // Matches VendorOrderAssignment.status length
    private AssignmentStatus status;

    @CreationTimestamp // Using CreationTimestamp as 'occurred_at' should be set on creation
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private OffsetDateTime occurredAt;

    // Optional: Add details like changed_by_user_id, notes, etc. if needed for audit
}
