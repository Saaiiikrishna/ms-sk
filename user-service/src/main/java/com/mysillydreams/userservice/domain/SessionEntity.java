package com.mysillydreams.userservice.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sessions") // As per PRD Data Model
@Getter
@Setter
public class SessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(nullable = false)
    private Instant loginTime;

    private Instant logoutTime; // Nullable

    @Column(length = 45) // Sufficient for IPv4 and IPv6
    private String ipAddress;
    // Consider if ipAddress needs encryption based on compliance (e.g. GDPR).
    // If so: @Convert(converter = CryptoConverter.class) @Column(length = 1024)

    @Column(length = 100)
    private String location; // E.g., "City, Country" from GeoIP
    // Consider if location needs encryption.
    // If so: @Convert(converter = CryptoConverter.class) @Column(length = 1024)

    // User-Agent or other session details can be added here
    // @Column(length = 512)
    // private String userAgent;
}
