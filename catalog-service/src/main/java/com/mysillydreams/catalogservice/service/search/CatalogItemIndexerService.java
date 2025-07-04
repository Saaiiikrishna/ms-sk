package com.mysillydreams.catalogservice.service.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mysillydreams.catalogservice.config.OpenSearchConfig;
import com.mysillydreams.catalogservice.domain.model.CatalogItemEntity;
import com.mysillydreams.catalogservice.domain.model.CategoryEntity;
import com.mysillydreams.catalogservice.domain.repository.CatalogItemRepository;
import com.mysillydreams.catalogservice.domain.repository.CategoryRepository;
import com.mysillydreams.catalogservice.kafka.event.CatalogItemEvent;
import com.mysillydreams.catalogservice.kafka.event.CategoryEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.java.OpenSearchClient;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.core.DeleteRequest;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CatalogItemIndexerService {

    private final OpenSearchClient openSearchClient;
    private final CatalogItemRepository itemRepository; // To fetch full item details if event is minimal
    private final CategoryRepository categoryRepository; // To fetch category details
    private final ObjectMapper objectMapper; // For converting map to metadata_flattened if needed

    private static final String ITEM_CREATED_EVENT_TYPE = "catalog.item.created";
    private static final String ITEM_UPDATED_EVENT_TYPE = "catalog.item.updated";
    private static final String ITEM_DELETED_EVENT_TYPE = "catalog.item.deleted";
    private static final String CATEGORY_UPDATED_EVENT_TYPE = "category.updated";
    private static final String CATEGORY_DELETED_EVENT_TYPE = "category.deleted";


    @KafkaListener(topics = "${app.kafka.topic.item-created}", groupId = "catalog-search-indexer-item-created", containerFactory = "kafkaListenerContainerFactory")
    public void onItemCreated(@Payload CatalogItemEvent event) {
        log.info("Received item.created event for item ID: {}", event.getItemId());
        if (!ITEM_CREATED_EVENT_TYPE.equals(event.getEventType())) return; // Defensive check
        indexItem(event);
    }

    @KafkaListener(topics = "${app.kafka.topic.item-updated}", groupId = "catalog-search-indexer-item-updated", containerFactory = "kafkaListenerContainerFactory")
    public void onItemUpdated(@Payload CatalogItemEvent event) {
        log.info("Received item.updated event for item ID: {}", event.getItemId());
         if (!ITEM_UPDATED_EVENT_TYPE.equals(event.getEventType())) return;
        indexItem(event); // Re-index with updated data
    }

    @KafkaListener(topics = "${app.kafka.topic.item-deleted}", groupId = "catalog-search-indexer-item-deleted", containerFactory = "kafkaListenerContainerFactory")
    public void onItemDeleted(@Payload CatalogItemEvent event) {
        log.info("Received item.deleted event for item ID: {}", event.getItemId());
        if (!ITEM_DELETED_EVENT_TYPE.equals(event.getEventType())) return;
        deleteItemFromIndex(event.getItemId().toString());
    }

    @KafkaListener(topics = "${app.kafka.topic.category-updated}", groupId = "catalog-search-indexer-category-updated", containerFactory = "categoryEventKafkaListenerContainerFactory")
    @Transactional(readOnly = true) // Ensure transaction for reading items
    public void onCategoryUpdated(@Payload CategoryEvent event) {
        log.info("Received category.updated event for category ID: {}. Re-indexing associated items.", event.getCategoryId());
        if (!CATEGORY_UPDATED_EVENT_TYPE.equals(event.getEventType())) return;

        // If category name or path changes, items in this category need re-indexing
        // as their categoryName or categoryPath might change in the search document.
        List<CatalogItemEntity> itemsInCategory = itemRepository.findByCategoryId(event.getCategoryId());
        if (itemsInCategory.isEmpty()) {
            log.info("No items found in updated category ID: {}. No re-indexing needed for items.", event.getCategoryId());
            return;
        }

        log.info("Found {} items in category {} to re-index.", itemsInCategory.size(), event.getCategoryId());
        List<BulkOperation> bulkOperations = new ArrayList<>();
        for (CatalogItemEntity itemEntity : itemsInCategory) {
            // Assuming CategoryEntity in itemEntity is up-to-date or refetch if necessary
            // For this event, the category details in the event are the new ones.
            CategoryEntity updatedCategoryDetailsFromEvent = CategoryEntity.builder()
                .id(event.getCategoryId())
                .name(event.getName())
                .path(event.getPath())
                .build();

            CatalogItemSearchDocument doc = convertToSearchDocument(itemEntity, updatedCategoryDetailsFromEvent);
            IndexRequest<CatalogItemSearchDocument> indexRequest = new IndexRequest.Builder<CatalogItemSearchDocument>()
                    .index(OpenSearchConfig.CATALOG_ITEMS_INDEX_NAME)
                    .id(doc.getId())
                    .document(doc)
                    .build();
            bulkOperations.add(new BulkOperation.Builder().index(idx -> idx.index(OpenSearchConfig.CATALOG_ITEMS_INDEX_NAME).id(doc.getId()).document(doc)).build());
        }
        executeBulkRequest(bulkOperations, "re-indexing items for updated category " + event.getCategoryId());
    }

    @KafkaListener(topics = "${app.kafka.topic.category-deleted}", groupId = "catalog-search-indexer-category-deleted", containerFactory = "categoryEventKafkaListenerContainerFactory")
    @Transactional(readOnly = true)
    public void onCategoryDeleted(@Payload CategoryEvent event) {
        log.info("Received category.deleted event for category ID: {}. Deleting associated items from index.", event.getCategoryId());
        if (!CATEGORY_DELETED_EVENT_TYPE.equals(event.getEventType())) return;

        // If a category is deleted, items within it are typically also deleted or moved.
        // ItemService.deleteCategory checks if category is empty. If it enforced this, this listener might not find items.
        // However, if items *could* remain (e.g. soft delete of category, or items moved then category deleted),
        // this listener might need to delete them from the index or update their category info to a placeholder.
        // For now, let's assume items are deleted from DB if category is hard-deleted.
        // The item.deleted events for those items would handle their removal from index.
        // If items are NOT deleted from DB but category is, then they become "orphaned" in search if not handled.
        // Let's assume for now that if a category is deleted, any items that *were* in it and are now effectively
        // without a valid category should be removed from the search index if they haven't been deleted already.
        // This logic depends heavily on business rules for category/item deletion.

        // Simpler: If category is deleted, its items should have already been deleted or moved,
        // triggering their own item.updated/deleted events. So, this listener might not need to do much for items.
        // However, if items *could* still exist and point to a now-deleted category ID:
        List<CatalogItemEntity> itemsInDeletedCategory = itemRepository.findByCategoryId(event.getCategoryId());
         if (!itemsInDeletedCategory.isEmpty()) {
            log.warn("Category ID {} was deleted, but {} items still reference it. Removing them from search index.", event.getCategoryId(), itemsInDeletedCategory.size());
            List<BulkOperation> deleteOperations = itemsInDeletedCategory.stream()
                .map(item -> new BulkOperation.Builder().delete(d -> d.index(OpenSearchConfig.CATALOG_ITEMS_INDEX_NAME).id(item.getId().toString())).build())
                .collect(Collectors.toList());
            executeBulkRequest(deleteOperations, "deleting items from deleted category " + event.getCategoryId());
        } else {
            log.info("No items found associated with deleted category ID: {}. No item deletions from index needed via this event.", event.getCategoryId());
        }
    }


    private void indexItem(CatalogItemEvent itemEvent) {
        // Fetch full category details for name and path, as event might not have full parent hierarchy for path
        CategoryEntity category = categoryRepository.findById(itemEvent.getCategoryId())
            .orElse(null); // Handle if category somehow doesn't exist, though should be rare

        if (category == null) {
            log.error("Category ID {} not found for item ID {}. Cannot fully enrich search document. Indexing with available data.", itemEvent.getCategoryId(), itemEvent.getItemId());
            // Fallback: create a minimal CategoryEntity from event data if possible, or skip category fields.
            category = CategoryEntity.builder().id(itemEvent.getCategoryId()).name("Unknown Category").path("/unknown/").build();
        }


        CatalogItemSearchDocument doc = CatalogItemSearchDocument.builder()
                .id(itemEvent.getItemId().toString())
                .sku(itemEvent.getSku())
                .name(itemEvent.getName())
                .description(itemEvent.getDescription())
                .itemType(itemEvent.getItemType())
                .basePrice(itemEvent.getBasePrice() != null ? itemEvent.getBasePrice().doubleValue() : null)
                .active(itemEvent.isActive())
                .createdAt(itemEvent.getTimestamp()) // Assuming event timestamp is close to creation/update
                .updatedAt(itemEvent.getTimestamp()) // Or fetch actual entity timestamps
                .categoryId(itemEvent.getCategoryId().toString())
                .categoryName(category.getName())
                .categoryPathKeyword(category.getPath()) // Materialized path
                .categoryPathHierarchy(category.getPath()) // For path_hierarchy tokenizer
                .metadata_flattened(ensureMapForFlattened(itemEvent.getMetadata()))
                .build();
        try {
            IndexRequest<CatalogItemSearchDocument> request = new IndexRequest.Builder<CatalogItemSearchDocument>()
                    .index(OpenSearchConfig.CATALOG_ITEMS_INDEX_NAME)
                    .id(doc.getId())
                    .document(doc)
                    .build();
            openSearchClient.index(request);
            log.info("Indexed item ID: {}", doc.getId());
        } catch (IOException e) {
            log.error("Error indexing item ID {}: {}", doc.getId(), e.getMessage(), e);
        }
    }

    private CatalogItemSearchDocument convertToSearchDocument(CatalogItemEntity itemEntity, CategoryEntity categoryEntity) {
        // CategoryEntity parameter is the potentially updated one from a category event
        CategoryEntity categoryToUse = categoryEntity != null ? categoryEntity : itemEntity.getCategory();

        return CatalogItemSearchDocument.builder()
            .id(itemEntity.getId().toString())
            .sku(itemEntity.getSku())
            .name(itemEntity.getName())
            .description(itemEntity.getDescription())
            .itemType(itemEntity.getItemType())
            .basePrice(itemEntity.getBasePrice() != null ? itemEntity.getBasePrice().doubleValue() : null)
            .active(itemEntity.isActive())
            .createdAt(itemEntity.getCreatedAt())
            .updatedAt(itemEntity.getUpdatedAt())
            .categoryId(categoryToUse.getId().toString())
            .categoryName(categoryToUse.getName())
            .categoryPathKeyword(categoryToUse.getPath())
            .categoryPathHierarchy(categoryToUse.getPath())
            .metadata_flattened(ensureMapForFlattened(itemEntity.getMetadata()))
            .build();
    }


    private void deleteItemFromIndex(String itemId) {
        try {
            DeleteRequest request = new DeleteRequest.Builder()
                    .index(OpenSearchConfig.CATALOG_ITEMS_INDEX_NAME)
                    .id(itemId)
                    .build();
            openSearchClient.delete(request);
            log.info("Deleted item ID: {} from index.", itemId);
        } catch (IOException e) {
            log.error("Error deleting item ID {}: {}", itemId, e.getMessage(), e);
        }
    }

    private void executeBulkRequest(List<BulkOperation> operations, String actionDescription) {
        if (operations.isEmpty()) {
            log.info("No operations to execute for: {}", actionDescription);
            return;
        }
        BulkRequest bulkRequest = new BulkRequest.Builder().operations(operations).build();
        try {
            BulkResponse response = openSearchClient.bulk(bulkRequest);
            if (response.errors()) {
                log.error("Bulk operation for '{}' had errors: {}", actionDescription, response.items().stream()
                    .filter(item -> item.error() != null)
                    .map(item -> "ID " + item.id() + ": " + item.error().reason())
                    .collect(Collectors.joining(", ")));
            } else {
                log.info("Bulk operation for '{}' completed successfully. Processed {} items.", actionDescription, response.items().size());
            }
        } catch (IOException e) {
            log.error("IOException during bulk operation for '{}': {}", actionDescription, e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> ensureMapForFlattened(Object metadata) {
        if (metadata == null) {
            return null; // Or Collections.emptyMap();
        }
        if (metadata instanceof Map) {
            try {
                return (Map<String, Object>) metadata;
            } catch (ClassCastException e) {
                log.warn("Metadata is a Map but not Map<String, Object>, attempting conversion. Metadata: {}", metadata, e);
                 // Try to convert using ObjectMapper if types are complex within the map
                try {
                    return objectMapper.convertValue(metadata, Map.class);
                } catch (IllegalArgumentException iae) {
                    log.error("Could not convert metadata map to Map<String, Object>: {}", metadata, iae);
                    return Map.of("error", "metadata_conversion_failed");
                }
            }
        }
        log.warn("Metadata is not a Map, cannot directly use for flattened field. Type: {}, Value: {}", metadata.getClass().getName(), metadata);
        // Convert to a map with a single key, or handle as error
        return Map.of("value", metadata.toString());
    }


    // Need to configure a KafkaListenerContainerFactory for these listeners if not using default.
    // Example (would go in a KafkaConfig class):
    // @Bean
    // public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
    // ConsumerFactory<String, Object> consumerFactory) {
    //     ConcurrentKafkaListenerContainerFactory<String, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
    //     factory.setConsumerFactory(consumerFactory);
    //     // Add custom error handlers, message converters etc. if needed
    //     return factory;
    // }
    // For events, ensure consumerFactory is configured with JsonDeserializer and trusted packages.
}
