package com.mysillydreams.pricingengine.repository;

import com.mysillydreams.pricingengine.domain.DynamicPricingRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface DynamicPricingRuleRepository extends JpaRepository<DynamicPricingRuleEntity, UUID> {
    // Custom query methods can be added here if needed by the pricing engine,
    // e.g., findByItemId, findByRuleType, etc.
    // For now, basic CRUD via JpaRepository is sufficient for mirroring.
}
