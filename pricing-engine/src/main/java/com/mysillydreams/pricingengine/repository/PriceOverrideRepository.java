package com.mysillydreams.pricingengine.repository;

import com.mysillydreams.pricingengine.domain.PriceOverrideEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PriceOverrideRepository extends JpaRepository<PriceOverrideEntity, UUID> {
    // Custom query methods can be added here if needed.
}
