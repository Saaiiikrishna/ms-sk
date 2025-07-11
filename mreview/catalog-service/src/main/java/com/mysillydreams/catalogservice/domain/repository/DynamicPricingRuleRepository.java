package com.mysillydreams.catalogservice.domain.repository;

import com.mysillydreams.catalogservice.domain.model.DynamicPricingRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface DynamicPricingRuleRepository extends JpaRepository<DynamicPricingRuleEntity, UUID> {

    List<DynamicPricingRuleEntity> findByCatalogItemId(UUID itemId);

    List<DynamicPricingRuleEntity> findByCatalogItemIdAndEnabledTrue(UUID itemId);

    // Find rules for an item that are currently active (enabled and within time window if applicable, though DPR doesn't have time window)
    // This might be more complex depending on how rule_type and parameters define "active" state beyond just 'enabled' flag
    @Query("SELECT dpr FROM DynamicPricingRuleEntity dpr WHERE dpr.catalogItem.id = :itemId AND dpr.enabled = true")
    List<DynamicPricingRuleEntity> findActiveRulesForItem(@Param("itemId") UUID itemId);

    // Example: Find rules of a specific type for an item
    List<DynamicPricingRuleEntity> findByCatalogItemIdAndRuleTypeAndEnabledTrue(UUID itemId, String ruleType);
}
