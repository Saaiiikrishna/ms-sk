package com.mysillydreams.catalogservice.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stock_levels")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockLevelEntity {

    @Id
    private UUID itemId; // This will be the ID of the CatalogItemEntity

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId // Maps the itemId field to the ID of CatalogItemEntity
    @JoinColumn(name = "item_id")
    private CatalogItemEntity catalogItem;

    @Column(name = "quantity_on_hand", nullable = false)
    @Builder.Default
    private Integer quantityOnHand = 0;

    @Column(name = "reorder_level")
    private Integer reorderLevel; // Optional: for triggering reorder alerts

    @Version // For optimistic locking, useful for stock updates
    private Long version;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;


    public void setCatalogItem(CatalogItemEntity catalogItem) {
        this.catalogItem = catalogItem;
        if (catalogItem != null) {
            this.itemId = catalogItem.getId();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StockLevelEntity that)) return false;
        return itemId != null && itemId.equals(that.itemId);
    }

    @Override
    public int hashCode() {
        return itemId != null ? itemId.hashCode() : 0;
    }
}
