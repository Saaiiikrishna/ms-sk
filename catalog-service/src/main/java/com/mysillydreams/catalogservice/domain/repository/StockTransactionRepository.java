package com.mysillydreams.catalogservice.domain.repository;

import com.mysillydreams.catalogservice.domain.model.StockTransactionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StockTransactionRepository extends JpaRepository<StockTransactionEntity, UUID> {

    List<StockTransactionEntity> findByCatalogItemIdOrderByTransactionTimeDesc(UUID catalogItemId);

    Page<StockTransactionEntity> findByCatalogItemIdOrderByTransactionTimeDesc(UUID catalogItemId, Pageable pageable);

    // Could add more specific finders, e.g., by type or referenceId if needed for auditing queries
}
