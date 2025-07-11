package com.mysillydreams.userservice.repository.inventory;

import com.mysillydreams.userservice.domain.UserEntity;
import com.mysillydreams.userservice.domain.inventory.InventoryProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface InventoryProfileRepository extends JpaRepository<InventoryProfile, UUID> {

    /**
     * Finds an inventory profile by the associated user entity.
     *
     * @param user The UserEntity associated with the inventory profile.
     * @return An {@link Optional} containing the {@link InventoryProfile} if found, or empty otherwise.
     */
    Optional<InventoryProfile> findByUser(UserEntity user);

    /**
     * Finds an inventory profile by the ID of the associated user entity.
     *
     * @param userId The UUID of the UserEntity.
     * @return An {@link Optional} containing the {@link InventoryProfile} if found, or empty otherwise.
     */
    Optional<InventoryProfile> findByUserId(UUID userId);
}
