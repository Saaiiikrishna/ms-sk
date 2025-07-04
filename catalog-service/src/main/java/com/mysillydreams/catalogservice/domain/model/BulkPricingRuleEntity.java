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
@Table(name = "bulk_pricing_rules", indexes = {
    @Index(name = "idx_bulkpricing_item_qty_valid", columnList = "item_id, min_quantity, valid_from, valid_to")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BulkPricingRuleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private CatalogItemEntity catalogItem;

    @Column(name = "min_quantity", nullable = false)
    private Integer minQuantity;

    @Column(name = "discount_percentage", nullable = false, precision = 5, scale = 2) // e.g., 5.00 for 5%
    private BigDecimal discountPercentage;

    @Column(name = "valid_from")
    private Instant validFrom; // Nullable if always valid or starts immediately

    @Column(name = "valid_to")
    private Instant validTo;   // Nullable if no expiration

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Version
    private Long version; // For optimistic locking


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BulkPricingRuleEntity that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
