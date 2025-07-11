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
import org.apache.kafka.clients.consumer.ConsumerRecord;
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


import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.List;


import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Testcontainers
@EmbeddedKafka(partitions = 1, topics = {
        "${app.kafka.topic.category-created}",
        "${app.kafka.topic.category-updated}",
        "${app.kafka.topic.category-deleted}"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS) // Ensure broker is reset between test classes
public class CategoryServiceIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> postgresqlContainer =
            new PostgreSQLContainer<>("postgres:15.3-alpine")
                    .withDatabaseName("test_catalog_db_integration") // Different DB for safety
                    .withUsername("testuser")
                    .withPassword("testpassword");

    static {
        System.setProperty("spring.datasource.url", postgresqlContainer.getJdbcUrl());
        System.setProperty("spring.datasource.username", postgresqlContainer.getUsername());
        System.setProperty("spring.datasource.password", postgresqlContainer.getPassword());
    }


    @Autowired
    private CategoryService categoryService;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private CatalogItemRepository catalogItemRepository;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Value("${app.kafka.topic.category-created}")
    private String categoryCreatedTopic;
    @Value("${app.kafka.topic.category-updated}")
    private String categoryUpdatedTopic;
    @Value("${app.kafka.topic.category-deleted}")
    private String categoryDeletedTopic;

    private KafkaMessageListenerContainer<String, CategoryEvent> listenerContainer;
    private BlockingQueue<ConsumerRecord<String, CategoryEvent>> consumerRecords;

    @BeforeEach
    void setUpKafkaListener() {
        consumerRecords = new LinkedBlockingQueue<>();
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("testGroup", "true", embeddedKafkaBroker);
        consumerProps.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        consumerProps.put(JsonDeserializer.VALUE_DEFAULT_TYPE, CategoryEvent.class.getName());


        DefaultKafkaConsumerFactory<String, CategoryEvent> consumerFactory =
                new DefaultKafkaConsumerFactory<>(consumerProps, new org.apache.kafka.common.serialization.StringDeserializer(), new JsonDeserializer<>(CategoryEvent.class, false));

        ContainerProperties containerProps = new ContainerProperties(categoryCreatedTopic, categoryUpdatedTopic, categoryDeletedTopic);
        listenerContainer = new KafkaMessageListenerContainer<>(consumerFactory, containerProps);
        listenerContainer.setupMessageListener((MessageListener<String, CategoryEvent>) consumerRecords::add);
        listenerContainer.start();
        ContainerTestUtils.waitForAssignment(listenerContainer, embeddedKafkaBroker.getPartitionsPerTopic());
    }

    @BeforeEach
    void cleanupDatabase() {
        catalogItemRepository.deleteAll(); // Must delete items before categories due to FK constraints
        categoryRepository.deleteAll();
    }


    @Test
    @Transactional // Use transactional to ensure data is rolled back or manage cleanup
    void createCategory_topLevel_savesAndPublishesEvent() throws InterruptedException {
        CreateCategoryRequest request = CreateCategoryRequest.builder().name("Electronics").type(ItemType.PRODUCT).build();
        CategoryDto createdDto = categoryService.createCategory(request);

        assertNotNull(createdDto.getId());
        assertThat(createdDto.getName()).isEqualTo("Electronics");
        assertThat(createdDto.getPath()).isEqualTo(CategoryService.PATH_SEPARATOR + createdDto.getId().toString() + CategoryService.PATH_SEPARATOR);

        CategoryEntity savedEntity = categoryRepository.findById(createdDto.getId()).orElseThrow();
        assertThat(savedEntity.getName()).isEqualTo("Electronics");

        ConsumerRecord<String, CategoryEvent> record = consumerRecords.poll(5, TimeUnit.SECONDS);
        assertNotNull(record, "Kafka message not received for category.created");
        assertEquals(categoryCreatedTopic, record.topic());
        CategoryEvent event = record.value();
        assertThat(event.getCategoryId()).isEqualTo(createdDto.getId());
        assertThat(event.getName()).isEqualTo("Electronics");
        assertThat(event.getEventType()).isEqualTo("category.created");
    }

    @Test
    @Transactional
    void createCategory_withParent_correctlySetsPathAndParent() throws InterruptedException {
        CreateCategoryRequest parentRequest = CreateCategoryRequest.builder().name("Root").type(ItemType.PRODUCT).build();
        CategoryDto parentDto = categoryService.createCategory(parentRequest);
        consumerRecords.poll(1, TimeUnit.SECONDS); // Consume parent event

        CreateCategoryRequest childRequest = CreateCategoryRequest.builder().name("Child").type(ItemType.PRODUCT).parentId(parentDto.getId()).build();
        CategoryDto childDto = categoryService.createCategory(childRequest);

        assertNotNull(childDto.getId());
        assertThat(childDto.getParentId()).isEqualTo(parentDto.getId());
        String expectedPath = parentDto.getPath() + childDto.getId().toString() + CategoryService.PATH_SEPARATOR;
        assertThat(childDto.getPath()).isEqualTo(expectedPath);

        CategoryEntity childEntity = categoryRepository.findById(childDto.getId()).orElseThrow();
        assertThat(childEntity.getParentCategory().getId()).isEqualTo(parentDto.getId());
        assertThat(childEntity.getPath()).isEqualTo(expectedPath);

        ConsumerRecord<String, CategoryEvent> record = consumerRecords.poll(5, TimeUnit.SECONDS);
        assertNotNull(record, "Kafka message not received for child category.created");
        CategoryEvent event = record.value();
        assertThat(event.getCategoryId()).isEqualTo(childDto.getId());
        assertThat(event.getParentId()).isEqualTo(parentDto.getId());
    }


    @Test
    @Transactional
    void updateCategory_renameAndReparent_updatesAndPublishesEvent() throws InterruptedException {
        // Create initial categories
        CategoryDto parent1 = categoryService.createCategory(CreateCategoryRequest.builder().name("Parent1").type(ItemType.PRODUCT).build());
        consumerRecords.poll(1, TimeUnit.SECONDS);
        CategoryDto parent2 = categoryService.createCategory(CreateCategoryRequest.builder().name("Parent2").type(ItemType.PRODUCT).build());
        consumerRecords.poll(1, TimeUnit.SECONDS);
        CategoryDto categoryToUpdateDto = categoryService.createCategory(CreateCategoryRequest.builder().name("Old Name").type(ItemType.PRODUCT).parentId(parent1.getId()).build());
        consumerRecords.poll(1, TimeUnit.SECONDS);

        // Update: new name, new parent
        CreateCategoryRequest updateRequest = CreateCategoryRequest.builder()
                .name("New Name")
                .type(ItemType.PRODUCT)
                .parentId(parent2.getId())
                .build();

        CategoryDto updatedDto = categoryService.updateCategory(categoryToUpdateDto.getId(), updateRequest);

        assertThat(updatedDto.getName()).isEqualTo("New Name");
        assertThat(updatedDto.getParentId()).isEqualTo(parent2.getId());
        String expectedPath = parent2.getPath() + updatedDto.getId().toString() + CategoryService.PATH_SEPARATOR;
        assertThat(updatedDto.getPath()).isEqualTo(expectedPath);

        ConsumerRecord<String, CategoryEvent> record = consumerRecords.poll(5, TimeUnit.SECONDS);
        assertNotNull(record, "Kafka message not received for category.updated");
        assertEquals(categoryUpdatedTopic, record.topic());
        CategoryEvent event = record.value();
        assertThat(event.getCategoryId()).isEqualTo(updatedDto.getId());
        assertThat(event.getName()).isEqualTo("New Name");
        assertThat(event.getParentId()).isEqualTo(parent2.getId());
        assertThat(event.getPath()).isEqualTo(expectedPath);
        assertThat(event.getEventType()).isEqualTo("category.updated");
    }

    @Test
    @Transactional
    void updateCategory_reparentWithDescendants_updatesAllPaths() throws InterruptedException {
        CategoryDto root = categoryService.createCategory(CreateCategoryRequest.builder().name("Root").type(ItemType.PRODUCT).build());
        consumerRecords.poll(1, TimeUnit.SECONDS);
        CategoryDto child = categoryService.createCategory(CreateCategoryRequest.builder().name("Child").type(ItemType.PRODUCT).parentId(root.getId()).build());
        consumerRecords.poll(1, TimeUnit.SECONDS); // This is categoryToMove
        CategoryDto grandchild = categoryService.createCategory(CreateCategoryRequest.builder().name("Grandchild").type(ItemType.PRODUCT).parentId(child.getId()).build());
        consumerRecords.poll(1, TimeUnit.SECONDS);
        CategoryDto newParent = categoryService.createCategory(CreateCategoryRequest.builder().name("New Root Parent").type(ItemType.PRODUCT).build());
        consumerRecords.poll(1, TimeUnit.SECONDS);

        // Reparent 'Child' category under 'New Root Parent'
        CreateCategoryRequest updateRequest = CreateCategoryRequest.builder()
            .name("Child") // Name stays same
            .type(ItemType.PRODUCT)
            .parentId(newParent.getId())
            .build();

        categoryService.updateCategory(child.getId(), updateRequest);
        ConsumerRecord<String, CategoryEvent> childUpdateRecord = consumerRecords.poll(5, TimeUnit.SECONDS); // For child update
        assertNotNull(childUpdateRecord);

        // Check paths
        CategoryEntity updatedChild = categoryRepository.findById(child.getId()).orElseThrow();
        String expectedChildPath = newParent.getPath() + child.getId().toString() + CategoryService.PATH_SEPARATOR;
        assertThat(updatedChild.getPath()).isEqualTo(expectedChildPath);

        CategoryEntity updatedGrandchild = categoryRepository.findById(grandchild.getId()).orElseThrow();
        String expectedGrandchildPath = expectedChildPath + grandchild.getId().toString() + CategoryService.PATH_SEPARATOR;
        assertThat(updatedGrandchild.getPath()).isEqualTo(expectedGrandchildPath);
    }


    @Test
    @Transactional
    void deleteCategory_emptyCategory_deletesAndPublishesEvent() throws InterruptedException {
        CreateCategoryRequest request = CreateCategoryRequest.builder().name("ToDelete").type(ItemType.PRODUCT).build();
        CategoryDto dto = categoryService.createCategory(request);
        consumerRecords.poll(1, TimeUnit.SECONDS); // Consume create event

        categoryService.deleteCategory(dto.getId());

        assertThat(categoryRepository.findById(dto.getId())).isEmpty();
        ConsumerRecord<String, CategoryEvent> record = consumerRecords.poll(5, TimeUnit.SECONDS);
        assertNotNull(record, "Kafka message not received for category.deleted");
        assertEquals(categoryDeletedTopic, record.topic());
        CategoryEvent event = record.value();
        assertThat(event.getCategoryId()).isEqualTo(dto.getId());
        assertThat(event.getEventType()).isEqualTo("category.deleted");
    }

    @Test
    @Transactional
    void deleteCategory_withItems_throwsException() {
        CategoryDto catDto = categoryService.createCategory(CreateCategoryRequest.builder().name("CatWithItems").type(ItemType.PRODUCT).build());
        consumerRecords.clear(); // Ignore create event

        // Create an item in this category (directly via repository for test setup simplicity)
        catalogItemRepository.save(com.mysillydreams.catalogservice.domain.model.CatalogItemEntity.builder()
            .category(categoryRepository.findById(catDto.getId()).get())
            .sku("ITEMINCAT").name("Item").itemType(ItemType.PRODUCT).basePrice(BigDecimal.ONE).build());

        assertThrows(InvalidRequestException.class, () -> categoryService.deleteCategory(catDto.getId()));
    }

    // ... other tests for exceptions (ResourceNotFound, DuplicateResource, InvalidRequest for cycles etc.)
}
