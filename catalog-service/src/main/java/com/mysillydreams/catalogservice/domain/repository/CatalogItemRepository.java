package com.mysillydreams.catalogservice.domain.repository;

import com.mysillydreams.catalogservice.domain.model.CatalogItemEntity;
import com.mysillydreams.catalogservice.domain.model.ItemType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CatalogItemRepository extends JpaRepository<CatalogItemEntity, UUID>, JpaSpecificationExecutor<CatalogItemEntity> {

    Optional<CatalogItemEntity> findBySku(String sku);

    List<CatalogItemEntity> findByCategoryId(UUID categoryId);
    Page<CatalogItemEntity> findByCategoryId(UUID categoryId, Pageable pageable);

    List<CatalogItemEntity> findByItemType(ItemType itemType);
    Page<CatalogItemEntity> findByItemType(ItemType itemType, Pageable pageable);

    List<CatalogItemEntity> findByActive(boolean active);
    Page<CatalogItemEntity> findByActive(boolean active, Pageable pageable);

    // Example of a more complex query, perhaps for searching
    @Query("SELECT ci FROM CatalogItemEntity ci WHERE " +
           "LOWER(ci.name) LIKE LOWER(CONCAT('%', :nameQuery, '%')) AND " +
           "ci.active = true AND " +
           "(:categoryId IS NULL OR ci.category.id = :categoryId) AND " +
           "(:itemType IS NULL OR ci.itemType = :itemType) AND " +
           "(:minPrice IS NULL OR ci.basePrice >= :minPrice) AND " +
           "(:maxPrice IS NULL OR ci.basePrice <= :maxPrice)")
    Page<CatalogItemEntity> searchActiveItems(
            @Param("nameQuery") String nameQuery,
            @Param("categoryId") UUID categoryId,
            @Param("itemType") ItemType itemType,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            Pageable pageable
    );

    // Find items by a list of IDs, useful for cart processing or batch operations
    List<CatalogItemEntity> findByIdIn(List<UUID> ids);

    // Find by category path (items in a category and all its subcategories)
    @Query("SELECT ci FROM CatalogItemEntity ci WHERE ci.category.path LIKE :categoryPathPrefix% AND ci.active = true")
    Page<CatalogItemEntity> findActiveItemsByCategoryPath(@Param("categoryPathPrefix") String categoryPathPrefix, Pageable pageable);
}
