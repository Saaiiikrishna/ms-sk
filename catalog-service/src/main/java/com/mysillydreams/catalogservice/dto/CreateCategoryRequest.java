package com.mysillydreams.catalogservice.dto;

import com.mysillydreams.catalogservice.domain.model.ItemType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateCategoryRequest {

    @NotBlank(message = "Category name cannot be blank")
    @Size(min = 2, max = 100, message = "Category name must be between 2 and 100 characters")
    private String name;

    private UUID parentId; // Nullable for top-level categories

    @NotNull(message = "Category type (PRODUCT or SERVICE) must be specified")
    private ItemType type;
}
