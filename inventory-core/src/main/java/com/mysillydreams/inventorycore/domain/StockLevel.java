package com.mysillydreams.inventorycore.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name="stock_levels")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockLevel {

    @Id
    private String sku;

    private int available;
    private int reserved;

    @Version
    private Long version;

    @UpdateTimestamp // Automatically sets the timestamp when the entity is updated
    private Instant updatedAt;
}
