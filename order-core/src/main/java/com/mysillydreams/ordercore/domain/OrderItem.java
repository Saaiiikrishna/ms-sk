package com.mysillydreams.ordercore.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.EqualsAndHashCode;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "order_items") // Matches Flyway script
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(exclude = "order") // Exclude parent from hashCode/equals to prevent recursion
public class OrderItem {

    @Id
    private UUID id; // Application-assigned UUID

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "product_id", nullable = false) // As per V1 migration script adjustment
    private UUID productId;

    @Column(name = "product_sku", length = 64) // Matches VARCHAR(64)
    private String productSku;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2) // Matches NUMERIC(12,2)
    private BigDecimal unitPrice;

    @Column(nullable = false, precision = 12, scale = 2) // Matches NUMERIC(12,2)
    private BigDecimal discount; // Total discount for this line item or per unit discount? Assuming total for line.

    @Column(name = "total_price", nullable = false, precision = 12, scale = 2) // Matches NUMERIC(12,2)
    private BigDecimal totalPrice; // (unitPrice * quantity) - discount

    // Constructors, getters, setters are handled by Lombok @Data, @NoArgsConstructor, @AllArgsConstructor
    // If specific logic is needed for totalPrice calculation, it can be added here or in the service.
    // For example, a pre-persist/pre-update hook or a transient method.
    // @PrePersist
    // @PreUpdate
    // public void calculateTotalPrice() {
    //     if (unitPrice != null && quantity > 0) {
    //         BigDecimal quantityBd = BigDecimal.valueOf(quantity);
    //         BigDecimal subTotal = unitPrice.multiply(quantityBd);
    //         this.totalPrice = subTotal.subtract(this.discount != null ? this.discount : BigDecimal.ZERO);
    //     } else {
    //         this.totalPrice = BigDecimal.ZERO;
    //     }
    // }
}
