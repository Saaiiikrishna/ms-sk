package com.mysillydreams.userservice.repository.delivery;

import com.mysillydreams.userservice.domain.UserEntity;
import com.mysillydreams.userservice.domain.delivery.DeliveryProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeliveryProfileRepository extends JpaRepository<DeliveryProfile, UUID> {

    /**
     * Finds a delivery profile by the associated user entity.
     *
     * @param user The UserEntity associated with the delivery profile.
     * @return An {@link Optional} containing the {@link DeliveryProfile} if found, or empty otherwise.
     */
    Optional<DeliveryProfile> findByUser(UserEntity user);

    /**
     * Finds a delivery profile by the ID of the associated user entity.
     *
     * @param userId The UUID of the UserEntity.
     * @return An {@link Optional} containing the {@link DeliveryProfile} if found, or empty otherwise.
     */
    Optional<DeliveryProfile> findByUserId(UUID userId);

    /**
     * Finds active delivery profiles.
     *
     * @return A list of active {@link DeliveryProfile}.
     */
    // List<DeliveryProfile> findByActiveTrue(); // Example if needed
}
