package com.mysillydreams.userservice.repository.support;

import com.mysillydreams.userservice.domain.UserEntity;
import com.mysillydreams.userservice.domain.support.SupportProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SupportProfileRepository extends JpaRepository<SupportProfile, UUID> {

    /**
     * Finds a support profile by the associated user entity.
     *
     * @param user The UserEntity associated with the support profile.
     * @return An {@link Optional} containing the {@link SupportProfile} if found.
     */
    Optional<SupportProfile> findByUser(UserEntity user);

    /**
     * Finds a support profile by the ID of the associated user entity.
     *
     * @param userId The UUID of the UserEntity.
     * @return An {@link Optional} containing the {@link SupportProfile} if found.
     */
    Optional<SupportProfile> findByUserId(UUID userId);

    /**
     * Finds all active support profiles.
     * @return A list of active {@link SupportProfile}.
     */
    List<SupportProfile> findByActiveTrue();

    /**
     * Finds all active support profiles with a given specialization.
     * @param specialization The specialization string to filter by.
     * @return A list of active {@link SupportProfile} with the matching specialization.
     */
    List<SupportProfile> findByActiveTrueAndSpecializationIgnoreCase(String specialization);
}
