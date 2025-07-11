package com.mysillydreams.catalogservice.search;

import com.mysillydreams.catalogservice.config.OpenSearchConfig;
import com.mysillydreams.catalogservice.domain.model.CatalogItemEntity;
import com.mysillydreams.catalogservice.domain.model.CategoryEntity;
import com.mysillydreams.catalogservice.domain.model.ItemType;
import com.mysillydreams.catalogservice.domain.repository.CatalogItemRepository;
import com.mysillydreams.catalogservice.domain.repository.CategoryRepository;
import com.mysillydreams.catalogservice.kafka.event.CatalogItemEvent;
import com.mysillydreams.catalogservice.kafka.event.CategoryEvent;
import com.mysillydreams.catalogservice.kafka.producer.KafkaProducerService;
import com.mysillydreams.catalogservice.service.search.CatalogItemSearchDocument;
import com.mysillydreams.catalogservice.service.search.OpenSearchIndexInitializer;
import com.mysillydreams.catalogservice.service.search.SearchService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opensearch.client.java.OpenSearchClient;
import org.opensearch.client.opensearch.indices.DeleteIndexRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;


import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;


@EmbeddedKafka(partitions = 1, topics = {
        "${app.kafka.topic.item-created}",
        "${app.kafka.topic.item-updated}",
        "${app.kafka.topic.item-deleted}",
        "${app.kafka.topic.category-updated}"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS) // Ensure broker & contexts are reset
public class CatalogSearchIntegrationTest extends AbstractOpenSearchIntegrationTest {

    @Autowired private OpenSearchClient openSearchClient;
    @Autowired private SearchService searchService;
    @SpyBean private CatalogItemIndexerService indexerService; // Spy to verify method calls
    @Autowired private KafkaProducerService kafkaProducerService; // To send events

    @Autowired private CategoryRepository categoryRepository;
    @Autowired private CatalogItemRepository itemRepository;
    @Autowired private OpenSearchIndexInitializer indexInitializer; // To ensure index is created

    @Value("${app.kafka.topic.item-created}") private String itemCreatedTopic;
    @Value("${app.kafka.topic.item-updated}") private String itemUpdatedTopic;
    @Value("${app.kafka.topic.item-deleted}") private String itemDeletedTopic;
    @Value("${app.kafka.topic.category-updated}") private String categoryUpdatedTopic;

    private CategoryEntity catElectronics;
    private CategoryEntity catLaptops;

    @BeforeEach
    void setUpTestDataAndIndex() throws IOException {
        // Clean up OpenSearch index before each test
        try {
            if (openSearchClient.indices().exists(e -> e.index(OpenSearchConfig.CATALOG_ITEMS_INDEX_NAME)).value()) {
                openSearchClient.indices().delete(new DeleteIndexRequest.Builder().index(OpenSearchConfig.CATALOG_ITEMS_INDEX_NAME).build());
            }
        } catch (Exception e) { /* ignore if index doesn't exist */ }
        // Re-initialize index (mapping)
        indexInitializer.initializeIndex();

        // Clean up DB
        itemRepository.deleteAll();
        categoryRepository.deleteAll();

        // Create categories
        catElectronics = categoryRepository.save(CategoryEntity.builder().name("Electronics").type(ItemType.PRODUCT).path("/electronics/").build());
        catElectronics.setPath(CategoryService.PATH_SEPARATOR + catElectronics.getId() + CategoryService.PATH_SEPARATOR); // Manually set path like service does
        catElectronics = categoryRepository.save(catElectronics);


        catLaptops = categoryRepository.save(CategoryEntity.builder().name("Laptops").parentCategory(catElectronics).type(ItemType.PRODUCT).build());
        catLaptops.setPath(catElectronics.getPath() + catLaptops.getId() + CategoryService.PATH_SEPARATOR);
        catLaptops = categoryRepository.save(catLaptops);
    }

    @Test
    void testItemCreatedEvent_IndexesDocument_AndIsSearchable() {
        UUID itemId = UUID.randomUUID();
        CatalogItemEvent createEvent = CatalogItemEvent.builder()
                .eventType("catalog.item.created")
                .itemId(itemId).sku("LAPTOP001").name("Super Fast Laptop")
                .description("A very fast gaming laptop with latest GPU.")
                .categoryId(catLaptops.getId()) // Belongs to Laptops
                .itemType(ItemType.PRODUCT).basePrice(new BigDecimal("1999.99"))
                .active(true).timestamp(Instant.now())
                .build();

        kafkaProducerService.sendMessage(itemCreatedTopic, itemId.toString(), createEvent);

        // Verify indexerService.onItemCreated was called (due to Kafka listener)
        // Awaitility can be used to wait for async processing
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
            verify(indexerService, timeout(5000).times(1)).onItemCreated(any(CatalogItemEvent.class))
        );

        // Allow some time for OpenSearch to index
        await().atMost(5, TimeUnit.SECONDS).until(() -> {
            Page<CatalogItemSearchDocument> results = searchService.searchItems("Super Fast Laptop", null, null, null, null, null, PageRequest.of(0, 10));
            return results.getTotalElements() == 1;
        });

        Page<CatalogItemSearchDocument> results = searchService.searchItems("gaming laptop", null, null, ItemType.PRODUCT, null, null, PageRequest.of(0, 10));
        assertThat(results.getTotalElements()).isEqualTo(1);
        CatalogItemSearchDocument doc = results.getContent().get(0);
        assertThat(doc.getId()).isEqualTo(itemId.toString());
        assertThat(doc.getName()).isEqualTo("Super Fast Laptop");
        assertThat(doc.getCategoryName()).isEqualTo(catLaptops.getName());
        assertThat(doc.getCategoryPathKeyword()).isEqualTo(catLaptops.getPath());
    }

    @Test
    void testItemUpdatedEvent_UpdatesDocumentInIndex() {
        UUID itemId = UUID.randomUUID();
        // Initial creation
        CatalogItemEvent createEvent = CatalogItemEvent.builder()
            .eventType("catalog.item.created").itemId(itemId).sku("UPDATE-TEST-01").name("Original Name")
            .categoryId(catElectronics.getId()).itemType(ItemType.PRODUCT).basePrice(BigDecimal.TEN).active(true).timestamp(Instant.now())
            .build();
        kafkaProducerService.sendMessage(itemCreatedTopic, itemId.toString(), createEvent);
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> verify(indexerService, timeout(5000).times(1)).onItemCreated(any(CatalogItemEvent.class)));


        // Update event
        CatalogItemEvent updateEvent = CatalogItemEvent.builder()
            .eventType("catalog.item.updated").itemId(itemId).sku("UPDATE-TEST-01").name("Updated Super Name") // Name changed
            .description("Now with more features!").categoryId(catElectronics.getId()) // Category same
            .itemType(ItemType.PRODUCT).basePrice(new BigDecimal("12.00")).active(true).timestamp(Instant.now())
            .build();
        kafkaProducerService.sendMessage(itemUpdatedTopic, itemId.toString(), updateEvent);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> verify(indexerService, timeout(5000).times(1)).onItemUpdated(any(CatalogItemEvent.class)));

        await().atMost(5, TimeUnit.SECONDS).until(() -> {
            Page<CatalogItemSearchDocument> results = searchService.searchItems("Updated Super Name", null, null, null, null, null, PageRequest.of(0, 10));
            return results.getTotalElements() == 1 && results.getContent().get(0).getName().equals("Updated Super Name");
        });
         Page<CatalogItemSearchDocument> finalResults = searchService.searchItems("Updated Super Name", null, null, null, null, null, PageRequest.of(0, 10));
         assertThat(finalResults.getContent().get(0).getDescription()).isEqualTo("Now with more features!");
    }

    @Test
    void testItemDeletedEvent_RemovesDocumentFromIndex() {
         UUID itemId = UUID.randomUUID();
        CatalogItemEvent createEvent = CatalogItemEvent.builder()
            .eventType("catalog.item.created").itemId(itemId).sku("DELETE-TEST-01").name("To Be Deleted Item")
            .categoryId(catElectronics.getId()).itemType(ItemType.PRODUCT).basePrice(BigDecimal.ONE).active(true).timestamp(Instant.now())
            .build();
        kafkaProducerService.sendMessage(itemCreatedTopic, itemId.toString(), createEvent);
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> verify(indexerService, timeout(5000).times(1)).onItemCreated(any(CatalogItemEvent.class)));

        // Ensure it's searchable first
        await().atMost(5, TimeUnit.SECONDS).until(() -> searchService.searchItems("To Be Deleted Item", null, null, null, null, null, PageRequest.of(0,10)).getTotalElements() == 1);

        // Delete event
        CatalogItemEvent deleteEvent = CatalogItemEvent.builder().eventType("catalog.item.deleted").itemId(itemId).categoryId(catElectronics.getId()).sku("DELETE-TEST-01").build(); // Other fields might not be needed for delete
        kafkaProducerService.sendMessage(itemDeletedTopic, itemId.toString(), deleteEvent);
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> verify(indexerService, timeout(5000).times(1)).onItemDeleted(any(CatalogItemEvent.class)));

        // Verify it's no longer searchable
         await().atMost(5, TimeUnit.SECONDS).until(() -> searchService.searchItems("To Be Deleted Item", null, null, null, null, null, PageRequest.of(0,10)).getTotalElements() == 0);
    }

    @Test
    void testCategoryUpdatedEvent_ReindexesAssociatedItems() {
        // Create two items in catLaptops
        CatalogItemEntity item1Entity = itemRepository.save(CatalogItemEntity.builder().category(catLaptops).sku("LAP1").name("Laptop Alpha").itemType(ItemType.PRODUCT).basePrice(BigDecimal.TEN).active(true).build());
        CatalogItemEntity item2Entity = itemRepository.save(CatalogItemEntity.builder().category(catLaptops).sku("LAP2").name("Laptop Beta").itemType(ItemType.PRODUCT).basePrice(BigDecimal.TEN).active(true).build());

        // Manually index them for test setup (or send create events and wait)
        indexerService.onItemCreated(buildEventFromEntity(item1Entity, "catalog.item.created"));
        indexerService.onItemCreated(buildEventFromEntity(item2Entity, "catalog.item.created"));
        await().atMost(5, TimeUnit.SECONDS).until(() -> searchService.searchItems("Laptop", null, catLaptops.getId(), null, null, null, PageRequest.of(0,10)).getTotalElements() == 2);


        // Update category catLaptops name
        CategoryEvent categoryUpdateEvent = CategoryEvent.builder()
            .eventType("category.updated")
            .categoryId(catLaptops.getId())
            .name("Gaming Laptops Super") // New name
            .parentId(catLaptops.getParentCategory().getId())
            .type(catLaptops.getType())
            .path(catLaptops.getPath()) // Path not changing in this test
            .timestamp(Instant.now())
            .build();

        // Update in DB for consistency if indexer re-fetches category (current indexer uses event data for category)
        catLaptops.setName("Gaming Laptops Super");
        categoryRepository.save(catLaptops);

        kafkaProducerService.sendMessage(categoryUpdatedTopic, catLaptops.getId().toString(), categoryUpdateEvent);

        // Verify indexerService.onCategoryUpdated was called
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
            verify(indexerService, timeout(5000).times(1)).onCategoryUpdated(any(CategoryEvent.class))
        );

        // Verify items are now searchable with new category name
        // This requires the CatalogItemSearchDocument to include categoryName and for it to be searchable.
        // Our SearchService query includes categoryName.
        await().atMost(10, TimeUnit.SECONDS).until(() -> {
            Page<CatalogItemSearchDocument> results = searchService.searchItems("Gaming Laptops Super", null, null, null, null, null, PageRequest.of(0, 10));
            if (results.getTotalElements() < 2) return false; // Wait for both items to be re-indexed

            // Check if items now reflect the new category name in their search document
            return results.getContent().stream().allMatch(doc -> doc.getCategoryName().equals("Gaming Laptops Super"));
        });
         Page<CatalogItemSearchDocument> finalResults = searchService.searchItems(null, null, catLaptops.getId(), null, null, null, PageRequest.of(0,10));
         assertThat(finalResults.getContent()).allMatch(doc -> doc.getCategoryName().equals("Gaming Laptops Super"));
    }

    // Helper to build CatalogItemEvent from CatalogItemEntity for test setup
    private CatalogItemEvent buildEventFromEntity(CatalogItemEntity entity, String eventType) {
        return CatalogItemEvent.builder()
            .eventType(eventType)
            .itemId(entity.getId())
            .sku(entity.getSku())
            .name(entity.getName())
            .description(entity.getDescription())
            .categoryId(entity.getCategory().getId())
            .itemType(entity.getItemType())
            .basePrice(entity.getBasePrice())
            .active(entity.isActive())
            .timestamp(entity.getUpdatedAt() != null ? entity.getUpdatedAt() : entity.getCreatedAt())
            .metadata(entity.getMetadata())
            .build();
    }
}
