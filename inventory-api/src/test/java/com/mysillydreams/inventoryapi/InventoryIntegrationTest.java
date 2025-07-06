package com.mysillydreams.inventoryapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mysillydreams.inventoryapi.domain.StockLevel;
import com.mysillydreams.inventoryapi.dto.AdjustStockRequest;
import com.mysillydreams.inventoryapi.dto.ReservationRequestDto;
import com.mysillydreams.inventoryapi.repository.StockLevelRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test") // Use application-test.yml
@EmbeddedKafka(
    partitions = 1,
    brokerProperties = { "listeners=PLAINTEXT://localhost:9093", "port=9093" }, // Use a different port than default
    topics = { "${kafka.topics.reservationRequested:order.reservation.requested}" } // Ensure topic is created
)
class InventoryIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:14-alpine")
            .withDatabaseName("inventorydb")
            .withUsername("inventory") // Matches application.yml default for tests
            .withPassword("inventory");

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private StockLevelRepository stockLevelRepository;

    @Autowired
    private ObjectMapper objectMapper; // For deserializing Kafka messages if needed

    private KafkaMessageListenerContainer<String, ReservationRequestDto> consumerContainer;
    private BlockingQueue<ConsumerRecord<String, ReservationRequestDto>> consumerRecords;

    private String reservationTopic = "order.reservation.requested"; // Default, will be resolved by Spring from @EmbeddedKafka topic

    @DynamicPropertySource
    static void registerDynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // Kafka broker is handled by @EmbeddedKafka, but if using Testcontainer for Kafka:
        // registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("kafka.topics.reservationRequested", () -> "order.reservation.requested"); // Explicitly set for test context
    }

    @BeforeEach
    void setUp() {
        // Resolve the actual topic name from properties (could be prefixed by test environment)
        // For @EmbeddedKafka, the topic defined in its 'topics' attribute is created.
        // We need to ensure our consumer listens to the correct one.
        // The @Value annotation on kafka.topics.reservationRequested in InventoryServiceImpl should pick up the test value.
        // So reservationTopic here should match that.
        // If kafka.topics.reservationRequested is dynamically set by profile, ensure it's "order.reservation.requested" or what is in @EmbeddedKafka

        stockLevelRepository.deleteAll(); // Clean database before each test

        // Setup Kafka consumer for #reserve test
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("testGroup", "true", embeddedKafkaBroker);
        // consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class.getName());
        // consumerProps.put(JsonDeserializer.TRUSTED_PACKAGES, "*"); // Trust all packages for deserialization
        // consumerProps.put(JsonDeserializer.VALUE_DEFAULT_TYPE, ReservationRequestDto.class.getName());


        DefaultKafkaConsumerFactory<String, ReservationRequestDto> consumerFactory =
            new DefaultKafkaConsumerFactory<>(consumerProps,
                                              new org.apache.kafka.common.serialization.StringDeserializer(),
                                              new org.springframework.kafka.support.serializer.JsonDeserializer<>(ReservationRequestDto.class).trustedPackages("*"));


        ContainerProperties containerProps = new ContainerProperties(reservationTopic);
        consumerContainer = new KafkaMessageListenerContainer<>(consumerFactory, containerProps);
        consumerRecords = new LinkedBlockingQueue<>();
        consumerContainer.setupMessageListener((MessageListener<String, ReservationRequestDto>) consumerRecords::add);
        consumerContainer.start();
        ContainerTestUtils.waitForAssignment(consumerContainer, embeddedKafkaBroker.getPartitionsPerTopic());
    }

    @AfterEach
    void tearDown() {
        if (consumerContainer != null) {
            consumerContainer.stop();
        }
        stockLevelRepository.deleteAll();
    }

    private String createURL(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    void adjustStock_createsOrUpdatesStockLevelInDb() {
        String sku = "INT-SKU-001";
        int initialDelta = 50;
        AdjustStockRequest adjustRequest = new AdjustStockRequest(sku, initialDelta);

        // Perform POST request to /inventory/adjust
        // Note: TestRestTemplate does not automatically handle Keycloak tokens.
        // For this test, we assume Keycloak security might be disabled or permissive for /inventory/adjust
        // via test profiles or specific test security config if KeycloakConfig is active.
        // If security is active and enforced, this request would fail with 401/403.
        // The @WebMvcTest for controller often bypasses full security stack or uses @WithMockUser.
        // For @SpringBootTest, the full stack is active.
        // For simplicity in this example, we'll assume security is not blocking this unauthenticated request
        // (e.g. keycloak.enabled=false in application-test.yml or specific security rules for test profile).
        ResponseEntity<Void> response = restTemplate.postForEntity(createURL("/inventory/adjust"), adjustRequest, Void.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());

        // Verify database state
        Optional<StockLevel> savedLevelOpt = stockLevelRepository.findById(sku);
        assertTrue(savedLevelOpt.isPresent(), "StockLevel should be saved in DB");
        StockLevel savedLevel = savedLevelOpt.get();
        assertEquals(initialDelta, savedLevel.getAvailable());
        assertEquals(0, savedLevel.getReserved()); // Default reserved
        assertNotNull(savedLevel.getUpdatedAt());

        // Adjust again
        int secondDelta = -20;
        AdjustStockRequest adjustAgainRequest = new AdjustStockRequest(sku, secondDelta);
        response = restTemplate.postForEntity(createURL("/inventory/adjust"), adjustAgainRequest, Void.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());

        savedLevelOpt = stockLevelRepository.findById(sku);
        assertTrue(savedLevelOpt.isPresent());
        savedLevel = savedLevelOpt.get();
        assertEquals(initialDelta + secondDelta, savedLevel.getAvailable()); // 50 - 20 = 30
    }

    @Test
    void reserve_publishesReservationRequestToKafka() throws InterruptedException {
        UUID orderId = UUID.randomUUID();
        String sku = "INT-SKU-002";
        int quantity = 5;
        ReservationRequestDto.LineItem lineItem = new ReservationRequestDto.LineItem(sku, quantity);
        ReservationRequestDto reservationRequest = new ReservationRequestDto(orderId, Collections.singletonList(lineItem));

        // Perform POST request to /inventory/reserve (again, assuming security is handled for test)
        ResponseEntity<Void> response = restTemplate.postForEntity(createURL("/inventory/reserve"), reservationRequest, Void.class);
        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());

        // Verify Kafka message
        ConsumerRecord<String, ReservationRequestDto> received = consumerRecords.poll(10, TimeUnit.SECONDS); // Wait up to 10s
        assertNotNull(received, "No message received from Kafka topic: " + reservationTopic);

        assertEquals(orderId.toString(), received.key());
        ReservationRequestDto receivedValue = received.value();
        assertNotNull(receivedValue);
        assertEquals(orderId, receivedValue.getOrderId());
        assertThat(receivedValue.getItems()).hasSize(1);
        assertEquals(sku, receivedValue.getItems().get(0).getSku());
        assertEquals(quantity, receivedValue.getItems().get(0).getQuantity());
    }

    @Test
    void getStock_returnsStockInformation() {
        String sku = "INT-SKU-003";
        StockLevel initialStock = new StockLevel(sku, 75, 25, null); // updatedAt will be set by DB/Hibernate
        stockLevelRepository.save(initialStock);

        // Perform GET request
        ResponseEntity<StockLevelDto> response = restTemplate.getForEntity(createURL("/inventory/" + sku), StockLevelDto.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());

        StockLevelDto dto = response.getBody();
        assertNotNull(dto);
        assertEquals(sku, dto.getSku());
        assertEquals(75, dto.getAvailable());
        assertEquals(25, dto.getReserved());
    }

    @Test
    void getStock_nonExistentSku_returnsDefaultStockInformation() {
        String sku = "NON-EXISTENT-SKU";

        ResponseEntity<StockLevelDto> response = restTemplate.getForEntity(createURL("/inventory/" + sku), StockLevelDto.class);
        assertEquals(HttpStatus.OK, response.getStatusCode()); // Service returns default, not 404

        StockLevelDto dto = response.getBody();
        assertNotNull(dto);
        assertEquals(sku, dto.getSku());
        assertEquals(0, dto.getAvailable());
        assertEquals(0, dto.getReserved());
    }
}
