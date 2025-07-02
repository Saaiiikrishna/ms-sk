package com.mysillydreams.userservice.domain.inventory;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stock_transactions")
@Getter
@Setter
public class StockTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull(message = "Inventory item cannot be null for a stock transaction.")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private InventoryItem item;

    @NotNull(message = "Transaction type cannot be null.")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private TransactionType type;    // RECEIVE, ISSUE, ADJUSTMENT

    @NotNull(message = "Quantity for the transaction cannot be null.")
    @Column(nullable = false)
    private Integer quantity; // The amount of stock change; positive for RECEIVE, can be positive/negative for ADJUSTMENT, typically positive for ISSUE (representing quantity issued)

    // Note: The sign of 'quantity' should be handled by business logic based on 'type'.
    // E.g., for ISSUE, quantity is positive, but it reduces stock. For RECEIVE, quantity is positive and increases stock.
    // For ADJUSTMENT, quantity could be positive or negative to represent the change.
    // The InventoryManagementService logic shows quantity as always positive from request, and type determines +/-.

    // Optional: Store the quantity *after* this transaction for easier auditing or history.
    // private Integer quantityAfterTransaction;

    // Optional: Reference to source of transaction, e.g., Order ID for ISSUE, Purchase Order ID for RECEIVE.
    // @Column(length = 100)
    // private String referenceId; // E.g., OrderId, PurchaseOrderId, AdjustmentReasonCode

    // Optional: User who performed or authorized the transaction
    // @ManyToOne(fetch = FetchType.LAZY)
    // @JoinColumn(name = "user_id") // Link to UserEntity if tracking who made the adjustment
    // private UserEntity performedBy;

    @CreationTimestamp // This will automatically set the timestamp when the entity is persisted.
    @Column(nullable = false, updatable = false)
    private Instant timestamp;
}
