package com.mysillydreams.catalogservice.controller;

import com.mysillydreams.catalogservice.dto.CategoryDto;
import com.mysillydreams.catalogservice.dto.CreateCategoryRequest;
import com.mysillydreams.catalogservice.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
@Tag(name = "Category Management", description = "APIs for managing product and service categories")
public class CategoryController {

    private final CategoryService categoryService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_CATALOG_MANAGER')")
    @Operation(summary = "Create a new category", description = "Creates a new top-level or sub-category.")
    @ApiResponse(responseCode = "201", description = "Category created successfully")
    @ApiResponse(responseCode = "400", description = "Invalid request payload")
    @ApiResponse(responseCode = "401", description = "Unauthorized")
    @ApiResponse(responseCode = "403", description = "Forbidden")
    public ResponseEntity<CategoryDto> createCategory(@Valid @RequestBody CreateCategoryRequest request) {
        CategoryDto createdCategory = categoryService.createCategory(request);
        return new ResponseEntity<>(createdCategory, HttpStatus.CREATED);
    }

    @GetMapping
    @Operation(summary = "List top-level categories", description = "Retrieves a list of all top-level categories.")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved categories")
    public ResponseEntity<List<CategoryDto>> getTopLevelCategories(
            @Parameter(description = "Whether to include child categories recursively")
            @RequestParam(defaultValue = "false") boolean includeChildren) {
        List<CategoryDto> categories = categoryService.getTopLevelCategories(includeChildren);
        return ResponseEntity.ok(categories);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get category by ID", description = "Retrieves a specific category by its ID, optionally including its children.")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved category")
    @ApiResponse(responseCode = "404", description = "Category not found")
    public ResponseEntity<CategoryDto> getCategoryById(
            @Parameter(description = "ID of the category to retrieve") @PathVariable UUID id,
            @Parameter(description = "Whether to include child categories recursively")
            @RequestParam(defaultValue = "false") boolean includeChildren) {
        CategoryDto category = categoryService.getCategoryById(id, includeChildren);
        return ResponseEntity.ok(category);
    }

    @GetMapping("/{id}/subcategories")
    @Operation(summary = "Get subcategories of a category", description = "Retrieves direct subcategories of a specific category.")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved subcategories")
    @ApiResponse(responseCode = "404", description = "Parent category not found")
    public ResponseEntity<List<CategoryDto>> getSubcategories(
            @Parameter(description = "ID of the parent category") @PathVariable UUID id,
            @Parameter(description = "Whether to include child categories recursively for the subcategories")
            @RequestParam(defaultValue = "false") boolean includeChildren) {
        List<CategoryDto> subcategories = categoryService.getSubcategories(id, includeChildren);
        return ResponseEntity.ok(subcategories);
    }


    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_CATALOG_MANAGER')")
    @Operation(summary = "Update a category", description = "Updates an existing category's name, parent, or type.")
    @ApiResponse(responseCode = "200", description = "Category updated successfully")
    @ApiResponse(responseCode = "400", description = "Invalid request payload")
    @ApiResponse(responseCode = "401", description = "Unauthorized")
    @ApiResponse(responseCode = "403", description = "Forbidden")
    @ApiResponse(responseCode = "404", description = "Category not found")
    public ResponseEntity<CategoryDto> updateCategory(
            @Parameter(description = "ID of the category to update") @PathVariable UUID id,
            @Valid @RequestBody CreateCategoryRequest request) {
        CategoryDto updatedCategory = categoryService.updateCategory(id, request);
        return ResponseEntity.ok(updatedCategory);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_CATALOG_MANAGER')")
    @Operation(summary = "Delete a category", description = "Deletes a category if it's empty (no items or subcategories).")
    @ApiResponse(responseCode = "204", description = "Category deleted successfully")
    @ApiResponse(responseCode = "400", description = "Category cannot be deleted (e.g., not empty)")
    @ApiResponse(responseCode = "401", description = "Unauthorized")
    @ApiResponse(responseCode = "403", description = "Forbidden")
    @ApiResponse(responseCode = "404", description = "Category not found")
    public ResponseEntity<Void> deleteCategory(
            @Parameter(description = "ID of the category to delete") @PathVariable UUID id) {
        categoryService.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }
}
