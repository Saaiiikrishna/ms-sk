package com.mysillydreams.userservice.domain.vendor;

import com.mysillydreams.userservice.domain.UserEntity;
// import com.mysillydreams.userservice.converter.CryptoConverter; // If legalName needs encryption

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
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
@Table(name = "vendor_profiles")
@Getter
@Setter
public class VendorProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY) // Lazy fetch is usually good for OneToOne unless always needed
    @JoinColumn(name = "user_id", nullable = false, unique = true) // Ensure user_id is unique for 1:1
    private UserEntity user;

    @NotBlank(message = "Legal name cannot be blank")
    @Size(max = 255)
    @Column(nullable = false)
    // TODO: SECURITY - Evaluate if legalName constitutes sensitive PII requiring field-level encryption.
    // If so, uncomment and apply CryptoConverter:
    // @Convert(converter = com.mysillydreams.userservice.converter.CryptoConverter.class)
    // @Column(nullable = false, length = 1024) // Encrypted fields might need more length
    private String legalName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private VendorStatus status = VendorStatus.REGISTERED;

    @Column(length = 100) // Assuming workflow ID length
    private String kycWorkflowId; // Correlates to Camunda/Temporal or other workflow engine ID

    @OneToMany(mappedBy = "vendorProfile", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<VendorDocument> documents = new ArrayList<>();

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    // Convenience constructor for DocumentService
    public VendorProfile() {} // JPA requires a no-arg constructor

    public VendorProfile(UUID id) { // Used in DocumentService scaffold
        this.id = id;
    }


    // Convenience methods for bidirectional relationship with VendorDocument
    public void addDocument(VendorDocument document) {
        documents.add(document);
        document.setVendorProfile(this);
    }

    public void removeDocument(VendorDocument document) {
        documents.remove(document);
        document.setVendorProfile(null);
    }
}
