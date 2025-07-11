package com.mysillydreams.auth.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity for caching admin profile information from Keycloak.
 * Improves performance by reducing Keycloak API calls for admin listings.
 */
@Entity
@Table(name = "admin_profiles_cache")
@Getter
@Setter
@NoArgsConstructor
public class AdminProfileCache {

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @NotBlank
    @Column(name = "username", nullable = false, unique = true)
    private String username;

    @NotBlank
    @Email
    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @NotNull
    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;

    @Column(name = "last_login")
    private LocalDateTime lastLogin;

    @NotNull
    @Column(name = "mfa_enabled", nullable = false)
    private Boolean mfaEnabled = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public AdminProfileCache(UUID userId, String username, String email, String firstName, String lastName) {
        this.userId = userId;
        this.username = username;
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.enabled = true;
        this.mfaEnabled = false;
    }

    /**
     * Get the full display name of the admin.
     */
    public String getFullName() {
        if (firstName != null && lastName != null) {
            return firstName + " " + lastName;
        } else if (firstName != null) {
            return firstName;
        } else if (lastName != null) {
            return lastName;
        } else {
            return username;
        }
    }

    /**
     * Update the last login timestamp.
     */
    public void updateLastLogin() {
        this.lastLogin = LocalDateTime.now();
    }

    /**
     * Check if the admin has logged in recently (within last 30 days).
     */
    public boolean hasRecentLogin() {
        if (lastLogin == null) {
            return false;
        }
        return lastLogin.isAfter(LocalDateTime.now().minusDays(30));
    }

    /**
     * Update profile information from Keycloak user data.
     */
    public void updateFromKeycloakUser(String username, String email, String firstName, String lastName, Boolean enabled) {
        this.username = username;
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.enabled = enabled;
    }
}
