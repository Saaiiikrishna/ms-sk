package com.mysillydreams.userservice.domain;

import com.mysillydreams.userservice.converter.CryptoConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "payment_info") // As per PRD Data Model
@Getter
@Setter
public class PaymentInfoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Convert(converter = CryptoConverter.class)
    @Column(nullable = false, length = 1024) // Card token from payment gateway, encrypted
    private String cardToken;

    @Column(length = 50) // E.g., VISA, MasterCard
    private String cardType;

    private Integer expiryMonth; // E.g., 12 for December

    private Integer expiryYear; // E.g., 2025

    // Timestamps can be added if useful
    // @CreationTimestamp
    // private Instant createdAt;
    // @UpdateTimestamp
    // private Instant updatedAt;
}
