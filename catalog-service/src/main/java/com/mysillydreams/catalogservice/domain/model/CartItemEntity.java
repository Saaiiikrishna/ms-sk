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
@Table(name = "cart_items", uniqueConstraints = {
    @UniqueConstraint(name = "uk_cart_item", columnNames = {"cart_id", "item_id"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_id", nullable = false)
    private CartEntity cart;

    @ManyToOne(fetch = FetchType.LAZY, optional = false) // Eager fetch might be useful if items are always needed with cart item
    @JoinColumn(name = "item_id", nullable = false)
    private CatalogItemEntity catalogItem; // Reference to the actual product/service in the catalog

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2) // Price at the time of adding to cart
    private BigDecimal unitPrice;

    @Column(name = "discount_applied", precision = 12, scale = 2) // Discount amount applied to this item
    private BigDecimal discountApplied;

    // This could also store the specific bulk_pricing_rule_id that was applied, if needed for auditing.

    @CreationTimestamp
    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CartItemEntity that)) return false;
        // If using generated ID, this is fine.
        // If natural keys (cart_id, item_id) are preferred for equals/hashCode, adjust accordingly.
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        // If using generated ID.
        return getClass().hashCode();
    }
}
