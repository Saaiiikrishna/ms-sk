package com.mysillydreams.catalogservice.domain.repository;

import com.mysillydreams.catalogservice.domain.model.PriceOverrideEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PriceOverrideRepository extends JpaRepository<PriceOverrideEntity, UUID> {

    List<PriceOverrideEntity> findByCatalogItemId(UUID itemId);

    List<PriceOverrideEntity> findByCatalogItemIdAndEnabledTrue(UUID itemId);

    /**
     * Finds active price overrides for a given item at a specific point in time.
     * An override is active if it's enabled, its start_time is null or in the past,
     * and its end_time is null or in the future.
     * If multiple overrides match, further logic in service layer might be needed to pick one (e.g., latest created).
     */
    @Query("SELECT po FROM PriceOverrideEntity po " +
           "WHERE po.catalogItem.id = :itemId " +
           "AND po.enabled = true " +
           "AND (po.startTime IS NULL OR po.startTime <= :currentTime) " +
           "AND (po.endTime IS NULL OR po.endTime >= :currentTime) " +
           "ORDER BY po.createdAt DESC") // Example: pick the most recently created if multiple active ones
    List<PriceOverrideEntity> findActiveOverridesForItemAtTime(
            @Param("itemId") UUID itemId,
            @Param("currentTime") Instant currentTime
    );

    // Default method to get the single "best" active override (e.g. most recent)
    default Optional<PriceOverrideEntity> findCurrentActiveOverrideForItem(UUID itemId) {
        List<PriceOverrideEntity> activeOverrides = findActiveOverridesForItemAtTime(itemId, Instant.now());
        return activeOverrides.stream().findFirst(); // Due to ORDER BY createdAt DESC
    }
}
