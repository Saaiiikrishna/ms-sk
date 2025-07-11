package com.mysillydreams.catalogservice.domain.repository;

import com.mysillydreams.catalogservice.domain.model.StockLevelEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StockLevelRepository extends JpaRepository<StockLevelEntity, UUID> { // ID is CatalogItem's ID

    // The primary way to get stock is by item ID, which is the entity's ID.
    // JpaRepository.findById(itemId) will work directly.

    // Find stock levels for items that are below their reorder level
    @Query("SELECT sl FROM StockLevelEntity sl WHERE sl.quantityOnHand < sl.reorderLevel")
    List<StockLevelEntity> findItemsBelowReorderLevel();

    Optional<StockLevelEntity> findByCatalogItemId(UUID catalogItemId);

    // For batch fetching stock levels
    List<StockLevelEntity> findByCatalogItemIdIn(List<UUID> catalogItemIds);
}
