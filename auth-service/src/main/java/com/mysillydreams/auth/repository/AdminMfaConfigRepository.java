package com.mysillydreams.auth.repository;

import com.mysillydreams.auth.domain.AdminMfaConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AdminMfaConfigRepository extends JpaRepository<AdminMfaConfig, UUID> {

    /**
     * Finds MFA configuration by user ID.
     * Since userId is the primary key, this is equivalent to findById(userId).
     *
     * @param userId The UUID of the user.
     * @return An {@link Optional} containing the {@link AdminMfaConfig} if found.
     */
    Optional<AdminMfaConfig> findByUserId(UUID userId);
}
