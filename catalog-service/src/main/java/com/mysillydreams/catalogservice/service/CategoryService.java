package com.mysillydreams.catalogservice.service;

import com.mysillydreams.catalogservice.domain.model.CategoryEntity;
import com.mysillydreams.catalogservice.domain.model.ItemType;
import com.mysillydreams.catalogservice.domain.repository.CategoryRepository;
import com.mysillydreams.catalogservice.domain.repository.CatalogItemRepository;
import com.mysillydreams.catalogservice.dto.CategoryDto;
import com.mysillydreams.catalogservice.dto.CreateCategoryRequest;
import com.mysillydreams.catalogservice.exception.DuplicateResourceException;
import com.mysillydreams.catalogservice.exception.InvalidRequestException;
import com.mysillydreams.catalogservice.exception.ResourceNotFoundException;
import com.mysillydreams.catalogservice.kafka.event.CategoryEvent;
import com.mysillydreams.catalogservice.kafka.producer.KafkaProducerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final CatalogItemRepository catalogItemRepository; // To check if category is empty before deletion
    private final KafkaProducerService kafkaProducerService;

    @Value("${app.kafka.topic.category-created}")
    private String categoryCreatedTopic;
    @Value("${app.kafka.topic.category-updated}")
    private String categoryUpdatedTopic;
    @Value("${app.kafka.topic.category-deleted}")
    private String categoryDeletedTopic;

    private static final String PATH_SEPARATOR = "/";

    @Transactional
    public CategoryDto createCategory(CreateCategoryRequest request) {
        log.info("Creating category with name: {}", request.getName());

        // Check for duplicate name under the same parent (or globally for top-level)
        if (request.getParentId() == null) {
            categoryRepository.findByParentCategoryIsNullAndName(request.getName())
                .ifPresent(c -> { throw new DuplicateResourceException("Category", "name", request.getName() + " (top-level)"); });
        } else {
            categoryRepository.findByParentCategoryIdAndName(request.getParentId(), request.getName())
                .ifPresent(c -> { throw new DuplicateResourceException("Category", "name", request.getName() + " (under parent " + request.getParentId() + ")"); });
        }

        CategoryEntity category = new CategoryEntity();
        category.setName(request.getName());
        category.setType(request.getType());

        String path;
        if (request.getParentId() != null) {
            CategoryEntity parentCategory = categoryRepository.findById(request.getParentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Parent Category", "id", request.getParentId()));

            if (parentCategory.getType() != request.getType()) {
                throw new InvalidRequestException("Category type must match parent category type. Parent is " +
                                                  parentCategory.getType() + ", requested is " + request.getType());
            }
            category.setParentCategory(parentCategory);
            // Path will be set after ID is generated
        } else {
            // Path will be set after ID is generated
        }

        CategoryEntity savedCategory = categoryRepository.save(category);
        // Now that ID is generated, set the path
        savedCategory.setPath(generatePath(savedCategory));
        savedCategory = categoryRepository.save(savedCategory); // Save again to persist path


        CategoryDto categoryDto = convertToDto(savedCategory, false); // Don't load children for create response
        publishCategoryEvent(categoryCreatedTopic, "category.created", savedCategory);
        log.info("Category created successfully with ID: {}", savedCategory.getId());
        return categoryDto;
    }

    @Transactional(readOnly = true)
    public CategoryDto getCategoryById(UUID categoryId, boolean includeChildren) {
        log.debug("Fetching category by ID: {}", categoryId);
        CategoryEntity category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", categoryId));
        return convertToDto(category, includeChildren);
    }

    @Transactional(readOnly = true)
    public List<CategoryDto> getTopLevelCategories(boolean includeChildren) {
        log.debug("Fetching top-level categories");
        return categoryRepository.findByParentCategoryIsNull().stream()
                .map(cat -> convertToDto(cat, includeChildren))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<CategoryDto> getSubcategories(UUID parentId, boolean includeChildren) {
        log.debug("Fetching subcategories for parent ID: {}", parentId);
        if (!categoryRepository.existsById(parentId)) {
            throw new ResourceNotFoundException("Parent Category", "id", parentId);
        }
        return categoryRepository.findByParentCategoryId(parentId).stream()
                .map(cat -> convertToDto(cat, includeChildren))
                .collect(Collectors.toList());
    }


    @Transactional
    public CategoryDto updateCategory(UUID categoryId, CreateCategoryRequest request) {
        log.info("Updating category with ID: {}", categoryId);
        CategoryEntity category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", categoryId));

        // Store old values for event if needed, e.g. oldPath = category.getPath();

        // Name change validation
        if (!category.getName().equals(request.getName())) {
            Optional<CategoryEntity> existingByName;
            if (category.getParentCategory() == null) {
                existingByName = categoryRepository.findByParentCategoryIsNullAndName(request.getName());
            } else {
                existingByName = categoryRepository.findByParentCategoryIdAndName(category.getParentCategory().getId(), request.getName());
            }
            if (existingByName.isPresent() && !existingByName.get().getId().equals(categoryId)) {
                 throw new DuplicateResourceException("Category", "name", request.getName());
            }
            category.setName(request.getName());
        }

        // Type change validation (usually not allowed if items exist or children exist with different type)
        if (category.getType() != request.getType()) {
            if (!catalogItemRepository.findByCategoryId(categoryId).isEmpty()) {
                throw new InvalidRequestException("Cannot change category type. Category contains items.");
            }
            if (!category.getChildCategories().isEmpty()) {
                 // Or recursively check/update children types if that's a desired feature
                throw new InvalidRequestException("Cannot change category type. Category has subcategories. Change subcategory types first or ensure consistency.");
            }
            category.setType(request.getType());
        }

        // Parent change (reparenting)
        UUID currentParentId = category.getParentCategory() != null ? category.getParentCategory().getId() : null;
        if ((currentParentId == null && request.getParentId() != null) ||
            (currentParentId != null && !currentParentId.equals(request.getParentId()))) {

            if (request.getParentId() != null) {
                CategoryEntity newParent = categoryRepository.findById(request.getParentId())
                        .orElseThrow(() -> new ResourceNotFoundException("New Parent Category", "id", request.getParentId()));

                // Cycle detection: new parent cannot be self or a descendant of current category
                if (newParent.getId().equals(category.getId()) || (newParent.getPath() != null && newParent.getPath().startsWith(category.getPath()))) {
                    throw new InvalidRequestException("Invalid reparenting operation: creates a cycle.");
                }
                if (newParent.getType() != category.getType()) {
                     throw new InvalidRequestException("New parent category type must match current category type.");
                }
                category.setParentCategory(newParent);
            } else { // Moving to top-level
                category.setParentCategory(null);
            }
            // Path needs to be regenerated for the category and all its descendants
            category.setPath(generatePath(category)); // Regenerate path for the category itself
            updateDescendantPaths(category); // Recursively update paths for children
        }


        CategoryEntity updatedCategory = categoryRepository.save(category);
        CategoryDto categoryDto = convertToDto(updatedCategory, false);
        publishCategoryEvent(categoryUpdatedTopic, "category.updated", updatedCategory /*, oldDetails if any */);
        log.info("Category updated successfully with ID: {}", updatedCategory.getId());
        return categoryDto;
    }

    private void updateDescendantPaths(CategoryEntity parent) {
        List<CategoryEntity> children = categoryRepository.findByParentCategoryId(parent.getId());
        for (CategoryEntity child : children) {
            child.setPath(generatePath(child)); // Parent's path should be up-to-date from generatePath(child)
            categoryRepository.save(child);
            updateDescendantPaths(child); // Recurse
        }
    }


    @Transactional
    public void deleteCategory(UUID categoryId) {
        log.info("Deleting category with ID: {}", categoryId);
        CategoryEntity category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", categoryId));

        if (!catalogItemRepository.findByCategoryId(categoryId).isEmpty()) {
            throw new InvalidRequestException("Cannot delete category. It contains catalog items.");
        }
        if (!category.getChildCategories().isEmpty()) {
            throw new InvalidRequestException("Cannot delete category. It has subcategories. Delete subcategories first.");
        }

        categoryRepository.delete(category);
        publishCategoryEvent(categoryDeletedTopic, "category.deleted", category);
        log.info("Category deleted successfully with ID: {}", categoryId);
    }

    private String generatePath(CategoryEntity category) {
        if (category.getParentCategory() == null) {
            return PATH_SEPARATOR + category.getId().toString() + PATH_SEPARATOR;
        }
        // Ensure parent path is correctly loaded and ends with a separator
        String parentPath = category.getParentCategory().getPath();
        if (parentPath == null || parentPath.isEmpty()){ // Should not happen if parent is persisted with path
            //This might indicate parent was not fully loaded or persisted. Re-fetch or throw error.
            CategoryEntity freshParent = categoryRepository.findById(category.getParentCategory().getId())
                .orElseThrow(() -> new IllegalStateException("Parent category disappeared during path generation"));
            parentPath = freshParent.getPath();
             if (parentPath == null || parentPath.isEmpty()){
                  throw new IllegalStateException("Parent category path is null or empty for parent ID: " + freshParent.getId());
             }
        }
        return parentPath + category.getId().toString() + PATH_SEPARATOR;
    }


    private void publishCategoryEvent(String topic, String eventType, CategoryEntity category) {
        CategoryEvent event = CategoryEvent.builder()
                .eventType(eventType)
                .categoryId(category.getId())
                .name(category.getName())
                .parentId(category.getParentCategory() != null ? category.getParentCategory().getId() : null)
                .type(category.getType())
                .path(category.getPath())
                .timestamp(Instant.now())
                .build();
        kafkaProducerService.sendMessage(topic, category.getId().toString(), event);
    }

    private CategoryDto convertToDto(CategoryEntity entity, boolean includeChildren) {
        if (entity == null) return null;
        CategoryDto dto = CategoryDto.builder()
                .id(entity.getId())
                .name(entity.getName())
                .parentId(entity.getParentCategory() != null ? entity.getParentCategory().getId() : null)
                .parentName(entity.getParentCategory() != null ? entity.getParentCategory().getName() : null)
                .type(entity.getType())
                .path(entity.getPath())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .itemCount((int) catalogItemRepository.countByCategoryId(entity.getId())) // Example: count items
                .build();
        if (includeChildren) {
            dto.setChildren(categoryRepository.findByParentCategoryId(entity.getId()).stream()
                .map(child -> convertToDto(child, true)) // Recursively include children's children
                .collect(Collectors.toList()));
        }
        return dto;
    }
}
