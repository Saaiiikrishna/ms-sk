package com.mysillydreams.auth.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.GenericGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "password_rotation_log")
@Getter
@Setter
@NoArgsConstructor
public class PasswordRotationLog {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(
            name = "UUID",
            strategy = "org.hibernate.id.UUIDGenerator"
    )
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotNull
    @Column(name = "user_id", nullable = false)
    private UUID userId; // This would typically be the Keycloak user ID

    @NotNull
    @Column(name = "rotated_at", nullable = false)
    private Instant rotatedAt;

    public PasswordRotationLog(UUID userId, Instant rotatedAt) {
        this.userId = userId;
        this.rotatedAt = rotatedAt;
    }

    // Consider adding toString, equals, and hashCode methods if needed,
    // especially if these entities are part of sets or used in complex comparisons.
    // Lombok's @Data or @Value can also be used, but @Getter/@Setter/@NoArgsConstructor is fine.
}
