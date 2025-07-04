package com.mysillydreams.catalogservice.service;

import com.mysillydreams.catalogservice.domain.model.BulkPricingRuleEntity;
import com.mysillydreams.catalogservice.domain.model.CatalogItemEntity;
import com.mysillydreams.catalogservice.domain.model.CategoryEntity;
import com.mysillydreams.catalogservice.domain.model.ItemType;
import com.mysillydreams.catalogservice.domain.repository.BulkPricingRuleRepository;
import com.mysillydreams.catalogservice.domain.repository.CatalogItemRepository;
import com.mysillydreams.catalogservice.domain.repository.CategoryRepository;
import com.mysillydreams.catalogservice.dto.*; // Import all DTOs
import com.mysillydreams.catalogservice.kafka.event.BulkPricingRuleEvent;
import com.mysillydreams.catalogservice.service.pricing.DynamicPricingEngine; // For potential spy/mock if complex interactions were tested

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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Testcontainers
@EmbeddedKafka(partitions = 1, topics = {"${app.kafka.topic.bulk-rule-added}"}) // Using the configured topic name
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class PricingServiceIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> postgresqlContainer =
            new PostgreSQLContainer<>("postgres:15.3-alpine")
                    .withDatabaseName("test_catalog_db_pricing_int")
                    .withUsername("testuser")
                    .withPassword("testpassword");

    static {
        System.setProperty("spring.datasource.url", postgresqlContainer.getJdbcUrl());
        System.setProperty("spring.datasource.username", postgresqlContainer.getUsername());
        System.setProperty("spring.datasource.password", postgresqlContainer.getPassword());
        System.setProperty("spring.datasource.driver-class-name", "org.testcontainers.jdbc.ContainerDatabaseDriver");
    }

    @Autowired private PricingService pricingService;
    @Autowired private CatalogItemRepository itemRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private BulkPricingRuleRepository bulkPricingRuleRepository;
    @Autowired private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Value("${app.kafka.topic.bulk-rule-added}") // Matches topic in @EmbeddedKafka
    private String bulkRuleEventTopic;

    private KafkaMessageListenerContainer<String, BulkPricingRuleEvent> listenerContainer;
    private BlockingQueue<ConsumerRecord<String, BulkPricingRuleEvent>> consumerRecords;

    private CatalogItemEntity product1;

    @BeforeEach
    void setUp() {
        consumerRecords = new LinkedBlockingQueue<>();
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("pricingTestGroup", "true", embeddedKafkaBroker);
        consumerProps.put(JsonDeserializer.TRUSTED_PACKAGES, "com.mysillydreams.catalogservice.kafka.event");
        consumerProps.put(JsonDeserializer.VALUE_DEFAULT_TYPE, BulkPricingRuleEvent.class.getName());
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);


        DefaultKafkaConsumerFactory<String, BulkPricingRuleEvent> cf =
                new DefaultKafkaConsumerFactory<>(consumerProps, new org.apache.kafka.common.serialization.StringDeserializer(), new JsonDeserializer<>(BulkPricingRuleEvent.class, false));

        ContainerProperties containerProps = new ContainerProperties(bulkRuleEventTopic);
        listenerContainer = new KafkaMessageListenerContainer<>(cf, containerProps);
        listenerContainer.setupMessageListener((MessageListener<String, BulkPricingRuleEvent>) consumerRecords::add);
        listenerContainer.start();
        ContainerTestUtils.waitForAssignment(listenerContainer, embeddedKafkaBroker.getPartitionsPerTopic());

        bulkPricingRuleRepository.deleteAll();
        itemRepository.deleteAll();
        categoryRepository.deleteAll();

        CategoryEntity cat = categoryRepository.save(CategoryEntity.builder().name("Test Category").type(ItemType.PRODUCT).path("/testcat/").build());
        product1 = itemRepository.save(CatalogItemEntity.builder().category(cat).sku("INT-PRICE-P1").name("Pricing Test Product").itemType(ItemType.PRODUCT).basePrice(new BigDecimal("100.00")).active(true).build());
    }

    @AfterEach
    void tearDown() {
        if (listenerContainer != null) {
            listenerContainer.stop();
        }
    }

    @Test
    void createBulkPricingRule_savesAndPublishesEvent() throws InterruptedException {
        CreateBulkPricingRuleRequest request = CreateBulkPricingRuleRequest.builder()
                .itemId(product1.getId())
                .minQuantity(10)
                .discountPercentage(new BigDecimal("5.00")) // 5%
                .active(true)
                .build();

        BulkPricingRuleDto createdRuleDto = pricingService.createBulkPricingRule(request);
        assertNotNull(createdRuleDto.getId());

        BulkPricingRuleEntity ruleEntity = bulkPricingRuleRepository.findById(createdRuleDto.getId()).orElseThrow();
        assertThat(ruleEntity.getDiscountPercentage()).isEqualByComparingTo("5.00");

        ConsumerRecord<String, BulkPricingRuleEvent> record = consumerRecords.poll(5, TimeUnit.SECONDS);
        assertNotNull(record, "Kafka message not received for bulk rule event");
        BulkPricingRuleEvent event = record.value();
        assertThat(event.getRuleId()).isEqualTo(createdRuleDto.getId());
        assertThat(event.getItemId()).isEqualTo(product1.getId());
        assertThat(event.getDiscountPercentage()).isEqualByComparingTo("5.00");
        assertThat(event.getEventType()).isEqualTo("bulk.pricing.rule.added");
    }

    @Test
    void getPriceDetail_noRules_returnsBasePrice() {
        PriceDetailDto priceDetail = pricingService.getPriceDetail(product1.getId(), 5);

        assertThat(priceDetail.getBasePrice()).isEqualByComparingTo("100.00");
        assertThat(priceDetail.getFinalUnitPrice()).isEqualByComparingTo("100.00");
        assertThat(priceDetail.getTotalPrice()).isEqualByComparingTo("500.00")); // 5 * 100
        assertThat(priceDetail.getComponents()).hasSize(1);
        assertThat(priceDetail.getComponents().get(0).getCode()).isEqualTo("CATALOG_BASE_PRICE");
    }

    @Test
    void getPriceDetail_ruleApplies_returnsDiscountedPrice() {
        // Rule: 10% off for 5 or more
        pricingService.createBulkPricingRule(CreateBulkPricingRuleRequest.builder()
            .itemId(product1.getId()).minQuantity(5).discountPercentage(new BigDecimal("10.00")).active(true).build());
        consumerRecords.clear(); // Ignore create event

        PriceDetailDto priceDetail = pricingService.getPriceDetail(product1.getId(), 10); // Request 10 items

        assertThat(priceDetail.getBasePrice()).isEqualByComparingTo("100.00");
        assertThat(priceDetail.getFinalUnitPrice()).isEqualByComparingTo("90.00"); // 100 * 0.9
        assertThat(priceDetail.getTotalPrice()).isEqualByComparingTo("900.00")); // 10 * 90
        assertThat(priceDetail.getComponents()).anyMatch(c -> "BULK_DISCOUNT".equals(c.getCode()) && c.getAmount().compareTo(new BigDecimal("-10.00")) == 0);
    }

    @Test
    void getPriceDetail_ruleNotApplicableDueToQuantity_returnsBasePrice() {
        // Rule: 10% off for 5 or more
        pricingService.createBulkPricingRule(CreateBulkPricingRuleRequest.builder()
            .itemId(product1.getId()).minQuantity(5).discountPercentage(new BigDecimal("10.00")).active(true).build());
        consumerRecords.clear();

        PriceDetailDto priceDetail = pricingService.getPriceDetail(product1.getId(), 4); // Request 4 items (less than minQty)

        assertThat(priceDetail.getFinalUnitPrice()).isEqualByComparingTo("100.00");
        assertThat(priceDetail.getComponents()).noneMatch(c -> "BULK_DISCOUNT".equals(c.getCode()));
    }

    @Test
    void getPriceDetail_ruleNotActive_returnsBasePrice() {
        pricingService.createBulkPricingRule(CreateBulkPricingRuleRequest.builder()
            .itemId(product1.getId()).minQuantity(5).discountPercentage(new BigDecimal("10.00")).active(false).build()); // Rule is inactive
        consumerRecords.clear();

        PriceDetailDto priceDetail = pricingService.getPriceDetail(product1.getId(), 10);
        assertThat(priceDetail.getFinalUnitPrice()).isEqualByComparingTo("100.00");
        assertThat(priceDetail.getComponents()).noneMatch(c -> "BULK_DISCOUNT".equals(c.getCode()));
    }

    @Test
    void getPriceDetail_ruleExpired_returnsBasePrice() {
        pricingService.createBulkPricingRule(CreateBulkPricingRuleRequest.builder()
            .itemId(product1.getId()).minQuantity(5).discountPercentage(new BigDecimal("10.00"))
            .validFrom(Instant.now().minus(2, ChronoUnit.DAYS))
            .validTo(Instant.now().minus(1, ChronoUnit.DAYS)) // Expired yesterday
            .active(true).build());
        consumerRecords.clear();

        PriceDetailDto priceDetail = pricingService.getPriceDetail(product1.getId(), 10);
        assertThat(priceDetail.getFinalUnitPrice()).isEqualByComparingTo("100.00");
        assertThat(priceDetail.getComponents()).noneMatch(c -> "BULK_DISCOUNT".equals(c.getCode()));
    }

    @Test
    void getPriceDetail_multipleApplicableRules_picksBestDiscount() {
        // Rule 1: 5% for >= 5 items
        pricingService.createBulkPricingRule(CreateBulkPricingRuleRequest.builder()
            .itemId(product1.getId()).minQuantity(5).discountPercentage(new BigDecimal("5.00")).active(true).build());
        // Rule 2: 10% for >= 10 items (better discount, more specific quantity for higher qty)
        pricingService.createBulkPricingRule(CreateBulkPricingRuleRequest.builder()
            .itemId(product1.getId()).minQuantity(10).discountPercentage(new BigDecimal("10.00")).active(true).build());
        // Rule 3: 7% for >= 2 items (applies to broader range, but less discount than rule 2 for qty >=10)
         pricingService.createBulkPricingRule(CreateBulkPricingRuleRequest.builder()
            .itemId(product1.getId()).minQuantity(2).discountPercentage(new BigDecimal("7.00")).active(true).build());
        consumerRecords.clear();

        // Scenario 1: Quantity 12 (Rule 2: 10% should apply)
        PriceDetailDto priceDetail_qty12 = pricingService.getPriceDetail(product1.getId(), 12);
        assertThat(priceDetail_qty12.getFinalUnitPrice()).isEqualByComparingTo("90.00"); // 100 * 0.90
        assertThat(priceDetail_qty12.getComponents()).anyMatch(c -> "BULK_DISCOUNT".equals(c.getCode()) && c.getAmount().compareTo(new BigDecimal("-10.00")) == 0);


        // Scenario 2: Quantity 7 (Rule 1 (5% for >=5) and Rule 3 (7% for >=2) apply. Rule 3 (7%) should be picked as it has higher discount)
        PriceDetailDto priceDetail_qty7 = pricingService.getPriceDetail(product1.getId(), 7);
        assertThat(priceDetail_qty7.getFinalUnitPrice()).isEqualByComparingTo("93.00"); // 100 * 0.93
        assertThat(priceDetail_qty7.getComponents()).anyMatch(c -> "BULK_DISCOUNT".equals(c.getCode()) && c.getAmount().compareTo(new BigDecimal("-7.00")) == 0);

        // Scenario 3: Quantity 3 (Only Rule 3 applies - 7%)
        PriceDetailDto priceDetail_qty3 = pricingService.getPriceDetail(product1.getId(), 3);
        assertThat(priceDetail_qty3.getFinalUnitPrice()).isEqualByComparingTo("93.00"); // 100 * 0.93
        assertThat(priceDetail_qty3.getComponents()).anyMatch(c -> "BULK_DISCOUNT".equals(c.getCode()) && c.getAmount().compareTo(new BigDecimal("-7.00")) == 0);
    }
}
