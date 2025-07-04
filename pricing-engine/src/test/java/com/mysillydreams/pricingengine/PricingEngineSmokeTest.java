package com.mysillydreams.pricingengine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mysillydreams.pricingengine.dto.MetricEvent;
import com.mysillydreams.pricingengine.dto.PriceUpdatedEvent;
import com.mysillydreams.pricingengine.dto.ItemBasePriceEvent; // Added
// Domain entities no longer directly asserted here as RuleOverrideEventListener doesn't save to DB
// import com.mysillydreams.pricingengine.domain.DynamicPricingRuleEntity;
// import com.mysillydreams.pricingengine.domain.PriceOverrideEntity;
import com.mysillydreams.pricingengine.dto.DynamicPricingRuleDto;
import com.mysillydreams.pricingengine.dto.PriceOverrideDto;
// Repositories no longer asserted here directly for rules/overrides
// import com.mysillydreams.pricingengine.repository.DynamicPricingRuleRepository;
// import com.mysillydreams.pricingengine.repository.PriceOverrideRepository;
import com.mysillydreams.pricingengine.service.DefaultPricingEngineService; // For spy
import com.mysillydreams.pricingengine.service.PricingEngineService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test") // Ensure application-test.yml properties are loaded if they exist, or defaults from main yml with test values
@Testcontainers
@EmbeddedKafka(
        partitions = 1,
        topics = {
            "${topics.dynamicRule}", "${topics.priceOverride}", "${topics.demandMetrics}",
            "${topics.internalRules}", "${topics.internalOverrides}", "${topics.internalBasePrices}",
            "${topics.priceUpdated}", "${topics.demandMetricsDlt}"
        }
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class PricingEngineSmokeTest {

    @Container
    private static final PostgreSQLContainer<?> postgresqlContainer =
            new PostgreSQLContainer<>("postgres:15.3-alpine")
                    .withDatabaseName("test_pricing_db_smoke") // Separate DB for smoke test
                    .withUsername("testuser")
                    .withPassword("testpassword");

    static {
        System.setProperty("spring.datasource.url", postgresqlContainer.getJdbcUrl());
        System.setProperty("spring.datasource.username", postgresqlContainer.getUsername());
        System.setProperty("spring.datasource.password", postgresqlContainer.getPassword());
        System.setProperty("spring.datasource.driver-class-name", "org.testcontainers.jdbc.ContainerDatabaseDriver");
        System.setProperty("spring.liquibase.enabled", "true"); // Ensure liquibase runs if schema comes from it
        // If pricing-engine has its own liquibase for its own tables (none yet, but good practice)
        // System.setProperty("spring.liquibase.change-log", "classpath:db/changelog/db.changelog-master.yaml");

        // Forcing JPA ddl-auto for tests if not set in application-test.yml
        // This is important because pricing-engine reads tables created by catalog-service.
        // For smoke test, we might want to ensure tables are there, but ddl-auto: none is in main.
        // The test profile in main application.yml already sets ddl-auto: create-drop
        // System.setProperty("spring.jpa.hibernate.ddl-auto", "create-drop");
    }

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Autowired
    private ObjectMapper objectMapper;

    // Repositories for rules/overrides are no longer directly relevant for assertions here
    // as RuleOverrideEventListener doesn't save to DB anymore.
    // @Autowired private DynamicPricingRuleRepository ruleRepository;
    // @Autowired private PriceOverrideRepository overrideRepository;

    @SpyBean
    private DefaultPricingEngineService pricingEngineService; // Spy on the concrete class for its methods

    @Autowired
    private MeterRegistry meterRegistry;

    @Value("${topics.dynamicRule}")
    private String dynamicRuleTopic;

    @Value("${topics.priceOverride}")
    private String externalPriceOverrideTopic; // Renamed to avoid conflict with internal

    @Value("${topics.demandMetrics}")
    private String demandMetricsTopic;

    @Value("${topics.priceUpdated}")
    private String priceUpdatedTopic;

    @Value("${topics.internalRules}")
    private String internalRulesTopic;
    @Value("${topics.internalOverrides}")
    private String internalOverridesTopic;
    @Value("${topics.internalBasePrices}")
    private String internalBasePricesTopic;
    @Value("${topics.demandMetricsDlt}")
    private String demandMetricsDltTopic;


    private KafkaTemplate<String, String> kafkaTemplate; // For sending raw JSON strings
    private KafkaMessageListenerContainer<String, PriceUpdatedEvent> priceUpdatedListenerContainer;
    private BlockingQueue<ConsumerRecord<String, PriceUpdatedEvent>> priceUpdatedConsumerRecords;


    @BeforeEach
    void setUp() {
        Map<String, Object> producerProps = KafkaTestUtils.producerProps(embeddedKafkaBroker);
        DefaultKafkaProducerFactory<String, String> pf = new DefaultKafkaProducerFactory<>(producerProps);
        kafkaTemplate = new KafkaTemplate<>(pf);

        priceUpdatedConsumerRecords = new LinkedBlockingQueue<>();
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("smokeTestPriceUpdatedConsumer", "true", embeddedKafkaBroker);
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, org.apache.kafka.common.serialization.StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        consumerProps.put(JsonDeserializer.TRUSTED_PACKAGES, "com.mysillydreams.pricingengine.dto");
        consumerProps.put(JsonDeserializer.VALUE_DEFAULT_TYPE, PriceUpdatedEvent.class.getName());
        consumerProps.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, "false");

        DefaultKafkaConsumerFactory<String, PriceUpdatedEvent> priceUpdatedCF = new DefaultKafkaConsumerFactory<>(consumerProps);
        ContainerProperties priceUpdatedContainerProps = new ContainerProperties(priceUpdatedTopic);
        priceUpdatedListenerContainer = new KafkaMessageListenerContainer<>(priceUpdatedCF, priceUpdatedContainerProps);
        priceUpdatedListenerContainer.setupMessageListener((MessageListener<String, PriceUpdatedEvent>) priceUpdatedConsumerRecords::add);
        priceUpdatedListenerContainer.start();
        ContainerTestUtils.waitForAssignment(priceUpdatedListenerContainer, embeddedKafkaBroker.getPartitionsPerTopic(priceUpdatedTopic));

        // No direct DB cleanup for rules/overrides as they are not persisted by this service anymore
    }

    @AfterEach
    void tearDown() {
        if (priceUpdatedListenerContainer != null) {
            priceUpdatedListenerContainer.stop();
        }
    }

    @Test
    void shouldConsumeRuleAndOverrideEvents_SaveToDb_AndUpdateMetrics_AndCallPricingService() throws JsonProcessingException, InterruptedException {
        // This test name is now a bit broad. It will also test metric consumption and price update publication.

        // Arrange: Dynamic Pricing Rule Event
        UUID ruleId = UUID.randomUUID();
        final UUID ruleItemId = UUID.randomUUID(); // Make final for use in lambda/anonymous class if needed
        Map<String, Object> ruleParams = new HashMap<>();
        ruleParams.put("discount", 5.0);
        DynamicPricingRuleDto ruleDto = DynamicPricingRuleDto.builder()
                .id(ruleId).itemId(ruleItemId).itemSku("SKU-RULE")
                .ruleType("FLAT_AMOUNT_OFF").parameters(ruleParams)
                .enabled(true).createdBy("smoke-test").createdAt(Instant.now()).updatedAt(Instant.now()).version(1L)
                .build();
        String rulePayload = objectMapper.writeValueAsString(ruleDto);

        // Arrange: Price Override Event
        UUID overrideId = UUID.randomUUID();
        UUID overrideItemId = UUID.randomUUID();
        PriceOverrideDto overrideDto = PriceOverrideDto.builder()
                .id(overrideId).itemId(overrideItemId).itemSku("SKU-OVERRIDE")
                .overridePrice(BigDecimal.valueOf(99.99)).startTime(Instant.now()).endTime(Instant.now().plusSeconds(3600))
                .enabled(true).createdByUserId("smoke-test-user").createdByRole("TESTER")
                .createdAt(Instant.now()).updatedAt(Instant.now()).version(1L)
                .build();
        String overridePayload = objectMapper.writeValueAsString(overrideDto);

        // Act: Publish events
        kafkaTemplate.send(new ProducerRecord<>(dynamicRuleTopic, ruleDto.getId().toString(), rulePayload));
        kafkaTemplate.send(new ProducerRecord<>(priceOverrideTopic, overrideDto.getId().toString(), overridePayload));

        // Assert: Check database after a short wait for Kafka listeners to process
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(ruleRepository.findById(ruleId)).isPresent();
            assertThat(overrideRepository.findById(overrideId)).isPresent();
        });

        DynamicPricingRuleEntity savedRule = ruleRepository.findById(ruleId).get();
        assertThat(savedRule.getItemId()).isEqualTo(ruleItemId);
        assertThat(savedRule.getRuleType()).isEqualTo("FLAT_AMOUNT_OFF");
        assertThat(savedRule.isEnabled()).isTrue();
        assertThat(savedRule.getVersion()).isEqualTo(1L);

        PriceOverrideEntity savedOverride = overrideRepository.findById(overrideId).get();
        assertThat(savedOverride.getItemId()).isEqualTo(overrideItemId);
        assertThat(savedOverride.getOverridePrice()).isEqualByComparingTo(BigDecimal.valueOf(99.99));
        assertThat(savedOverride.isEnabled()).isTrue();
        assertThat(savedOverride.getVersion()).isEqualTo(1L);

        // Assert: Check Micrometer counters
        Counter rulesConsumed = meterRegistry.get("pricing.engine.rules.consumed").counter();
        Counter overridesConsumed = meterRegistry.get("pricing.engine.overrides.consumed").counter();

        // Counters might have been incremented by other tests if context is not fully dirtied or if tests run in parallel.
        // For a clean check, ensure this test runs in isolation or assert increment from a baseline.
        // Here, we assume it's the first/only one incrementing these specific counters in this test run.
        // Awaitility can also be used for counters if they increment asynchronously from listener.
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
             assertThat(rulesConsumed.count()).isEqualTo(1.0);
             assertThat(overridesConsumed.count()).isEqualTo(1.0);
        });


        // Assert: Verify PricingEngineService calls for rules and overrides
        verify(pricingEngineService, timeout(5000).times(1)).updateRules(org.mockito.ArgumentMatchers.anyList());
        verify(pricingEngineService, timeout(5000).times(1)).updateOverrides(org.mockito.ArgumentMatchers.anyList());


        // Arrange: Demand Metric Event for the same item as the rule
        MetricEvent metricEvent = MetricEvent.builder()
                .eventId(UUID.randomUUID())
                .itemId(ruleItemId) // Use same itemId as the rule for testing rule application
                .metricType("VIEW_COUNT")
                .timestamp(Instant.now())
                .details(Map.of("views", 150L)) // Example detail
                .build();
        String metricPayload = objectMapper.writeValueAsString(metricEvent);

        // Act: Publish metric event
        kafkaTemplate.send(new ProducerRecord<>(demandMetricsTopic, metricEvent.getItemId().toString(), metricPayload));

        // Assert: Verify PricingEngineService processMetric call
        // The Kafka Streams topology runs in its own threads. Awaitility might be needed if it's slow.
        // Also, processMetric in DefaultPricingEngineService now calls calculateAndPublishPrice.
        // We are spying on pricingEngineService, so we can verify processMetric.
        // The calculateAndPublishPrice will then use the KafkaTemplate to send a PriceUpdatedEvent.
        verify(pricingEngineService, timeout(10000).times(1)).processMetric(any(MetricEvent.class));


        // Assert: Check for PriceUpdatedEvent on the output topic
        ConsumerRecord<String, PriceUpdatedEvent> priceUpdateRecord = priceUpdatedConsumerRecords.poll(10, TimeUnit.SECONDS);
        assertThat(priceUpdateRecord).as("PriceUpdatedEvent should be published").isNotNull();
        assertThat(priceUpdateRecord.key()).isEqualTo(ruleItemId.toString());
        PriceUpdatedEvent publishedPriceUpdate = priceUpdateRecord.value();
        assertThat(publishedPriceUpdate.getItemId()).isEqualTo(ruleItemId);

        // Further assertions on publishedPriceUpdate content can be added based on expected logic
        // e.g. if the rule "FLAT_AMOUNT_OFF" with discount 5.0 was applied to base price 100.0
        // final price should be 95.0
        // DefaultPricingEngineService.fetchBasePrice returns 100.00 by default in tests.
        // Rule was FLAT_AMOUNT_OFF with discount 5.0, so expected adjustment factor -0.05
        // Expected final price: 100 * (1 - 0.05) = 95.00 if metric doesn't change it
        // The current rule in test is "FLAT_AMOUNT_OFF" with "discount": 5.0 in params
        // The calculateRuleAdjustment logic for FLAT_AMOUNT_OFF is:
        // adjustmentFactor = BigDecimal.valueOf(amountOff).divide(basePrice, 4, RoundingMode.HALF_UP).negate();
        // So, 5.0 / 100.00 = 0.05. Negated = -0.05.
        // Final price = 100.00 * (1 - 0.05) = 100.00 * 0.95 = 95.00
        assertThat(publishedPriceUpdate.getFinalPrice()).isEqualByComparingTo("95.00");
        assertThat(publishedPriceUpdate.getComponents()).anySatisfy(component -> {
            assertThat(component.getComponentName()).isEqualTo("FLAT_AMOUNT_OFF");
            assertThat(component.getValue()).isEqualByComparingTo("-5.00"); // The value of the adjustment amount
        });


        // Assert: Check metrics counter for demand metrics
        Counter metricsConsumed = meterRegistry.get("pricing.engine.metrics.consumed").counter();
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
             assertThat(metricsConsumed.count()).isEqualTo(1.0);
        });
    }
}
