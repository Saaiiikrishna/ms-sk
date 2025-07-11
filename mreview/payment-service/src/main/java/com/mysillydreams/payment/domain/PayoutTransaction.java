package com.mysillydreams.payment.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payout_transactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PayoutTransaction {

    @Id
    // @GeneratedValue // Guide shows @GeneratedValue, but UUIDs are often assigned by app.
    // Let's assume application will assign UUID for consistency with OutboxEvent & PaymentTransaction.
    // If @GeneratedValue(strategy = GenerationType.UUID) is desired, ensure DB supports it or use appropriate generator.
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY) // Optional: if you want a direct JPA relationship
    @JoinColumn(name = "payment_id", nullable = false, updatable = false)
    private PaymentTransaction paymentTransaction; // Reference to the original payment

    @Column(name = "vendor_id", nullable = false, updatable = false)
    private UUID vendorId;

    @Column(name = "gross_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal grossAmount;

    @Column(name = "commission_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal commissionAmount;

    @Column(name = "net_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal netAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "razorpay_payout_id", length = 64)
    private String razorpayPayoutId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PayoutStatus status;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // @Version
    // private Long version; // If optimistic locking is needed

    // Constructor to simplify creation
    public PayoutTransaction(PaymentTransaction paymentTransaction, UUID vendorId, BigDecimal grossAmount,
                             BigDecimal commissionAmount, BigDecimal netAmount, String currency,
                             PayoutStatus status) {
        this.id = UUID.randomUUID(); // Application-assigned ID
        this.paymentTransaction = paymentTransaction;
        this.vendorId = vendorId;
        this.grossAmount = grossAmount;
        this.commissionAmount = commissionAmount;
        this.netAmount = netAmount;
        this.currency = currency;
        this.status = status;
    }
}
