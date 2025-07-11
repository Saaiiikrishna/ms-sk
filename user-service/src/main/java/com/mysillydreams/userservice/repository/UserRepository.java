package com.mysillydreams.userservice.repository;

import com.mysillydreams.userservice.domain.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, UUID> {

    // Standard find methods will now respect the @Where clause implicitly

    /**
     * Finds a user by their unique business reference ID.
     *
     * @param referenceId The reference ID to search for.
     * @return An {@link Optional} containing the {@link UserEntity} if found, or empty otherwise.
     */
    Optional<UserEntity> findByReferenceId(String referenceId);

    /**
     * Finds a user by their email address.
     * Note: Email is stored encrypted. This query will effectively search against encrypted values.
     * If a case-insensitive search or partial search on email is needed,
     * it cannot be done directly on the encrypted column with standard SQL.
     * Such requirements would need careful consideration (e.g., storing a searchable hash,
     * or decrypting in application layer for small datasets - not recommended for performance/security).
     * For exact match on encrypted email (e.g. to check uniqueness before creation), this can work if
     * the encryption is deterministic for the same input (which it often is for a given key, but not if IV changes per encryption).
     * Vault transit encryption is generally deterministic for a given key and context.
     *
     * @param email The encrypted email to search for.
     * @return An {@link Optional} containing the {@link UserEntity} if found.
     */
    Optional<UserEntity> findByEmail(String email); // Searching by encrypted email.

    // Additional methods for admin operations
    Page<UserEntity> findByActiveTrue(Pageable pageable);
    Page<UserEntity> findByActiveFalse(Pageable pageable);
}
