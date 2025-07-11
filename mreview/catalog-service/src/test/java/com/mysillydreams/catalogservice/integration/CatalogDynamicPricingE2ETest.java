package com.mysillydreams.catalogservice.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mysillydreams.catalogservice.domain.model.CatalogItemEntity;
import com.mysillydreams.catalogservice.domain.model.CategoryEntity;
import com.mysillydreams.catalogservice.domain.model.ItemType;
import com.mysillydreams.catalogservice.domain.repository.CatalogItemRepository;
import com.mysillydreams.catalogservice.domain.repository.CategoryRepository;
import com.mysillydreams.catalogservice.dto.PriceDetailDto;
import com.mysillydreams.catalogservice.dto.PriceUpdatedEventDto;
import com.mysillydreams.catalogservice.service.search.CatalogItemSearchDocument;
import com.mysillydreams.catalogservice.config.OpenSearchConfig; // For index name

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestClient;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.client.indices.CreateIndexRequest;
import org.opensearch.client.indices.GetIndexRequest;
import org.opensearch.client.java.OpenSearchClient;
import org.opensearch.client.java.opensearch.core.GetResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@EmbeddedKafka(
        partitions = 1,
        brokerProperties = { "listeners=PLAINTEXT://localhost:9092", "port=9092" }, // Port for embedded Kafka
        // Topics that DynamicPriceUpdateListener listens to, and DLT
        topics = { "${app.kafka.topic.price-updated-from-engine}", "${app.kafka.topic.price-update-listener-dlt}" }
)
@ActiveProfiles("test") // Ensure test properties are loaded (e.g., for Kafka topic names)
public class CatalogDynamicPricingE2ETest {

    private static final String POSTGRES_IMAGE = "postgres:14-alpine";
    private static final String OPENSEARCH_IMAGE = "opensearchproject/opensearch:2.11.0"; // Use a version compatible with client

    @Container
    private static final PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>(POSTGRES_IMAGE)
            .withDatabaseName("catalog_db_test")
            .withUsername("testuser")
            .withPassword("testpass");

