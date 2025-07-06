package com.mysillydreams.inventoryapi.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.Instant; // Changed from OffsetDateTime to Instant

@Entity
@Table(name = "stock_levels") // Table name matches migration script
@Data // Includes @Getter, @Setter, @ToString, @EqualsAndHashCode, @RequiredArgsConstructor
@NoArgsConstructor
@AllArgsConstructor
public class StockLevel {

    @Id
    @Column(name = "sku", length = 64) // Matches migration
    private String sku;

    @Column(name = "available", nullable = false) // Matches migration
    private int available;

    @Column(name = "reserved", nullable = false /*, columnDefinition = "INT DEFAULT 0" */) // Default handled by DB
    private int reserved = 0; // Initialize in Java as well for new objects

    @UpdateTimestamp // Automatically sets the timestamp on update
    @Column(name = "updated_at", nullable = false /*, columnDefinition = "TIMESTAMPTZ DEFAULT now()" */) // Default now() handled by DB
    private Instant updatedAt; // Changed to Instant

    // Constructor for creating new instances if needed, though @AllArgsConstructor covers it
    // public StockLevel(String sku, int available, int reserved, Instant updatedAt) {
    //     this.sku = sku;
    //     this.available = available;
    //     this.reserved = reserved;
    //     this.updatedAt = updatedAt; // Usually set by Hibernate or DB
    // }
}
