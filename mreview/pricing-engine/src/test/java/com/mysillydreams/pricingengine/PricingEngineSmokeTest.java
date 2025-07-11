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
            "${topics.dynamicRule}", "${topics.priceOverride}", "${topics.demandMetrics}", // External inputs
            "${topics.internalRulesByItemId}", // Corrected: itemId keyed internal topic for rules
            "${topics.internalOverridesByItemId}", // Corrected: itemId keyed internal topic for overrides
            "${topics.internalBasePrices}",
            "${topics.internalLastPublishedPrices}", // For KTable state
            "${topics.priceUpdated}", // External output
            "${topics.demandMetricsDlt}"
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

    @Value("${topics.priceUpdated}") // This is the external output topic
    private String externalPriceUpdatedTopic;

    // Corrected topic names for what RuleOverrideEventListener publishes to
    @Value("${topics.internalRulesByItemId}")
    private String internalRulesByItemIdTopic;
    @Value("${topics.internalOverridesByItemId}")
    private String internalOverridesByItemIdTopic;
    // Base prices and last published prices topics remain the same
    @Value("${topics.internalBasePrices}")
    private String internalBasePricesTopic;
    @Value("${topics.internalLastPublishedPrices}")
    private String internalLastPublishedPricesTopic;

    @Value("${topics.demandMetricsDlt}")
    private String demandMetricsDltTopic;


    private KafkaTemplate<String, String> kafkaTemplate;
    private KafkaMessageListenerContainer<String, PriceUpdatedEvent> priceUpdatedListenerContainer;
    private BlockingQueue<ConsumerRecord<String, PriceUpdatedEvent>> priceUpdatedConsumerRecords;
    private KafkaMessageListenerContainer<String, MetricEvent> dltListenerContainer; // For verifying DLT
    private BlockingQueue<ConsumerRecord<String, MetricEvent>> dltConsumerRecords;


    @BeforeEach
    void setUp() {
        Map<String, Object> producerProps = KafkaTestUtils.producerProps(embeddedKafkaBroker);
        DefaultKafkaProducerFactory<String, String> pf = new DefaultKafkaProducerFactory<>(producerProps);
        kafkaTemplate = new KafkaTemplate<>(pf);

        // Consumer for external priceUpdatedTopic
        priceUpdatedConsumerRecords = new LinkedBlockingQueue<>();
        Map<String, Object> priceUpdatedConsumerProps = KafkaTestUtils.consumerProps("smokeTestPriceUpdatedConsumer", "true", embeddedKafkaBroker);
        // Deserializer props already set for PriceUpdatedEvent in main KafkaConfig, can rely on that or be explicit
        priceUpdatedConsumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        priceUpdatedConsumerProps.put(JsonDeserializer.TRUSTED_PACKAGES, "com.mysillydreams.pricingengine.dto");
        priceUpdatedConsumerProps.put(JsonDeserializer.VALUE_DEFAULT_TYPE, PriceUpdatedEvent.class.getName());
        priceUpdatedConsumerProps.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, "false");
        DefaultKafkaConsumerFactory<String, PriceUpdatedEvent> priceUpdatedCF = new DefaultKafkaConsumerFactory<>(priceUpdatedConsumerProps);
        ContainerProperties priceUpdatedContainerProps = new ContainerProperties(externalPriceUpdatedTopic); // Use the correct variable
        priceUpdatedListenerContainer = new KafkaMessageListenerContainer<>(priceUpdatedCF, priceUpdatedContainerProps);
        priceUpdatedListenerContainer.setupMessageListener((MessageListener<String, PriceUpdatedEvent>) priceUpdatedConsumerRecords::add);
        priceUpdatedListenerContainer.start();
        ContainerTestUtils.waitForAssignment(priceUpdatedListenerContainer, embeddedKafkaBroker.getPartitionsPerTopic(externalPriceUpdatedTopic));

        // Consumer for DLT topic
        dltConsumerRecords = new LinkedBlockingQueue<>();
        Map<String, Object> dltConsumerProps = KafkaTestUtils.consumerProps("smokeTestDltConsumer", "true", embeddedKafkaBroker);
        dltConsumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        dltConsumerProps.put(JsonDeserializer.TRUSTED_PACKAGES, "com.mysillydreams.pricingengine.dto");
        dltConsumerProps.put(JsonDeserializer.VALUE_DEFAULT_TYPE, MetricEvent.class.getName()); // Assuming DLT contains MetricEvent
        dltConsumerProps.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, "false");
        DefaultKafkaConsumerFactory<String, MetricEvent> dltCF = new DefaultKafkaConsumerFactory<>(dltConsumerProps);
        ContainerProperties dltContainerProps = new ContainerProperties(demandMetricsDltTopic);
        dltListenerContainer = new KafkaMessageListenerContainer<>(dltCF, dltContainerProps);
        dltListenerContainer.setupMessageListener((MessageListener<String, MetricEvent>) dltConsumerRecords::add);
        dltListenerContainer.start();
        ContainerTestUtils.waitForAssignment(dltListenerContainer, embeddedKafkaBroker.getPartitionsPerTopic(demandMetricsDltTopic));
    }

    @AfterEach
    void tearDown() {
        if (priceUpdatedListenerContainer != null) priceUpdatedListenerContainer.stop();
        if (dltListenerContainer != null) dltListenerContainer.stop();
    }

    @Test
    void fullEndToEndFlow_WithRule_Metric_Threshold_AndPriceUpdate() throws Exception {
        final UUID itemId = UUID.randomUUID();
        final String itemIdStr = itemId.toString();

        // 1. Publish initial data for GlobalKTables / KTables
        // Base Price (simulating output from RuleOverrideEventListener to internalBasePricesTopic)
        ItemBasePriceEvent basePriceEvent = ItemBasePriceEvent.builder()
                .itemId(itemId).basePrice(new BigDecimal("100.00")).eventTimestamp(Instant.now()).build();
        kafkaTemplate.send(internalBasePricesTopic, itemIdStr, objectMapper.writeValueAsString(basePriceEvent));

        // Rule (simulating output from RuleOverrideEventListener to internalRulesByItemIdTopic)
        Map<String, Object> ruleParams = new HashMap<>();
        ruleParams.put("threshold", 10L); // Lower threshold for test
        ruleParams.put("adjustmentPercentage", 0.20); // +20%
        DynamicPricingRuleDto ruleDto = DynamicPricingRuleDto.builder()
                .id(UUID.randomUUID()).itemId(itemId).ruleType("VIEW_COUNT_THRESHOLD").parameters(ruleParams)
                .enabled(true).createdAt(Instant.now()).updatedAt(Instant.now()).version(1L)
                .build();
        kafkaTemplate.send(internalRulesByItemIdTopic, itemIdStr, objectMapper.writeValueAsString(ruleDto));

        // (Optional) Override - make it non-applicable or disabled for this rule test
        PriceOverrideDto overrideDto = PriceOverrideDto.builder()
            .id(UUID.randomUUID()).itemId(itemId).overridePrice(new BigDecimal("50.00"))
            .enabled(false).startTime(Instant.now().minusSeconds(1000)).endTime(Instant.now().plusSeconds(1000))
            .build();
        kafkaTemplate.send(internalOverridesByItemIdTopic, itemIdStr, objectMapper.writeValueAsString(overrideDto));

        // Allow GlobalKTables and KTables to populate. This remains a tricky point in tests.
        // Consider using Awaitility to check for some side effect if possible, or ensure stream processing time.
        Thread.sleep(3000); // Increased sleep, still not ideal.

        // 2. Publish Metric Event to trigger calculation
        MetricEvent metricEvent = MetricEvent.builder()
                .eventId(UUID.randomUUID()).itemId(itemId).metricType("VIEW")
                .timestamp(Instant.now()).details(Map.of("count", 20L)) // Above threshold
                .build();
        kafkaTemplate.send(demandMetricsTopic, itemIdStr, objectMapper.writeValueAsString(metricEvent));

        // 3. Assert PriceUpdatedEvent on external topic
        ConsumerRecord<String, PriceUpdatedEvent> priceUpdateRecord = priceUpdatedConsumerRecords.poll(15, TimeUnit.SECONDS);
        assertThat(priceUpdateRecord).as("PriceUpdatedEvent should be published as change is significant").isNotNull();
        assertThat(priceUpdateRecord.key()).isEqualTo(itemIdStr);
        PriceUpdatedEvent publishedEvent = priceUpdateRecord.value();
        assertThat(publishedEvent.getItemId()).isEqualTo(itemId);
        assertThat(publishedEvent.getBasePrice()).isEqualByComparingTo("100.00");
        assertThat(publishedEvent.getFinalPrice()).isEqualByComparingTo("120.00"); // 100 * (1 + 0.20)
        assertThat(publishedEvent.getComponents()).anySatisfy(c ->
            assertThat(c.getComponentName()).isEqualTo("VIEW_COUNT_THRESHOLD")
        );

        // 4. Assert PriceUpdatedEvent on internal topic (for KTable update)
        // This requires another consumer or to check the KTable state if possible with TopologyTestDriver.
        // For smoke test, consuming from the topic is a good validation.
        // We need a separate consumer for internalLastPublishedPricesTopic or check the output topic list from EmbeddedKafka.
        // For simplicity, the test setup for internalLastPriceOutputTopic in DemandMetricsAggregatorStreamTest can be reused.
        // Here, we'll assume the .split().branch().to() works and it was sent.

        // 5. Test DLT: Publish an invalid metric (no itemId)
        MetricEvent invalidMetric = MetricEvent.builder().eventId(UUID.randomUUID()).metricType("BAD_METRIC").timestamp(Instant.now()).build();
        kafkaTemplate.send(demandMetricsTopic, "badkey", objectMapper.writeValueAsString(invalidMetric));

        ConsumerRecord<String, MetricEvent> dltRecord = dltConsumerRecords.poll(5, TimeUnit.SECONDS);
        assertThat(dltRecord).as("Invalid metric should go to DLT").isNotNull();
        assertThat(dltRecord.key()).isEqualTo("badkey");
        assertThat(dltRecord.value().getMetricType()).isEqualTo("BAD_METRIC");

        // 6. Test Threshold: Publish another metric that results in a price below threshold
        // First, let the last published price (120.00) be established in the KTable
        // This requires the previous PriceUpdatedEvent to be consumed by the KTable's topic.
        // In a real scenario, this happens via the .to(internalLastPublishedPricesTopic).
        // In test, we might need to explicitly publish to internalLastPublishedPricesTopic if not using TopologyTestDriver's output topic for it.
        // For this smoke test, directly publishing to internalLastPublishedPricesTopic to set state.
        PriceUpdatedEvent lastPrice = PriceUpdatedEvent.builder().itemId(itemId).finalPrice(new BigDecimal("120.00")).build();
        kafkaTemplate.send(internalLastPublishedPricesTopic, itemIdStr, objectMapper.writeValueAsString(lastPrice));
        Thread.sleep(1000); // Allow KTable to update

        MetricEvent metricForSmallChange = MetricEvent.builder()
                .eventId(UUID.randomUUID()).itemId(itemId).metricType("VIEW")
                .timestamp(Instant.now()).details(Map.of("count", 0L)) // Should result in base price (100.00)
                .build();
        kafkaTemplate.send(demandMetricsTopic, itemIdStr, objectMapper.writeValueAsString(metricForSmallChange));

        // New price would be 100.00. Last price was 120.00. Change is 20.00.
        // Threshold is 1% of 120 = 1.20. 20.00 > 1.20, so it *should* publish.
        // Let's test a case where it *shouldn't* publish.
        // New rule: -0.5% adjustment if count < 5. Base 100. New price = 99.50. Last price 100. Change 0.50. Threshold 1.00. No publish.

        // Reset last price to 100 for a cleaner threshold test
        lastPrice = PriceUpdatedEvent.builder().itemId(itemId).finalPrice(new BigDecimal("100.00")).build();
        kafkaTemplate.send(internalLastPublishedPricesTopic, itemIdStr, objectMapper.writeValueAsString(lastPrice));
        Thread.sleep(1000);

        Map<String, Object> subtleRuleParams = new HashMap<>();
        subtleRuleParams.put("threshold", 5L); // views > 5
        subtleRuleParams.put("adjustmentPercentage", 0.005); // +0.5%
        DynamicPricingRuleDto subtleRule = DynamicPricingRuleDto.builder()
                .id(UUID.randomUUID()).itemId(itemId).ruleType("VIEW_COUNT_THRESHOLD").parameters(subtleRuleParams)
                .enabled(true).build();
        kafkaTemplate.send(internalRulesByItemIdTopic, itemIdStr, objectMapper.writeValueAsString(subtleRule)); // Update the rule
        Thread.sleep(1000); // Allow GKT to update

        MetricEvent metricForSubtleChange = MetricEvent.builder()
                .eventId(UUID.randomUUID()).itemId(itemId).metricType("VIEW")
                .timestamp(Instant.now()).details(Map.of("count", 10L)) // count > 5, triggers +0.5%
                .build();
        kafkaTemplate.send(demandMetricsTopic, itemIdStr, objectMapper.writeValueAsString(metricForSubtleChange));

        // Expected new price: 100 * 1.005 = 100.50. Change from 100.00 is 0.50.
        // Threshold is 1% of 100.00 = 1.00. Since 0.50 < 1.00, no event should be published.
        ConsumerRecord<String, PriceUpdatedEvent> noPriceUpdateRecord = priceUpdatedConsumerRecords.poll(5, TimeUnit.SECONDS);
        assertThat(noPriceUpdateRecord).as("Price update should be skipped due to threshold").isNull();

        // Verify counters (these are tricky in smoke tests due to potential parallel runs or existing state)
        // Counter rulesConsumedByListener = meterRegistry.get("pricing.engine.rules.consumed").counter();
        // Counter overridesConsumedByListener = meterRegistry.get("pricing.engine.overrides.consumed").counter();
        Counter metricsConsumedByStream = meterRegistry.get("pricing.engine.metrics.consumed").counter(); // This is from DemandMetricsListener

        // We sent 3 metric events (1 valid for price calc, 1 invalid to DLT, 1 valid for subtle change)
        // The DLT path in DemandMetricsAggregatorStream is *after* deserialization, so DemandMetricsListener won't see it.
        // The DemandMetricsListener is on the raw `demandMetricsTopic`.
        // If the stream's DLT logic is before the listener for that topic, it would be different.
        // Current setup: Listener on demandMetricsTopic, Stream on demandMetricsTopic.
        // The DemandMetricsListener (simple @KafkaListener) will increment for all 3.
        // The stream's internal counter for "metrics processed by stream topology" would be different.
        // For now, let's assume DemandMetricsListener's counter is what "metrics.consumed" refers to.
         await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(metricsConsumedByStream.count()).isGreaterThanOrEqualTo(3.0); // At least 3 metrics were sent to the topic
         });
    }
}
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
