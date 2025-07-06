package com.mysillydreams.inventorycore.repository;

import com.mysillydreams.inventorycore.domain.StockLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StockLevelRepository extends JpaRepository<StockLevel, String> {
    // JpaRepository provides common methods like findById, save, findAll, etc.
    // Custom query methods can be added here if needed.
}
