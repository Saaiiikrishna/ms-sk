package com.mysillydreams.catalogservice.domain.model;

import com.mysillydreams.catalogservice.dto.StockAdjustmentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stock_transactions", indexes = {
    @Index(name = "idx_stocktransaction_item_timestamp", columnList = "item_id, transaction_time DESC")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockTransactionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private CatalogItemEntity catalogItem;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false)
    private StockAdjustmentType transactionType; // RECEIVE, ISSUE, ADJUSTMENT, (Could also include RESERVED, RELEASED from cart/order ops)

    @Column(nullable = false)
    private Integer quantityChanged; // Positive for increase, negative for decrease

    @Column(name = "quantity_before_transaction", nullable = false)
    private Integer quantityBeforeTransaction;

    @Column(name = "quantity_after_transaction", nullable = false)
    private Integer quantityAfterTransaction;

    @Column(length = 255)
    private String reason;

    @Column(name = "reference_id", length = 100) // E.g., PO number, Order ID, User ID for adjustment
    private String referenceId;

    @CreationTimestamp
    @Column(name = "transaction_time", nullable = false, updatable = false)
    private Instant transactionTime;

    // Could also add a field for who performed the transaction if user context is available
    // private String userId;
}
