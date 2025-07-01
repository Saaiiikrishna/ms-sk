package com.mysillydreams.userservice.repository.vendor;

import com.mysillydreams.userservice.domain.UserEntity;
import com.mysillydreams.userservice.domain.vendor.VendorProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface VendorProfileRepository extends JpaRepository<VendorProfile, UUID> {

    /**
     * Finds a vendor profile by the associated user entity.
     * Since the relationship is OneToOne, this should return at most one profile.
     *
     * @param user The UserEntity associated with the vendor profile.
     * @return An {@link Optional} containing the {@link VendorProfile} if found, or empty otherwise.
     */
    Optional<VendorProfile> findByUser(UserEntity user);

    /**
     * Finds a vendor profile by the ID of the associated user entity.
     *
     * @param userId The UUID of the UserEntity.
     * @return An {@link Optional} containing the {@link VendorProfile} if found, or empty otherwise.
     */
    Optional<VendorProfile> findByUserId(UUID userId);
}
