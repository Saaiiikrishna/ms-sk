package com.mysillydreams.inventoryapi.repository;

import com.mysillydreams.inventoryapi.domain.StockLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository // Optional, Spring Data JPA enables repositories by default
public interface StockLevelRepository extends JpaRepository<StockLevel, String> {
    // String is the type of the Primary Key (sku) for StockLevel entity
    // JpaRepository provides common persistence operations like save, findById, findAll, delete etc.
    // No custom methods needed based on the specification.
}