    @Container
    private static final GenericContainer<?> openSearchContainer = new GenericContainer<>(DockerImageName.parse(OPENSEARCH_IMAGE))
            .withExposedPorts(9200, 9600) // 9200 for REST, 9600 for performance analyzer
            .withEnv("discovery.type", "single-node")
            .withEnv("OPENSEARCH_JAVA_OPTS", "-Xms512m -Xmx512m") // Adjust memory as needed
            .withEnv("DISABLE_SECURITY_PLUGIN", "true") // For easier test setup
            .waitingFor(Wait.forHttp("/_cluster/health").forPort(9200).forStatusCode(200).withStartupTimeout(Duration.ofMinutes(2)));
            // .withNetwork(Network.SHARED) // If other containers need to see it by hostname

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgresContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgresContainer::getUsername);
        registry.add("spring.datasource.password", postgresContainer::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create"); // Let Hibernate create schema for tests

        registry.add("opensearch.host", openSearchContainer::getHost);
        registry.add("opensearch.port", openSearchContainer::getFirstMappedPort); // Port 9200
        registry.add("opensearch.scheme", () -> "http");
        // For spring.kafka.bootstrap-servers, EmbeddedKafka handles this if 'localhost:9092' is used by default
        // or if spring.kafka.bootstrap-servers property is overridden by EmbeddedKafka.
        // Explicitly setting it for clarity or if default doesn't align:
        registry.add("spring.kafka.bootstrap-servers", () -> System.getProperty("spring.embedded.kafka.brokers"));

    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private CatalogItemRepository catalogItemRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private OpenSearchClient openSearchClient; // The new Java client

    @Autowired
    private ObjectMapper objectMapper;

    @LocalServerPort
    private int serverPort;

    @org.springframework.beans.factory.annotation.Value("${app.kafka.topic.price-updated-from-engine}")
    private String priceUpdatedFromEngineTopic; // Injected from properties

    // Placeholder for category to ensure items can be created
    private CategoryEntity testCategory;

    // Re-use OpenSearchIndexInitializer to create index with proper mappings
    @Autowired
    private com.mysillydreams.catalogservice.service.search.OpenSearchIndexInitializer openSearchIndexInitializer;


    @BeforeAll
    static void beforeAll() {
        // postgresContainer.start(); // JUnit Jupiter extension handles this
        // openSearchContainer.start(); // JUnit Jupiter extension handles this
        // Increase Awaitility default timeout if needed for slow container starts or Kafka operations
        // Awaitility.setDefaultTimeout(60, TimeUnit.SECONDS);
    }

    @BeforeEach
    void setUpEach() throws IOException {
        // Clean up DB - order matters for foreign keys
        // Consider using @Sql or programmatic deletion via repositories
        catalogItemRepository.deleteAllInBatch();
        categoryRepository.deleteAllInBatch();

        // Create a default category for items
        testCategory = CategoryEntity.builder()
                .name("Test Category")
                .description("Default category for E2E tests")
                .path("/test-category/")
                .type(ItemType.PRODUCT) // Assuming PRODUCT type for test items
                .active(true)
                .build();
        categoryRepository.save(testCategory);

        // Ensure OpenSearch index is clean by deleting and recreating it
        // This guarantees a fresh state for each test and applies the defined mappings.
        try {
            boolean indexExists = openSearchClient.indices().exists(r -> r.index(OpenSearchConfig.CATALOG_ITEMS_INDEX_NAME)).value();
            if (indexExists) {
                openSearchClient.indices().delete(d -> d.index(OpenSearchConfig.CATALOG_ITEMS_INDEX_NAME));
                // log.info("Deleted existing OpenSearch index: {}", OpenSearchConfig.CATALOG_ITEMS_INDEX_NAME);
            }
            // Use the initializer bean to create the index with correct mappings
            openSearchIndexInitializer.initializeIndex(); // This will create if not exists or log if exists (it shouldn't at this point)
            // log.info("Recreated OpenSearch index: {}", OpenSearchConfig.CATALOG_ITEMS_INDEX_NAME);
        } catch (Exception e) { // Catch broader exception as OpenSearch client can throw various things
            // Log or handle the error; if index setup is critical, this might be a test setup failure.
            System.err.println("Error setting up OpenSearch index in @BeforeEach: " + e.getMessage());
            // Depending on test strictness, you might want to re-throw or Assert.fail()
            // For now, logging it. Consider if tests can proceed if index setup fails.
            throw new RuntimeException("Failed to setup OpenSearch index for test", e);
        }
    }

    @Test
    void fullFlowSmokeTest_priceUpdateReflectedEndToEnd() throws Exception {
        // --- Arrange ---
        // 1. Create and save a CatalogItemEntity
        UUID itemId = UUID.randomUUID();
        CatalogItemEntity initialItem = CatalogItemEntity.builder()
                .id(itemId)
                .sku("E2E-SMOKE-001")
                .name("E2E Smoke Test Item")
                .category(testCategory) // Use the category created in setUpEach
                .itemType(ItemType.PRODUCT)
                .basePrice(new BigDecimal("100.00"))
                .dynamicPrice(new BigDecimal("80.00")) // Initial dynamic price
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .dynamicPriceLastAppliedTimestamp(Instant.now().minusSeconds(3600)) // old timestamp
                .build();
        catalogItemRepository.saveAndFlush(initialItem);

        // Ensure it's initially in OpenSearch (optional, indexer service might handle on save via outbox/event)
        // For this test, we assume it might not be there yet or we want to ensure reindex works from listener.
        // If ItemService.createItem was used, it would trigger indexing. Here, direct repo save.

        // --- Act ---
        // 2. Publish a PriceUpdatedEventDto
        BigDecimal newDynamicPrice = new BigDecimal("75.00");
        Instant eventTimestamp = Instant.now();
        PriceUpdatedEventDto priceEvent = PriceUpdatedEventDto.builder()
                .eventId(UUID.randomUUID())
                .itemId(itemId)
                .finalPrice(newDynamicPrice) // This is the key field the listener uses
                .timestamp(eventTimestamp) // Crucial for idempotency and tracking
                .basePrice(initialItem.getBasePrice()) // Informational
                .currency("USD") // Informational
                // .components() // Optional, listener doesn't use currently
                .build();

        kafkaTemplate.send(priceUpdatedFromEngineTopic, itemId.toString(), priceEvent);
        // log.info("Sent PriceUpdatedEventDto to topic {}: {}", priceUpdatedFromEngineTopic, priceEvent);

        // 3. Wait for DynamicPriceUpdateListener to consume and process
        // Awaitility condition: Check DB for the updated price. Timeout after a reasonable period.
        await().atMost(15, TimeUnit.SECONDS).pollInterval(500, TimeUnit.MILLISECONDS).untilAsserted(() -> {
            CatalogItemEntity updatedItemFromDb = catalogItemRepository.findById(itemId).orElseThrow();
            assertThat(updatedItemFromDb.getDynamicPrice()).isEqualByComparingTo(newDynamicPrice);
        });

        // --- Assert ---
        // 4. DB Verification (already partially done by Awaitility, but re-fetch for full check)
        CatalogItemEntity finalItemState = catalogItemRepository.findById(itemId).orElseThrow();
        assertThat(finalItemState.getDynamicPrice()).isEqualByComparingTo(newDynamicPrice);
        assertThat(finalItemState.getDynamicPriceLastAppliedTimestamp()).isEqualTo(eventTimestamp);

        // 5. Cache Eviction Verification (Indirect)
        // Call a service method that *would* be cached to see if it reflects the new state.
        // PricingService.getPriceDetail is cached.
        // If cache wasn't evicted, it might return stale data (based on old dynamicPrice).
        // The API call below will also test this implicitly.

        // 6. Search Index Verification
        // Allow some time for OpenSearch re-indexing to complete, as it's asynchronous (listener -> indexer service)
        await().atMost(10, TimeUnit.SECONDS).pollInterval(500, TimeUnit.MILLISECONDS).untilAsserted(() -> {
            GetResponse<CatalogItemSearchDocument> searchResponse = openSearchClient.get(g -> g
                    .index(OpenSearchConfig.CATALOG_ITEMS_INDEX_NAME)
                    .id(itemId.toString()),
                    CatalogItemSearchDocument.class
            );
            assertThat(searchResponse.found()).isTrue();
            CatalogItemSearchDocument indexedDoc = searchResponse.source();
            assertThat(indexedDoc).isNotNull();
            assertThat(indexedDoc.getDynamicPrice()).isEqualTo(newDynamicPrice.doubleValue());
            // Timestamps might have slight differences due to serialization/deserialization or DB precision.
            // Compare with a tolerance or check if it's close to eventTimestamp.
            // For exact match, ensure Instant precision is handled consistently.
            assertThat(indexedDoc.getDynamicPriceLastAppliedTimestamp()).isEqualTo(eventTimestamp);
        });

        // 7. API Verification (Price Detail)
        String priceDetailUrl = "http://localhost:" + serverPort + "/api/v1/pricing/items/" + itemId + "/price-detail?quantity=1";
        ResponseEntity<PriceDetailDto> priceDetailResponse = restTemplate.getForEntity(priceDetailUrl, PriceDetailDto.class);

        assertThat(priceDetailResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        PriceDetailDto priceDetailDto = priceDetailResponse.getBody();
        assertThat(priceDetailDto).isNotNull();
        assertThat(priceDetailDto.getFinalUnitPrice()).isEqualByComparingTo(newDynamicPrice);
        assertThat(priceDetailDto.getPriceSource()).isEqualTo("DYNAMIC");
        assertThat(priceDetailDto.getDynamicPrice()).isEqualByComparingTo(newDynamicPrice);
        assertThat(priceDetailDto.getBasePrice()).isEqualByComparingTo(initialItem.getBasePrice()); // Original base
    }

    @Test
    @org.springframework.security.test.context.support.WithMockUser(username = "testuser-cart") // For Principal in CartController
    void cartIntegrationTest_reflectsDynamicPriceUpdates() throws Exception {
        // --- Arrange Phase 1: Initial Item Setup & Price ---
        UUID itemId = UUID.randomUUID();
        String testUserId = "testuser-cart"; // Matches @WithMockUser
        BigDecimal initialDynamicPrice = new BigDecimal("50.00");

        CatalogItemEntity item = CatalogItemEntity.builder()
                .id(itemId)
                .sku("E2E-CART-001")
                .name("E2E Cart Test Item")
                .category(testCategory)
                .itemType(ItemType.PRODUCT)
                .basePrice(new BigDecimal("60.00"))
                .dynamicPrice(initialDynamicPrice)
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .dynamicPriceLastAppliedTimestamp(Instant.now().minusSeconds(3600))
                .build();
        catalogItemRepository.saveAndFlush(item);

        // Ensure OpenSearch is updated (as listener would do)
        // Re-using logic similar to smoke test for search update
        PriceUpdatedEventDto initialPriceEvent = PriceUpdatedEventDto.builder()
                .eventId(UUID.randomUUID()).itemId(itemId).finalPrice(initialDynamicPrice)
                .timestamp(item.getDynamicPriceLastAppliedTimestamp() != null ? item.getDynamicPriceLastAppliedTimestamp() : Instant.now())
                .build();
        kafkaTemplate.send(priceUpdatedFromEngineTopic, itemId.toString(), initialPriceEvent);
        await().atMost(10, TimeUnit.SECONDS).pollInterval(500, TimeUnit.MILLISECONDS).untilAsserted(() -> {
            GetResponse<CatalogItemSearchDocument> searchResponse = openSearchClient.get(g -> g
                    .index(OpenSearchConfig.CATALOG_ITEMS_INDEX_NAME).id(itemId.toString()), CatalogItemSearchDocument.class);
            assertThat(searchResponse.found()).isTrue();
            assertThat(searchResponse.source().getDynamicPrice()).isEqualTo(initialDynamicPrice.doubleValue());
        });


        // --- Act Phase 1: Add to Cart & Verify Price ---
        AddItemToCartRequest addItemRequest = AddItemToCartRequest.builder()
                .catalogItemId(itemId)
                .quantity(2)
                .build();

        // Note: CartController uses Principal. For TestRestTemplate with @WithMockUser, this should work.
        // If not, might need to configure TestRestTemplate with basic auth or mock Principal.
        String addItemUrl = "/api/v1/cart/items"; // Assuming user is derived from security context
        ResponseEntity<CartDto> addResponse = restTemplate.postForEntity(addItemUrl, addItemRequest, CartDto.class);
        assertThat(addResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        String getCartUrl = "/api/v1/cart";
        ResponseEntity<CartDto> cartResponse1 = restTemplate.getForEntity(getCartUrl, CartDto.class);
        assertThat(cartResponse1.getStatusCode()).isEqualTo(HttpStatus.OK);
        CartDto cart1 = cartResponse1.getBody();
        assertThat(cart1).isNotNull();
        assertThat(cart1.getItems()).hasSize(1);
        assertThat(cart1.getItems().get(0).getCatalogItemId()).isEqualTo(itemId);
        assertThat(cart1.getItems().get(0).getQuantity()).isEqualTo(2);
        assertThat(cart1.getItems().get(0).getUnitPrice()).isEqualByComparingTo(initialDynamicPrice); // Should be 50.00
        assertThat(cart1.getFinalTotal()).isEqualByComparingTo(new BigDecimal("100.00")); // 2 * 50.00

        // --- Act Phase 2: Update Dynamic Price ---
        BigDecimal updatedDynamicPrice = new BigDecimal("45.00");
        Instant updatedEventTimestamp = Instant.now();
        PriceUpdatedEventDto priceUpdateEvent2 = PriceUpdatedEventDto.builder()
                .eventId(UUID.randomUUID())
                .itemId(itemId)
                .finalPrice(updatedDynamicPrice)
                .timestamp(updatedEventTimestamp)
                .build();

        kafkaTemplate.send(priceUpdatedFromEngineTopic, itemId.toString(), priceUpdateEvent2);

        // Wait for listener to update DB
        await().atMost(15, TimeUnit.SECONDS).pollInterval(500, TimeUnit.MILLISECONDS).untilAsserted(() -> {
            CatalogItemEntity itemAfterUpdate = catalogItemRepository.findById(itemId).orElseThrow();
            assertThat(itemAfterUpdate.getDynamicPrice()).isEqualByComparingTo(updatedDynamicPrice);
        });
        // Wait for search to be updated (important if cart pricing might hit search, though unlikely for direct getPriceDetail)
         await().atMost(10, TimeUnit.SECONDS).pollInterval(500, TimeUnit.MILLISECONDS).untilAsserted(() -> {
            GetResponse<CatalogItemSearchDocument> searchResponse = openSearchClient.get(g -> g
                    .index(OpenSearchConfig.CATALOG_ITEMS_INDEX_NAME).id(itemId.toString()), CatalogItemSearchDocument.class);
            assertThat(searchResponse.found()).isTrue();
            assertThat(searchResponse.source().getDynamicPrice()).isEqualTo(updatedDynamicPrice.doubleValue());
        });


        // --- Assert Phase 2: Verify Cart reflects new price ---
        // Getting the cart again should trigger re-calculation of prices if CartService uses PricingService.getPriceDetail for each item.
        ResponseEntity<CartDto> cartResponse2 = restTemplate.getForEntity(getCartUrl, CartDto.class);
        assertThat(cartResponse2.getStatusCode()).isEqualTo(HttpStatus.OK);
        CartDto cart2 = cartResponse2.getBody();
        assertThat(cart2).isNotNull();
        assertThat(cart2.getItems()).hasSize(1);
        assertThat(cart2.getItems().get(0).getUnitPrice()).isEqualByComparingTo(updatedDynamicPrice); // Should now be 45.00
        assertThat(cart2.getFinalTotal()).isEqualByComparingTo(new BigDecimal("90.00")); // 2 * 45.00
    }
}
