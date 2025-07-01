package com.mysillydreams.userservice.repository;

import com.mysillydreams.userservice.domain.AddressEntity;
import com.mysillydreams.userservice.domain.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AddressRepository extends JpaRepository<AddressEntity, UUID> {

    /**
     * Finds all addresses associated with a given user.
     *
     * @param user The user entity whose addresses are to be retrieved.
     * @return A list of {@link AddressEntity} belonging to the user.
     */
    List<AddressEntity> findByUser(UserEntity user);

    /**
     * Finds all addresses associated with a given user's ID.
     *
     * @param userId The UUID of the user.
     * @return A list of {@link AddressEntity} belonging to the user with the given ID.
     */
    List<AddressEntity> findByUserId(UUID userId);
}
