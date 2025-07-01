package com.mysillydreams.userservice.domain.vendor;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "vendor_documents")
@Getter
@Setter
public class VendorDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_profile_id", nullable = false)
    private VendorProfile vendorProfile;

    @NotBlank(message = "Document type cannot be blank")
    @Size(max = 100) // E.g., "PAN_CARD", "GST_CERTIFICATE", "AADHAAR_SELFIE"
    @Column(nullable = false, length = 100)
    private String docType;

    @NotBlank(message = "S3 key cannot be blank")
    @Column(nullable = false, length = 1024) // S3 keys can be long
    private String s3Key;       // Object key in S3 bucket

    @Column(length = 64) // SHA-256 checksum is 64 hex characters
    private String checksum;    // SHA-256 checksum of the uploaded file

    @Column(nullable = false)
    private boolean processed = false; // Flag indicating if KYC orchestration has processed this document

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant uploadedAt; // Timestamp of when the metadata record was created (usually matches presigned URL generation time)

    // Consider adding an `updatedAt` timestamp if the record (e.g., processed status, checksum) can be updated.
    // @UpdateTimestamp
    // private Instant updatedAt;
}
