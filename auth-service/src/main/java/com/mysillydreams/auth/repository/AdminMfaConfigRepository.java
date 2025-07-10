package com.mysillydreams.auth.repository;

import com.mysillydreams.auth.entity.AdminMfaConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AdminMfaConfigRepository extends JpaRepository<AdminMfaConfig, UUID> {

    /**
     * Finds MFA configuration by admin profile ID.
     *
     * @param adminProfileId The UUID of the admin profile.
     * @return An {@link Optional} containing the {@link AdminMfaConfig} if found.
     */
    Optional<AdminMfaConfig> findByAdminProfileId(UUID adminProfileId);

    /**
     * Finds MFA configuration by user ID (alias for adminProfileId).
     *
     * @param userId The UUID of the user.
     * @return An {@link Optional} containing the {@link AdminMfaConfig} if found.
     */
    default Optional<AdminMfaConfig> findByUserId(UUID userId) {
        return findByAdminProfileId(userId);
    }

    /**
     * Delete MFA config by user ID.
     *
     * @param userId The UUID of the user.
     */
    @Modifying
    @Transactional
    void deleteByAdminProfileId(UUID adminProfileId);

    /**
     * Delete MFA config by user ID (alias for adminProfileId).
     *
     * @param userId The UUID of the user.
     */
    @Modifying
    @Transactional
    default void deleteByUserId(UUID userId) {
        deleteByAdminProfileId(userId);
    }
}
