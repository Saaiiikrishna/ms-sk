package com.mysillydreams.auth.domain;

import com.mysillydreams.auth.converter.TotpSecretConverter;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID; // Assuming Keycloak User IDs are UUIDs

@Entity
@Table(name = "admin_mfa_config")
@Getter
@Setter
@NoArgsConstructor
public class AdminMfaConfig {

    @Id
    @Column(name = "user_id", nullable = false, updatable = false) // Using Keycloak's user ID as PK
    private UUID userId; // This should match the type of user IDs from Keycloak (typically UUID)

    @NotNull
    @Column(nullable = false)
    private boolean mfaEnabled = false;

    @NotNull // Encrypted secret should not be null if MFA is being set up
    @Column(nullable = false, length = 1024) // Store encrypted secret, length might vary
    @Convert(converter = TotpSecretConverter.class)
    private String encryptedTotpSecret;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    public AdminMfaConfig(UUID userId, String encryptedTotpSecret) {
        this.userId = userId;
        this.encryptedTotpSecret = encryptedTotpSecret;
        this.mfaEnabled = false; // Default to false on initial setup
    }
}
