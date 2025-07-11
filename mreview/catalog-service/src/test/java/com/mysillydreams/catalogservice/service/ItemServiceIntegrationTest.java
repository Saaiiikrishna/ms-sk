package com.mysillydreams.catalogservice.service;

import com.mysillydreams.catalogservice.domain.model.*;
import com.mysillydreams.catalogservice.domain.repository.*;
import com.mysillydreams.catalogservice.dto.CatalogItemDto;
import com.mysillydreams.catalogservice.dto.CreateCatalogItemRequest;
import com.mysillydreams.catalogservice.exception.DuplicateResourceException;
import com.mysillydreams.catalogservice.exception.InvalidRequestException;
import com.mysillydreams.catalogservice.kafka.event.CatalogItemEvent;
import com.mysillydreams.catalogservice.kafka.event.PriceUpdatedEvent;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.transaction.annotation.Transactional;


import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.Optional;


import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Testcontainers
@EmbeddedKafka(partitions = 1, topics = {
        "${app.kafka.topic.item-created}",
        "${app.kafka.topic.item-updated}",
        "${app.kafka.topic.item-deleted}",
        "${app.kafka.topic.price-updated}"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class ItemServiceIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> postgresqlContainer =
            new PostgreSQLContainer<>("postgres:15.3-alpine")
                    .withDatabaseName("test_catalog_db_item_int")
                    .withUsername("testuser")
                    .withPassword("testpassword");

    static {
        System.setProperty("spring.datasource.url", postgresqlContainer.getJdbcUrl());
        System.setProperty("spring.datasource.username", postgresqlContainer.getUsername());
        System.setProperty("spring.datasource.password", postgresqlContainer.getPassword());
    }

    @Autowired private ItemService itemService;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private CatalogItemRepository itemRepository;
    @Autowired private PriceHistoryRepository priceHistoryRepository;
    @Autowired private StockLevelRepository stockLevelRepository;
    @Autowired private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Value("${app.kafka.topic.item-created}") private String itemCreatedTopic;
    @Value("${app.kafka.topic.item-updated}") private String itemUpdatedTopic;
    @Value("${app.kafka.topic.item-deleted}") private String itemDeletedTopic;
    @Value("${app.kafka.topic.price-updated}") private String priceUpdatedTopic;

    private KafkaMessageListenerContainer<String, Object> listenerContainer; // Object for multiple event types
    private BlockingQueue<ConsumerRecord<String, Object>> consumerRecords;

    private CategoryEntity productCategory;
    private CategoryEntity serviceCategory;

    @BeforeEach
    void setUpKafkaListenerAndCategories() {
        consumerRecords = new LinkedBlockingQueue<>();
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("itemTestGroup", "true", embeddedKafkaBroker);
        consumerProps.put(JsonDeserializer.TRUSTED_PACKAGES, "com.mysillydreams.catalogservice.kafka.event");
        // Configure for specific event types if possible, or use a generic approach
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);


        DefaultKafkaConsumerFactory<String, Object> consumerFactory = new DefaultKafkaConsumerFactory<>(consumerProps);

        ContainerProperties containerProps = new ContainerProperties(itemCreatedTopic, itemUpdatedTopic, itemDeletedTopic, priceUpdatedTopic);
        listenerContainer = new KafkaMessageListenerContainer<>(consumerFactory, containerProps);
        listenerContainer.setupMessageListener((MessageListener<String, Object>) consumerRecords::add); // Add all to one queue
        listenerContainer.start();
        ContainerTestUtils.waitForAssignment(listenerContainer, embeddedKafkaBroker.getPartitionsPerTopic());

        // Clean up DB before each test
        stockLevelRepository.deleteAll();
        priceHistoryRepository.deleteAll();
        itemRepository.deleteAll();
        categoryRepository.deleteAll();

        productCategory = categoryRepository.save(CategoryEntity.builder().name("Electronics").type(ItemType.PRODUCT).path("/electronics/").build());
        serviceCategory = categoryRepository.save(CategoryEntity.builder().name("Support").type(ItemType.SERVICE).path("/support/").build());
    }

    @AfterEach
    void tearDownKafkaListener() {
        if (listenerContainer != null) {
            listenerContainer.stop();
        }
    }

    @Test
    @Transactional
    void createItem_product_savesAndPublishesEvent_createsStockAndPriceHistory() throws InterruptedException {
        CreateCatalogItemRequest request = CreateCatalogItemRequest.builder()
                .categoryId(productCategory.getId())
                .sku("LAPTOP001")
                .name("Super Laptop")
                .itemType(ItemType.PRODUCT)
                .basePrice(new BigDecimal("1500.00"))
                .active(true)
                .build();

        CatalogItemDto createdDto = itemService.createItem(request);

        assertNotNull(createdDto.getId());
        assertEquals("LAPTOP001", createdDto.getSku());

        // Verify DB state
        CatalogItemEntity itemEntity = itemRepository.findById(createdDto.getId()).orElseThrow();
        assertEquals("LAPTOP001", itemEntity.getSku());

        PriceHistoryEntity priceHistory = priceHistoryRepository.findByCatalogItemIdOrderByEffectiveFromDesc(createdDto.getId()).get(0);
        assertThat(priceHistory.getPrice()).isEqualByComparingTo("1500.00");

        StockLevelEntity stockLevel = stockLevelRepository.findByCatalogItemId(createdDto.getId()).orElseThrow();
        assertEquals(0, stockLevel.getQuantityOnHand()); // Initial stock

        // Verify Kafka event
        ConsumerRecord<String, Object> record = consumerRecords.poll(5, TimeUnit.SECONDS);
        assertNotNull(record, "Kafka message not received for item.created");
        assertEquals(itemCreatedTopic, record.topic());
        assertThat(record.value()).isInstanceOf(CatalogItemEvent.class);
        CatalogItemEvent event = (CatalogItemEvent) record.value();
        assertEquals(createdDto.getId(), event.getItemId());
        assertEquals("catalog.item.created", event.getEventType());
    }

    @Test
    @Transactional
    void createItem_service_savesAndPublishesEvent_noStockRecord() throws InterruptedException {
        CreateCatalogItemRequest request = CreateCatalogItemRequest.builder()
                .categoryId(serviceCategory.getId())
                .sku("SUPPORT01")
                .name("Basic Support Package")
                .itemType(ItemType.SERVICE)
                .basePrice(new BigDecimal("99.99"))
                .build();

        CatalogItemDto createdDto = itemService.createItem(request);
        assertNotNull(createdDto.getId());

        assertThat(stockLevelRepository.findByCatalogItemId(createdDto.getId())).isEmpty(); // No stock for service

        ConsumerRecord<String, Object> record = consumerRecords.poll(5, TimeUnit.SECONDS);
        assertNotNull(record, "Kafka message not received for item.created (service)");
        assertThat(record.value()).isInstanceOf(CatalogItemEvent.class);
    }


    @Test
    @Transactional
    void updateItem_priceChange_publishesItemUpdateAndPriceUpdateEvents() throws InterruptedException {
        // Create initial item
        CreateCatalogItemRequest createRequest = CreateCatalogItemRequest.builder()
            .categoryId(productCategory.getId()).sku("ITEM-PCHANGE").name("Item Original Price")
            .itemType(ItemType.PRODUCT).basePrice(new BigDecimal("100.00")).build();
        CatalogItemDto itemDto = itemService.createItem(createRequest);
        consumerRecords.poll(1, TimeUnit.SECONDS); // Consume create event

        // Update price
        CreateCatalogItemRequest updateRequest = CreateCatalogItemRequest.builder()
            .categoryId(productCategory.getId()).sku("ITEM-PCHANGE").name("Item Original Price") // name not changing
            .itemType(ItemType.PRODUCT).basePrice(new BigDecimal("120.00")).build(); // price changing

        itemService.updateItem(itemDto.getId(), updateRequest);

        // Expect two events: PriceUpdatedEvent and CatalogItemEvent (for general update)
        ConsumerRecord<String, Object> priceUpdateRecord = consumerRecords.poll(5, TimeUnit.SECONDS);
        assertNotNull(priceUpdateRecord, "PriceUpdatedEvent not received");
        assertEquals(priceUpdatedTopic, priceUpdateRecord.topic());
        assertThat(priceUpdateRecord.value()).isInstanceOf(PriceUpdatedEvent.class);
        PriceUpdatedEvent priceEvent = (PriceUpdatedEvent) priceUpdateRecord.value();
        assertThat(priceEvent.getOldPrice()).isEqualByComparingTo("100.00");
        assertThat(priceEvent.getNewPrice()).isEqualByComparingTo("120.00");

        ConsumerRecord<String, Object> itemUpdateRecord = consumerRecords.poll(5, TimeUnit.SECONDS);
        assertNotNull(itemUpdateRecord, "CatalogItemEvent (update) not received");
        assertEquals(itemUpdatedTopic, itemUpdateRecord.topic());
        assertThat(itemUpdateRecord.value()).isInstanceOf(CatalogItemEvent.class);
        CatalogItemEvent itemEvent = (CatalogItemEvent) itemUpdateRecord.value();
        assertThat(itemEvent.getBasePrice()).isEqualByComparingTo("120.00");
        assertThat(itemEvent.getEventType()).isEqualTo("catalog.item.updated");

        // Verify Price History
        List<PriceHistoryEntity> history = priceHistoryRepository.findByCatalogItemIdOrderByEffectiveFromDesc(itemDto.getId());
        assertThat(history).hasSize(2); // Initial price + updated price
        assertThat(history.get(0).getPrice()).isEqualByComparingTo("120.00");
        assertThat(history.get(1).getPrice()).isEqualByComparingTo("100.00");
    }

    @Test
    @Transactional
    void updateItemPrice_standaloneMethod_publishesEventsCorrectly() throws InterruptedException {
        CreateCatalogItemRequest createRequest = CreateCatalogItemRequest.builder()
            .categoryId(productCategory.getId()).sku("ITEM-PRICE-UPDATE-STANDALONE").name("Standalone Price Update Item")
            .itemType(ItemType.PRODUCT).basePrice(new BigDecimal("200.00")).build();
        CatalogItemDto itemDto = itemService.createItem(createRequest);
        consumerRecords.poll(1, TimeUnit.SECONDS); // Consume create event

        BigDecimal newPrice = new BigDecimal("250.00");
        itemService.updateItemPrice(itemDto.getId(), newPrice);

        ConsumerRecord<String, Object> priceUpdateRecord = consumerRecords.poll(5, TimeUnit.SECONDS);
        assertNotNull(priceUpdateRecord);
        assertEquals(priceUpdatedTopic, priceUpdateRecord.topic());
        PriceUpdatedEvent priceEvent = (PriceUpdatedEvent) priceUpdateRecord.value();
        assertThat(priceEvent.getOldPrice()).isEqualByComparingTo("200.00");
        assertThat(priceEvent.getNewPrice()).isEqualByComparingTo("250.00");

        ConsumerRecord<String, Object> itemUpdateRecord = consumerRecords.poll(5, TimeUnit.SECONDS);
        assertNotNull(itemUpdateRecord);
        assertEquals(itemUpdatedTopic, itemUpdateRecord.topic());
        CatalogItemEvent itemEvent = (CatalogItemEvent) itemUpdateRecord.value();
        assertThat(itemEvent.getBasePrice()).isEqualByComparingTo("250.00");
    }


    @Test
    @Transactional
    void deleteItem_product_deletesAndPublishesEvent() throws InterruptedException {
        CreateCatalogItemRequest createRequest = CreateCatalogItemRequest.builder()
            .categoryId(productCategory.getId()).sku("ITEM-TO-DELETE").name("To Be Deleted")
            .itemType(ItemType.PRODUCT).basePrice(new BigDecimal("10.00")).build();
        CatalogItemDto itemDto = itemService.createItem(createRequest);
        consumerRecords.poll(1, TimeUnit.SECONDS); // Consume create event

        itemService.deleteItem(itemDto.getId());

        assertThat(itemRepository.findById(itemDto.getId())).isEmpty();
        assertThat(stockLevelRepository.findByCatalogItemId(itemDto.getId())).isEmpty(); // Stock should be deleted

        ConsumerRecord<String, Object> record = consumerRecords.poll(5, TimeUnit.SECONDS);
        assertNotNull(record, "Kafka message not received for item.deleted");
        assertEquals(itemDeletedTopic, record.topic());
        assertThat(record.value()).isInstanceOf(CatalogItemEvent.class);
        CatalogItemEvent event = (CatalogItemEvent) record.value();
        assertEquals(itemDto.getId(), event.getItemId());
        assertEquals("catalog.item.deleted", event.getEventType());
    }

    @Test
    @Transactional
    void createItem_itemTypeMismatchWithCategory_throwsInvalidRequestException() {
        CreateCatalogItemRequest request = CreateCatalogItemRequest.builder()
                .categoryId(productCategory.getId()) // Type PRODUCT
                .sku("MISMATCH-SKU")
                .name("Service Item")
                .itemType(ItemType.SERVICE) // Mismatch
                .basePrice(new BigDecimal("50.00"))
                .build();

        Exception ex = assertThrows(InvalidRequestException.class, () -> itemService.createItem(request));
        assertThat(ex.getMessage()).contains("Item type 'SERVICE' does not match category type 'PRODUCT'.");
    }

    // ... other tests for exceptions, edge cases etc.
}
