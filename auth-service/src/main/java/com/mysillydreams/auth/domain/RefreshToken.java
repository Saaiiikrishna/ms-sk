package com.mysillydreams.auth.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity for storing refresh tokens securely in database.
 * Provides better security than storing refresh tokens as JWTs.
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 255)
    private String token;

    @Column(nullable = false, length = 100)
    private String username;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private LocalDateTime issuedAt;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private boolean revoked = false;

    @Column(length = 45)
    private String ipAddress;

    @Column(length = 500)
    private String userAgent;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public RefreshToken(String token, String username, UUID userId, LocalDateTime expiresAt) {
        this.token = token;
        this.username = username;
        this.userId = userId;
        this.issuedAt = LocalDateTime.now();
        this.expiresAt = expiresAt;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Check if the refresh token is expired
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(this.expiresAt);
    }

    /**
     * Check if the refresh token is valid (not expired and not revoked)
     */
    public boolean isValid() {
        return !isExpired() && !revoked;
    }

    /**
     * Revoke the refresh token
     */
    public void revoke() {
        this.revoked = true;
        this.updatedAt = LocalDateTime.now();
    }
}
