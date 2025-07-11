package com.mysillydreams.catalogservice.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "price_overrides", indexes = {
    @Index(name = "idx_po_item_id_active_time", columnList = "item_id, enabled, start_time, end_time"),
    @Index(name = "idx_po_enabled", columnList = "enabled")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PriceOverrideEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private CatalogItemEntity catalogItem;

    @Column(name = "override_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal overridePrice;

    @Column(name = "start_time")
    private Instant startTime; // Nullable for immediate start

    @Column(name = "end_time")
    private Instant endTime;   // Nullable for no specific end time

    @Column(name = "created_by_user_id", length = 255)
    private String createdByUserId; // User ID (e.g., UUID string) or system identifier

    @Column(name = "created_by_role", length = 50)
    private String createdByRole;   // Role of the creator, e.g., "VENDOR_USER", "ADMIN_USER"

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @Version
    private Long version; // For optimistic locking

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PriceOverrideEntity that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
