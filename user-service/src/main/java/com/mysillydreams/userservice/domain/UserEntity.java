package com.mysillydreams.userservice.domain;

import com.mysillydreams.userservice.converter.CryptoConverter;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email; // For semantic validation if DTOs don't catch it first
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
// import java.time.LocalDate; // Will use String for dob to use existing CryptoConverter
import java.util.ArrayList;
import java.util.HashSet; // For roles set
import java.util.List;
import java.util.Set; // For roles set
import java.util.UUID;

@Entity
@Table(name = "users") // As per PRD Data Model
@Getter
@Setter
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotBlank
    @Column(nullable = false, unique = true, length = 50) // Length from PRD
    private String referenceId; // Business reference

    @Convert(converter = CryptoConverter.class)
    @Column(length = 1024) // Assuming encrypted content might be longer
    private String name; // Encrypted

    @Convert(converter = CryptoConverter.class)
    @Column(length = 1024, unique = true) // Email should be unique, even encrypted form
    // @Email // JPA validation, but DTO validation is primary
    private String email; // Encrypted

    @Convert(converter = CryptoConverter.class)
    @Column(length = 1024)
    private String phone; // Encrypted

    @Convert(converter = CryptoConverter.class)
    @Column(length = 1024) // Storing LocalDate as encrypted String (e.g., "YYYY-MM-DD")
    private String dob; // Encrypted (Date of Birth as String)

    @Column(length = 10) // From PRD
    private String gender;

    @Column(length = 255) // From PRD
    private String profilePicUrl;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<AddressEntity> addresses = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<PaymentInfoEntity> paymentInfos = new ArrayList<>(); // Added based on PRD schema

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<SessionEntity> sessions = new ArrayList<>();

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean active = true;

    @Column(nullable = true)
    private Instant archivedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role_name", nullable = false) // Made role_name non-nullable as discussed before
    private Set<String> roles = new HashSet<>();

    // Convenience methods for bidirectional relationships if needed
    public void addAddress(AddressEntity address) {
        addresses.add(address);
        address.setUser(this);
    }

    public void removeAddress(AddressEntity address) {
        addresses.remove(address);
        address.setUser(null);
    }

    public void addPaymentInfo(PaymentInfoEntity paymentInfo) {
        paymentInfos.add(paymentInfo);
        paymentInfo.setUser(this);
    }

    public void removePaymentInfo(PaymentInfoEntity paymentInfo) {
        paymentInfos.remove(paymentInfo);
        paymentInfo.setUser(null);
    }

    public void addSession(SessionEntity session) {
        sessions.add(session);
        session.setUser(this);
    }

    public void removeSession(SessionEntity session) {
        sessions.remove(session);
        session.setUser(null);
    }
}
