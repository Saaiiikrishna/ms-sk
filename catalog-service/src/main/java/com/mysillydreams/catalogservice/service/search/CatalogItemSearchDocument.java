package com.mysillydreams.catalogservice.service.search;

import com.mysillydreams.catalogservice.domain.model.ItemType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
// import org.springframework.data.annotation.Id; // If using Spring Data OpenSearch
// import org.springframework.data.elasticsearch.annotations.Document; // If using Spring Data OpenSearch/Elasticsearch

// This is a simple POJO for direct OpenSearch client usage.
// If Spring Data OpenSearch were used, annotations like @Document and field mappings would go here.
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CatalogItemSearchDocument {
    // Note: The document ID in OpenSearch will be the CatalogItemEntity's UUID as a string.
    // This 'id' field here is for the content of the document itself.
    private String id; // UUID of the catalog item
    private String sku;
    private String name;
    private String description;
    private ItemType itemType;
    private Double basePrice; // OpenSearch 'double' type for BigDecimal
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;

    private String categoryId;
    private String categoryName;
    private String categoryPathKeyword; // For exact path matches / prefix
    private String categoryPathHierarchy; // For hierarchical facet queries

    // Using Map<String, Object> for flattened metadata.
    // If metadata has a known, queryable structure, map it explicitly.
    private Map<String, Object> metadata_flattened;
}
