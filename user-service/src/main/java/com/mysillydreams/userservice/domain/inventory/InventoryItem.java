package com.mysillydreams.userservice.domain.inventory;

// import com.mysillydreams.userservice.converter.CryptoConverter; // If name/description need encryption
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "inventory_items", indexes = {
    @Index(name = "idx_inventoryitem_sku", columnList = "sku", unique = true)
})
@Getter
@Setter
public class InventoryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventory_profile_id", nullable = false)
    private InventoryProfile owner; // The inventory profile this item belongs to

    @NotBlank(message = "SKU cannot be blank.")
    @Size(max = 100, message = "SKU must be less than 100 characters.")
    @Column(nullable = false, unique = true, length = 100)
    private String sku; // Stock Keeping Unit - should be unique across all items or per owner context

    @NotBlank(message = "Item name cannot be blank.")
    @Size(max = 255, message = "Item name must be less than 255 characters.")
    @Column(nullable = false)
    // TODO: SECURITY - Evaluate if item 'name' can contain sensitive PII requiring field-level encryption.
    // If so, uncomment and apply CryptoConverter:
    // @Convert(converter = com.mysillydreams.userservice.converter.CryptoConverter.class) @Column(nullable = false, length = 1024)
    private String name;

    @Column(columnDefinition = "TEXT") // For longer descriptions
    // TODO: SECURITY - Evaluate if item 'description' can contain sensitive PII requiring field-level encryption.
    // If so, uncomment and apply CryptoConverter:
    // @Convert(converter = com.mysillydreams.userservice.converter.CryptoConverter.class) @Column(columnDefinition = "TEXT")
    private String description;

    @NotNull(message = "Quantity on hand cannot be null.")
    @Min(value = 0, message = "Quantity on hand cannot be negative.")
    @Column(nullable = false)
    private Integer quantityOnHand = 0;

    @NotNull(message = "Reorder level cannot be null.")
    @Min(value = 0, message = "Reorder level cannot be negative.")
    @Column(nullable = false)
    private Integer reorderLevel = 0;

    // Other potential fields: unitPrice, costPrice, supplierInfo, dimensions, weight, category, etc.

    @OneToMany(mappedBy = "item", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<StockTransaction> transactions = new ArrayList<>();

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    // Convenience methods for transactions
    public void addTransaction(StockTransaction transaction) {
        transactions.add(transaction);
        transaction.setItem(this);
    }

    public void removeTransaction(StockTransaction transaction) {
        transactions.remove(transaction);
        transaction.setItem(null);
    }
}
