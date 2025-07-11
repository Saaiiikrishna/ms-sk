package com.mysillydreams.catalogservice.domain.repository;

import com.mysillydreams.catalogservice.domain.model.BulkPricingRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface BulkPricingRuleRepository extends JpaRepository<BulkPricingRuleEntity, UUID> {

    List<BulkPricingRuleEntity> findByCatalogItemId(UUID catalogItemId);

    List<BulkPricingRuleEntity> findByCatalogItemIdAndActiveTrue(UUID catalogItemId);

    // Find active rules for a specific item and quantity, valid at a given time
    @Query("SELECT bpr FROM BulkPricingRuleEntity bpr " +
           "WHERE bpr.catalogItem.id = :itemId " +
           "AND bpr.active = true " +
           "AND bpr.minQuantity <= :quantity " +
           "AND (bpr.validFrom IS NULL OR bpr.validFrom <= :now) " +
           "AND (bpr.validTo IS NULL OR bpr.validTo >= :now) " +
           "ORDER BY bpr.minQuantity DESC") // To easily pick the best applicable rule (highest minQuantity first)
    List<BulkPricingRuleEntity> findActiveApplicableRules(
            @Param("itemId") UUID itemId,
            @Param("quantity") Integer quantity,
            @Param("now") Instant now
    );

    List<BulkPricingRuleEntity> findByCatalogItemIdAndActiveTrueAndValidFromBeforeAndValidToAfter(
        UUID catalogItemId, Instant validFrom, Instant validTo);

    List<BulkPricingRuleEntity> findByCatalogItemIdAndActiveTrueAndMinQuantityLessThanEqualAndValidFromBeforeAndValidToAfter(
        UUID catalogItemId, Integer minQuantity, Instant validFrom, Instant validTo);

}
