package com.mysillydreams.userservice.domain;

import com.mysillydreams.userservice.converter.CryptoConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "addresses") // As per PRD Data Model
@Getter
@Setter
public class AddressEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Convert(converter = CryptoConverter.class)
    @Column(length = 1024) // Assuming encrypted content might be longer
    private String addressLine1; // Encrypted

    @Convert(converter = CryptoConverter.class)
    @Column(length = 1024)
    private String addressLine2; // Encrypted

    @Convert(converter = CryptoConverter.class)
    @Column(length = 1024)
    private String city; // Encrypted

    @Column(length = 50) // From PRD
    private String state; // Not specified as encrypted in PRD for Address, but consider if PII

    @Column(length = 50) // From PRD
    private String country; // Not specified as encrypted

    @Column(length = 20) // From PRD
    private String postalCode; // Not specified as encrypted, but often sensitive.
                               // For now, following PRD strictly.
                               // If this needs encryption, apply @Convert.

    // Timestamps can be added if needed (createdAt, updatedAt)
    // @CreationTimestamp
    // private Instant createdAt;
    // @UpdateTimestamp
    // private Instant updatedAt;
}
