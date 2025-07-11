package com.mysillydreams.catalogservice.domain.repository;

import com.mysillydreams.catalogservice.domain.model.CategoryEntity;
import com.mysillydreams.catalogservice.domain.model.ItemType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CategoryRepository extends JpaRepository<CategoryEntity, UUID> {

    Optional<CategoryEntity> findByName(String name);

    // Find top-level categories
    List<CategoryEntity> findByParentCategoryIsNull();

    // Find direct children of a category
    List<CategoryEntity> findByParentCategoryId(UUID parentId);

    // Find categories by type
    List<CategoryEntity> findByType(ItemType type);

    // For materialized path: find descendants (path starts with parent's path + parent's ID + separator)
    // Example path: /uuid1/uuid2/
    // To find children of uuid1: path LIKE '/uuid1/%' AND path NOT LIKE '/uuid1/%/%' (direct children)
    // To find all descendants of uuid1: path LIKE '/uuid1/%'
    @Query("SELECT c FROM CategoryEntity c WHERE c.path LIKE :parentPathPrefix% AND c.id <> :parentId")
    List<CategoryEntity> findDescendantsByPath(@Param("parentPathPrefix") String parentPathPrefix, @Param("parentId") UUID parentId);

    @Query("SELECT c FROM CategoryEntity c WHERE c.path LIKE :pathPrefix%")
    List<CategoryEntity> findAllDescendantsByPath(@Param("pathPrefix") String pathPrefix);

    // Find by path (exact match)
    Optional<CategoryEntity> findByPath(String path);

    // Check if a category with the same name exists under a specific parent
    Optional<CategoryEntity> findByParentCategoryIdAndName(UUID parentId, String name);

    // Check if a top-level category with the name exists
    Optional<CategoryEntity> findByParentCategoryIsNullAndName(String name);
}
