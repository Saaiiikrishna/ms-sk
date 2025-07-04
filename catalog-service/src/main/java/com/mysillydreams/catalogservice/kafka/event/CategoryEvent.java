package com.mysillydreams.catalogservice.kafka.event;

import com.mysillydreams.catalogservice.domain.model.ItemType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryEvent {
    private String eventType; // e.g., "category.created", "category.updated", "category.deleted"
    private UUID categoryId;
    private String name;
    private UUID parentId;
    private ItemType type;
    private String path;
    private Instant timestamp;

    // Optional: For updates, could include old values if consumers need them
    // private CategoryDetails oldDetails;

    // public static class CategoryDetails {
    //     private String name;
    //     private UUID parentId;
    //     private ItemType type;
    //     private String path;
    // }
}
