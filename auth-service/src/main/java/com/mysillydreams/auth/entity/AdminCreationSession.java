package com.mysillydreams.auth.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Entity for managing multi-step admin creation sessions.
 * Stores temporary data during the admin creation process.
 */
@Entity
@Table(name = "admin_creation_sessions")
@Getter
@Setter
@NoArgsConstructor
public class AdminCreationSession {

    @Id
    @Column(name = "session_id", nullable = false, updatable = false)
    private UUID sessionId;

    @NotNull
    @Column(name = "current_admin_id", nullable = false)
    private UUID currentAdminId;

    @Column(name = "new_admin_id")
    private UUID newAdminId;

    @NotNull
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "admin_details", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> adminDetails;

    @Column(name = "mfa_secret", length = 1024)
    private String mfaSecret;

    @NotNull
    @Column(name = "step", nullable = false)
    private Integer step = 1;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @NotNull
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    public AdminCreationSession(UUID sessionId, UUID currentAdminId, Map<String, Object> adminDetails) {
        this.sessionId = sessionId;
        this.currentAdminId = currentAdminId;
        this.adminDetails = adminDetails;
        this.step = 1;
        this.expiresAt = LocalDateTime.now().plusHours(1); // 1 hour expiry
    }

    /**
     * Check if the session has expired.
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    /**
     * Advance to the next step in the creation process.
     */
    public void nextStep() {
        if (step < 3) {
            step++;
        }
    }

    /**
     * Check if the session is in a valid step.
     */
    public boolean isValidStep(int expectedStep) {
        return this.step.equals(expectedStep);
    }
}
