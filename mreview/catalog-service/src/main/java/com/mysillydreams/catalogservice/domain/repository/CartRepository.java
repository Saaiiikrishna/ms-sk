package com.mysillydreams.catalogservice.domain.repository;

import com.mysillydreams.catalogservice.domain.model.CartEntity;
import com.mysillydreams.catalogservice.domain.model.CartStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CartRepository extends JpaRepository<CartEntity, UUID> {

    // Find an active cart for a user
    Optional<CartEntity> findByUserIdAndStatus(String userId, CartStatus status);

    // Find all carts for a user (active, checked_out, etc.)
    List<CartEntity> findByUserId(String userId);

    // Find carts by status (e.g., all ACTIVE carts, or all ABANDONED carts)
    List<CartEntity> findByStatus(CartStatus status);

    // Find carts that were last updated before a certain timestamp and are still ACTIVE (for cleanup/abandoned cart logic)
    @Query("SELECT c FROM CartEntity c WHERE c.status = :status AND c.updatedAt < :timestamp")
    List<CartEntity> findByStatusAndUpdatedAtBefore(
            @Param("status") CartStatus status,
            @Param("timestamp") Instant timestamp
    );
}
