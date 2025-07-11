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
@Table(name = "payment_transactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentTransaction {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId; // Changed to UUID to match schema suggestion

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3) // CHAR(3) in SQL
    private String currency;

    @Column(nullable = false, length = 32)
    private String status; // e.g., PENDING, AUTHORIZED, CAPTURED, FAILED, REFUND_INITIATED, REFUNDED

    @Column(name = "razorpay_order_id", length = 64)
    private String razorpayOrderId;

    @Column(name = "razorpay_payment_id", length = 64)
    private String razorpayPaymentId;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @CreationTimestamp // Automatically set on creation
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp // Automatically set on update
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version = 0L; // Initialize version for new entities

    // Constructor for initial creation based on PaymentRequestedEvent
    // This is an example; actual creation logic might be more nuanced in the service.
    public PaymentTransaction(UUID orderId, BigDecimal amount, String currency, String status,
                              String razorpayOrderId, String razorpayPaymentId, String errorMessage) {
        this.id = UUID.randomUUID(); // Generate ID upon creation
        this.orderId = orderId;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.razorpayOrderId = razorpayOrderId;
        this.razorpayPaymentId = razorpayPaymentId;
        this.errorMessage = errorMessage;
        // createdAt, updatedAt, version are handled by JPA/Hibernate
    }

    // Simplified constructor as used in PaymentServiceImpl example
    public PaymentTransaction(String orderIdString, double amountDouble, String currency,
                              String status, String razorpayOrderId, String razorpayPaymentId) {
        this(UUID.fromString(orderIdString), BigDecimal.valueOf(amountDouble), currency, status,
             razorpayOrderId, razorpayPaymentId, null);
    }
}
