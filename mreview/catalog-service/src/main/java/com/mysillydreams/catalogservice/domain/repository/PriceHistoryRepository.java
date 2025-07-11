package com.mysillydreams.catalogservice.domain.repository;

import com.mysillydreams.catalogservice.domain.model.PriceHistoryEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest; // Added import
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PriceHistoryRepository extends JpaRepository<PriceHistoryEntity, UUID> {

    List<PriceHistoryEntity> findByCatalogItemIdOrderByEffectiveFromDesc(UUID catalogItemId);
    Page<PriceHistoryEntity> findByCatalogItemIdOrderByEffectiveFromDesc(UUID catalogItemId, Pageable pageable);

    // Get the current effective price for an item
    @Query("SELECT ph FROM PriceHistoryEntity ph " +
           "WHERE ph.catalogItem.id = :itemId AND ph.effectiveFrom <= :date " +
           "ORDER BY ph.effectiveFrom DESC")
    List<PriceHistoryEntity> findCurrentEffectivePrice(@Param("itemId") UUID itemId, @Param("date") Instant date, Pageable pageable);

    // Helper to get just the latest one
    default Optional<PriceHistoryEntity> findLatestByCatalogItemId(UUID catalogItemId) {
        Page<PriceHistoryEntity> page = findByCatalogItemIdOrderByEffectiveFromDesc(catalogItemId, PageRequest.of(0, 1));
        return page.hasContent() ? Optional.of(page.getContent().get(0)) : Optional.empty();
    }
}
