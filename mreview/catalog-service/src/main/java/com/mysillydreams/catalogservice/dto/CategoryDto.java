package com.mysillydreams.catalogservice.dto;

import com.mysillydreams.catalogservice.domain.model.ItemType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryDto {
    private UUID id;
    private String name;
    private UUID parentId; // ID of the parent category, null if top-level
    private String parentName; // Optional: denormalized parent name for convenience
    private ItemType type;
    private String path;
    private Instant createdAt;
    private Instant updatedAt;
    private List<CategoryDto> children; // For hierarchical display
    private Integer itemCount; // Optional: count of items directly in this category
}
