package com.mysillydreams.catalogservice.domain.repository;

import com.mysillydreams.catalogservice.domain.model.CartItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

@Repository
public interface CartItemRepository extends JpaRepository<CartItemEntity, UUID> {

    // Find a specific item within a specific cart
    Optional<CartItemEntity> findByCartIdAndCatalogItemId(UUID cartId, UUID catalogItemId);

    // Find all items for a given cart
    List<CartItemEntity> findByCartId(UUID cartId);

    // Delete item by cartId and catalogItemId
    void deleteByCartIdAndCatalogItemId(UUID cartId, UUID catalogItemId);
}
