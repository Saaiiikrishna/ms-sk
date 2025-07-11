package com.mysillydreams.auth.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity representing MFA configuration for admin users.
 * Stores MFA settings and backup codes for admin authentication.
 */
@Entity
@Table(name = "admin_mfa_config")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminMfaConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "admin_profile_id", nullable = false, unique = true)
    private UUID adminProfileId;

    @Column(name = "secret_key", nullable = false)
    private String secretKey;

    @Column(name = "backup_codes", columnDefinition = "TEXT")
    private String backupCodes; // JSON array of backup codes

    @Column(name = "is_enabled", nullable = false)
    private Boolean isEnabled = false;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Constructor for creating new MFA config.
     */
    public AdminMfaConfig(UUID adminProfileId, String secretKey, String backupCodes) {
        this.adminProfileId = adminProfileId;
        this.secretKey = secretKey;
        this.backupCodes = backupCodes;
        this.isEnabled = true;
    }
}
