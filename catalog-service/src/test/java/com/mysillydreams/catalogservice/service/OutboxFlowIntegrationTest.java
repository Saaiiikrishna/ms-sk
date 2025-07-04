package com.mysillydreams.catalogservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mysillydreams.catalogservice.domain.model.CatalogItemEntity;
import com.mysillydreams.catalogservice.domain.model.CategoryEntity;
import com.mysillydreams.catalogservice.domain.model.ItemType;
import com.mysillydreams.catalogservice.domain.model.OutboxEventEntity;
import com.mysillydreams.catalogservice.domain.repository.CatalogItemRepository;
import com.mysillydreams.catalogservice.domain.repository.CategoryRepository;
import com.mysillydreams.catalogservice.domain.repository.DynamicPricingRuleRepository;
import com.mysillydreams.catalogservice.domain.repository.OutboxEventRepository;
import com.mysillydreams.catalogservice.domain.repository.PriceOverrideRepository;
import com.mysillydreams.catalogservice.dto.CreateDynamicPricingRuleRequest;
import com.mysillydreams.catalogservice.dto.DynamicPricingRuleDto;
import com.mysillydreams.catalogservice.dto.PriceOverrideDto; // Assuming this will be needed for override tests
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Testcontainers
@EmbeddedKafka(
    partitions = 1,
    topics = {
        "${app.kafka.topic.dynamic-rule-events}",
        "${app.kafka.topic.price-override-events}"
    }
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class OutboxFlowIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> postgresqlContainer =
            new PostgreSQLContainer<>("postgres:15.3-alpine")
                    .withDatabaseName("test_catalog_db_outbox_flow")
                    .withUsername("testuser")
                    .withPassword("testpassword");

    static {
        System.setProperty("spring.datasource.url", postgresqlContainer.getJdbcUrl());
        System.setProperty("spring.datasource.username", postgresqlContainer.getUsername());
        System.setProperty("spring.datasource.password", postgresqlContainer.getPassword());
        System.setProperty("spring.datasource.driver-class-name", "org.testcontainers.jdbc.ContainerDatabaseDriver");
        // Forcing liquibase to run for SpringBootTest, if not running by default for this setup
        System.setProperty("spring.liquibase.enabled", "true");
        System.setProperty("spring.liquibase.change-log", "classpath:db/changelog/db.changelog-master.yaml");
    }

    @Autowired
    private DynamicPricingRuleService dynamicPricingRuleService;
    // @Autowired private PriceOverrideService priceOverrideService; // For later tests

    @Autowired
    private OutboxEventRepository outboxEventRepository;
    @Autowired
    private OutboxEventPollerService outboxEventPollerService;

    @Autowired
    private CatalogItemRepository itemRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private DynamicPricingRuleRepository dynamicPricingRuleRepository;
    @Autowired
    private PriceOverrideRepository priceOverrideRepository; // For cleanup

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;
    @Autowired
    private ObjectMapper objectMapper;

    @Value("${app.kafka.topic.dynamic-rule-events}")
    private String dynamicRuleEventsTopic;
    @Value("${app.kafka.topic.price-override-events}")
    private String priceOverrideEventsTopic;

    private KafkaMessageListenerContainer<String, DynamicPricingRuleDto> dynamicRuleListenerContainer;
    private BlockingQueue<ConsumerRecord<String, DynamicPricingRuleDto>> dynamicRuleConsumerRecords;

    // private KafkaMessageListenerContainer<String, PriceOverrideDto> priceOverrideListenerContainer; // For later
    // private BlockingQueue<ConsumerRecord<String, PriceOverrideDto>> priceOverrideConsumerRecords; // For later

    private CatalogItemEntity testItem;

    @BeforeEach
    void setUp() {
        // Setup Kafka consumer for dynamic rule events
        dynamicRuleConsumerRecords = new LinkedBlockingQueue<>();
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("outboxFlowTestGroup-Rules", "true", embeddedKafkaBroker);
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, org.apache.kafka.common.serialization.StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        consumerProps.put(JsonDeserializer.TRUSTED_PACKAGES, "com.mysillydreams.catalogservice.dto");
        consumerProps.put(JsonDeserializer.VALUE_DEFAULT_TYPE, DynamicPricingRuleDto.class.getName());
        consumerProps.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, "false");


        DefaultKafkaConsumerFactory<String, DynamicPricingRuleDto> ruleCF =
                new DefaultKafkaConsumerFactory<>(consumerProps, new org.apache.kafka.common.serialization.StringDeserializer(), new JsonDeserializer<>(DynamicPricingRuleDto.class,false));

        ContainerProperties ruleContainerProps = new ContainerProperties(dynamicRuleEventsTopic);
        dynamicRuleListenerContainer = new KafkaMessageListenerContainer<>(ruleCF, ruleContainerProps);
        dynamicRuleListenerContainer.setupMessageListener((MessageListener<String, DynamicPricingRuleDto>) dynamicRuleConsumerRecords::add);
        dynamicRuleListenerContainer.start();
        ContainerTestUtils.waitForAssignment(dynamicRuleListenerContainer, embeddedKafkaBroker.getPartitionsPerTopic(dynamicRuleEventsTopic));

        // TODO: Setup consumer for price override events when those tests are added

        // Clean up DB - order matters due to foreign keys
        outboxEventRepository.deleteAll();
        dynamicPricingRuleRepository.deleteAll();
        priceOverrideRepository.deleteAll();
        itemRepository.deleteAll();
        categoryRepository.deleteAll();


        // Setup test data
        CategoryEntity category = categoryRepository.save(CategoryEntity.builder().name("Test Category").type(ItemType.PRODUCT).path("/test/").build());
        testItem = itemRepository.save(CatalogItemEntity.builder().category(category).sku("OUTBOX-ITEM-1").name("Outbox Test Item 1").itemType(ItemType.PRODUCT).basePrice(BigDecimal.valueOf(100.00)).build());
    }

    @AfterEach
    void tearDown() {
        if (dynamicRuleListenerContainer != null) {
            dynamicRuleListenerContainer.stop();
        }
        // if (priceOverrideListenerContainer != null) { priceOverrideListenerContainer.stop(); }
    }

    @Test
    void createRule_CreatesOutboxEvent_PublishesToKafka_MarksAsProcessed() throws InterruptedException, JsonProcessingException {
        // Arrange
        Map<String, Object> params = new HashMap<>();
        params.put("discountPercentage", 10.0);
        CreateDynamicPricingRuleRequest createRequest = CreateDynamicPricingRuleRequest.builder()
                .itemId(testItem.getId())
                .ruleType("FLAT_DISCOUNT")
                .parameters(params)
                .enabled(true)
                .build();

        // Act Part 1: Create the rule
        DynamicPricingRuleDto createdRuleDto = dynamicPricingRuleService.createRule(createRequest, "test-user");
        assertNotNull(createdRuleDto);
        assertNotNull(createdRuleDto.getId());

        // Assert Part 1: Check outbox event creation
        List<OutboxEventEntity> outboxEvents = outboxEventRepository.findAll();
        assertThat(outboxEvents).hasSize(1);
        OutboxEventEntity outboxEvent = outboxEvents.get(0);

        assertThat(outboxEvent.getAggregateId()).isEqualTo(createdRuleDto.getId().toString());
        assertThat(outboxEvent.getAggregateType()).isEqualTo("DynamicPricingRule");
        assertThat(outboxEvent.getEventType()).isEqualTo("dynamic.pricing.rule.created");
        assertThat(outboxEvent.getKafkaTopic()).isEqualTo(dynamicRuleEventsTopic);
        assertThat(outboxEvent.isProcessed()).isFalse();
        assertThat(outboxEvent.getProcessingAttempts()).isZero();

        DynamicPricingRuleDto payloadDtoFromOutbox = objectMapper.readValue(outboxEvent.getPayload(), DynamicPricingRuleDto.class);
        assertThat(payloadDtoFromOutbox).isEqualTo(createdRuleDto);

        // Act Part 2: Trigger the outbox poller
        outboxEventPollerService.pollAndPublishEvents();

        // Assert Part 2: Check outbox event processed and Kafka message
        OutboxEventEntity processedOutboxEvent = outboxEventRepository.findById(outboxEvent.getId()).orElseThrow();
        assertThat(processedOutboxEvent.isProcessed()).isTrue();
        assertThat(processedOutboxEvent.getProcessingAttempts()).isEqualTo(1); // Assuming it processes on the first try

        ConsumerRecord<String, DynamicPricingRuleDto> kafkaRecord = dynamicRuleConsumerRecords.poll(10, TimeUnit.SECONDS); // Increased timeout
        assertNotNull(kafkaRecord, "Kafka message for dynamic rule creation not received");
        assertThat(kafkaRecord.key()).isEqualTo(createdRuleDto.getId().toString()); // Key should be aggregateId
        DynamicPricingRuleDto receivedDto = kafkaRecord.value();
        assertThat(receivedDto).isEqualTo(createdRuleDto);
    }
}
