package com.mysillydreams.pricingengine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mysillydreams.pricingengine.domain.DynamicPricingRuleEntity;
import com.mysillydreams.pricingengine.domain.PriceOverrideEntity;
import com.mysillydreams.pricingengine.dto.DynamicPricingRuleDto;
import com.mysillydreams.pricingengine.dto.PriceOverrideDto;
import com.mysillydreams.pricingengine.repository.DynamicPricingRuleRepository;
import com.mysillydreams.pricingengine.repository.PriceOverrideRepository;
import com.mysillydreams.pricingengine.service.PricingEngineService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
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
        // Topics defined in application-test.yml or defaults from application.yml if not overridden
        topics = {"${topics.dynamicRule}", "${topics.priceOverride}", "${topics.demandMetrics}"}
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS) // Ensures Kafka broker is reset
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

    @Autowired
    private DynamicPricingRuleRepository ruleRepository;

    @Autowired
    private PriceOverrideRepository overrideRepository;

    @SpyBean // Using SpyBean to verify interactions on the actual bean instance
    private PricingEngineService pricingEngineService;

    @Autowired
    private MeterRegistry meterRegistry;

    @Value("${topics.dynamicRule}")
    private String dynamicRuleTopic;

    @Value("${topics.priceOverride}")
    private String priceOverrideTopic;

    private KafkaTemplate<String, String> kafkaTemplate;

    @BeforeEach
    void setUp() {
        Map<String, Object> producerProps = KafkaTestUtils.producerProps(embeddedKafkaBroker);
        DefaultKafkaProducerFactory<String, String> pf = new DefaultKafkaProducerFactory<>(producerProps);
        kafkaTemplate = new KafkaTemplate<>(pf);

        // Clean repositories before each test
        ruleRepository.deleteAll();
        overrideRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        // You could reset spies here if needed, but @DirtiesContext might handle full context reset
    }

    @Test
    void shouldConsumeRuleAndOverrideEvents_SaveToDb_AndUpdateMetrics_AndCallPricingService() throws JsonProcessingException, InterruptedException {
        // Arrange: Dynamic Pricing Rule Event
        UUID ruleId = UUID.randomUUID();
        UUID ruleItemId = UUID.randomUUID();
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


        // Assert: Verify PricingEngineService calls (using timeout for async listener processing)
        verify(pricingEngineService, timeout(5000).times(1)).updateRules(org.mockito.ArgumentMatchers.anyList());
        verify(pricingEngineService, timeout(5000).times(1)).updateOverrides(org.mockito.ArgumentMatchers.anyList());
        // Can be more specific with argument captors if needed for the list content
    }
}
