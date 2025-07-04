package com.mysillydreams.catalogservice.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mysillydreams.catalogservice.domain.model.CatalogItemEntity;
import com.mysillydreams.catalogservice.domain.model.CategoryEntity;
import com.mysillydreams.catalogservice.domain.model.ItemType;
import com.mysillydreams.catalogservice.domain.repository.CatalogItemRepository;
import com.mysillydreams.catalogservice.domain.repository.CategoryRepository;
import com.mysillydreams.catalogservice.dto.PriceUpdatedEventDto;
import com.mysillydreams.catalogservice.dto.PricingComponentDto;
import com.mysillydreams.catalogservice.service.search.CacheInvalidationService;
import com.mysillydreams.catalogservice.service.search.CatalogItemIndexerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.timeout;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@EmbeddedKafka(
        partitions = 1,
        topics = {"${app.kafka.topic.price-updated-from-engine}"},
        brokerProperties = {"listeners=PLAINTEXT://localhost:9092", "port=9092"} // Standard Kafka port
)
public class DynamicPriceUpdateListenerIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> postgresqlContainer =
            new PostgreSQLContainer<>("postgres:15.3-alpine")
                    .withDatabaseName("test_catalog_db_listener")
                    .withUsername("testuser")
                    .withPassword("testpassword");

    @Container
    private static final GenericContainer<?> redisContainer =
            new GenericContainer<>("redis:6-alpine")
                    .withExposedPorts(6379);

    static {
        System.setProperty("spring.datasource.url", postgresqlContainer.getJdbcUrl());
        System.setProperty("spring.datasource.username", postgresqlContainer.getUsername());
        System.setProperty("spring.datasource.password", postgresqlContainer.getPassword());
        System.setProperty("spring.liquibase.enabled", "true");
        System.setProperty("spring.liquibase.change-log", "classpath:db/changelog/db.changelog-master.yaml");

        // These will be overridden by TestPropertyValues in practice if not set this way
        // For SpringBootTest, better to use @DynamicPropertySource or TestPropertyValues
    }

    // @DynamicPropertySource
    // static void overrideProperties(DynamicPropertyRegistry registry) {
    //     registry.add("spring.redis.host", redisContainer::getHost);
    //     registry.add("spring.redis.port", () -> redisContainer.getMappedPort(6379).toString());
    // }


    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CatalogItemRepository catalogItemRepository;

    @Autowired
    private CategoryRepository categoryRepository; // To create parent category for item

    // Mock actual cache/search operations to verify calls without full Redis/OpenSearch setup complexity in this test
    @MockBean
    private CacheInvalidationService cacheInvalidationService;
    @MockBean
    private CatalogItemIndexerService catalogItemIndexerService;

    // To verify cache eviction, we might need StringRedisTemplate if keys are known
    // @Autowired private StringRedisTemplate stringRedisTemplate;


    @Value("${app.kafka.topic.price-updated-from-engine}")
    private String priceUpdatedTopic;

    private CatalogItemEntity testItem;

    @BeforeEach
    void setUp() {
         // Manually set Redis properties as @DynamicPropertySource is not used with static containers in this way
        System.setProperty("spring.redis.host", redisContainer.getHost());
        System.setProperty("spring.redis.port", redisContainer.getMappedPort(6379).toString());

        catalogItemRepository.deleteAll(); // Clean up before test
        categoryRepository.deleteAll();

        CategoryEntity category = categoryRepository.save(CategoryEntity.builder().name("Test Cat").type(ItemType.PRODUCT).build());
        testItem = CatalogItemEntity.builder()
                .name("Test Item for Dynamic Price")
                .sku("DYNPRICE001")
                .category(category)
                .itemType(ItemType.PRODUCT)
                .basePrice(new BigDecimal("150.00"))
                .active(true)
                .build();
        catalogItemRepository.save(testItem);
    }

    @Test
    void shouldConsumePriceUpdateEvent_AndUpdateCatalogItem_EvictCache_AndReindex() throws Exception {
        BigDecimal newDynamicPrice = new BigDecimal("140.50");
        PriceUpdatedEventDto eventDto = PriceUpdatedEventDto.builder()
                .eventId(UUID.randomUUID())
                .itemId(testItem.getId())
                .basePrice(testItem.getBasePrice())
                .finalPrice(newDynamicPrice)
                .currency("USD")
                .timestamp(Instant.now())
                .components(Collections.singletonList(PricingComponentDto.builder().componentName("DYNAMIC_ADJUST").value(newDynamicPrice.subtract(testItem.getBasePrice())).build()))
                .build();

        String payload = objectMapper.writeValueAsString(eventDto);
        kafkaTemplate.send(priceUpdatedTopic, testItem.getId().toString(), payload);

        // Awaitility to check for DB update and mock interactions
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            CatalogItemEntity updatedItem = catalogItemRepository.findById(testItem.getId()).orElseThrow();
            assertThat(updatedItem.getDynamicPrice()).isEqualByComparingTo(newDynamicPrice);
        });

        verify(cacheInvalidationService, timeout(5000).times(1)).evictItemPriceCaches(testItem.getId().toString());
        verify(catalogItemIndexerService, timeout(5000).times(1)).reindexItem(testItem.getId());
    }

    @Test
    void shouldNotUpdate_IfItemNotFound() throws Exception {
        UUID nonExistentItemId = UUID.randomUUID();
        PriceUpdatedEventDto eventDto = PriceUpdatedEventDto.builder()
                .eventId(UUID.randomUUID())
                .itemId(nonExistentItemId)
                .finalPrice(new BigDecimal("10.00"))
                // ... other fields
                .build();
        String payload = objectMapper.writeValueAsString(eventDto);
        kafkaTemplate.send(priceUpdatedTopic, nonExistentItemId.toString(), payload);

        // Allow some time for listener to process
        Thread.sleep(2000); // Crude wait, Awaitility on a non-change is harder

        verify(catalogItemRepository, never()).save(any());
        verify(cacheInvalidationService, never()).evictItemPriceCaches(anyString());
        verify(catalogItemIndexerService, never()).reindexItem(any(UUID.class));
    }
}
