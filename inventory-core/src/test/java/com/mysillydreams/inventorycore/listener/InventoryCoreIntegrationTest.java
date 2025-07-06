package com.mysillydreams.inventorycore.listener;

import com.mysillydreams.inventorycore.domain.OutboxEvent;
import com.mysillydreams.inventorycore.domain.StockLevel;
import com.mysillydreams.inventorycore.dto.LineItem;
import com.mysillydreams.inventorycore.dto.ReservationRequestedEvent;
import com.mysillydreams.inventorycore.dto.ReservationSucceededEvent; // Assuming this DTO will be generated
import com.mysillydreams.inventorycore.dto.ReservationFailedEvent;   // Assuming this DTO will be generated
import com.mysillydreams.inventorycore.repository.OutboxRepository;
import com.mysillydreams.inventorycore.repository.StockLevelRepository;

import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import io.confluent.kafka.serializers.KafkaAvroSerializer;

import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.GenericContainer; // For Schema Registry
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE) // No web server needed for this test
@Testcontainers
@ActiveProfiles("test") // Ensure application-test.yml is considered (though many props are dynamic)
class InventoryCoreIntegrationTest {

    private static final Network network = Network.newNetwork();

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:15.3-alpine"))
            .withNetwork(network)
            .withNetworkAliases("postgres-db")
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass");

    @Container
    static final KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0")) // Updated version
            .withNetwork(network)
            .withNetworkAliases("kafka-broker");

    @Container
    static final GenericContainer<?> schemaRegistry = new GenericContainer<>(DockerImageName.parse("confluentinc/cp-schema-registry:7.6.0")) // Updated version
            .withNetwork(network)
            .withNetworkAliases("schema-registry")
            .withExposedPorts(8081)
            .withEnv("SCHEMA_REGISTRY_HOST_NAME", "schema-registry")
            .withEnv("SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS", "PLAINTEXT://kafka-broker:9092") // Kafka internal listener
            .dependsOn(kafka);

    @DynamicPropertySource
    static void dynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.url", postgres::getJdbcUrl); // Ensure Flyway uses the Testcontainer DB
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true"); // Explicitly enable for Testcontainers
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate"); // Flyway handles schema


        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        String schemaRegistryUrl = "http://" + schemaRegistry.getHost() + ":" + schemaRegistry.getMappedPort(8081);
        registry.add("spring.kafka.consumer.properties.schema.registry.url", () -> schemaRegistryUrl);
        registry.add("spring.kafka.producer.properties.schema.registry.url", () -> schemaRegistryUrl);

        // Ensure outbox poller runs reasonably fast for tests
        registry.add("inventory.outbox.poll.delay", () -> "500"); // ms
        registry.add("inventory.outbox.poll.initialDelay", () -> "1000"); // ms
    }

    @Autowired
    private StockLevelRepository stockLevelRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    // Kafka topics from application context (resolved from application.yml)
    @Value("${kafka.topics.reservationRequested}")
    private String reservationRequestedTopic;
    @Value("${kafka.topics.reservationSucceeded}")
    private String reservationSucceededTopic;
    @Value("${kafka.topics.reservationFailed}")
    private String reservationFailedTopic;

    private KafkaProducer<String, SpecificRecord> producer;
    private KafkaConsumer<String, SpecificRecord> consumer;

    @BeforeAll
    static void startContainers() {
        postgres.start();
        kafka.start();
        schemaRegistry.start();
        // You can add readiness checks here if needed
    }

    @AfterAll
    static void stopContainers() {
        schemaRegistry.stop();
        kafka.stop();
        postgres.stop();
        network.close();
    }

    @BeforeEach
    void setUpKafkaClientsAndClearDb() {
        // Producer setup
        Map<String, Object> producerProps = KafkaTestUtils.producerProps(kafka.getBootstrapServers());
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class.getName());
        producerProps.put("schema.registry.url", "http://" + schemaRegistry.getHost() + ":" + schemaRegistry.getMappedPort(8081));
        producer = new KafkaProducer<>(producerProps);

        // Consumer setup
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("test-group-" + UUID.randomUUID(), "true", kafka.getBootstrapServers());
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class.getName());
        consumerProps.put(KafkaAvroDeserializerConfig.SCHEMA_REGISTRY_URL_CONFIG, "http://" + schemaRegistry.getHost() + ":" + schemaRegistry.getMappedPort(8081));
        consumerProps.put(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, "true");
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumer = new KafkaConsumer<>(consumerProps);

        // Clear DB state before each test
        outboxRepository.deleteAll();
        stockLevelRepository.deleteAll(); // Ensures tests are isolated
    }


    @Test
    void shouldReserveStockAndPublishSuccessEvent_whenSufficientStock() throws InterruptedException {
        // 1. Arrange: Initial stock
        String sku = "SKU_SUCCESS";
        int initialAvailable = 10;
        int quantityToReserve = 3;
        String orderId = UUID.randomUUID().toString();

        stockLevelRepository.save(new StockLevel(sku, initialAvailable, 0, 0L, null));

        ReservationRequestedEvent requestEvent = ReservationRequestedEvent.newBuilder()
                .setOrderId(orderId)
                .setItems(List.of(LineItem.newBuilder().setSku(sku).setQuantity(quantityToReserve).build()))
                .build();

        // 2. Act: Produce ReservationRequestedEvent to Kafka
        producer.send(new ProducerRecord<>(reservationRequestedTopic, orderId, requestEvent));
        producer.flush();

        // 3. Assertions
        // Wait for StockLevel to be updated
        await().atMost(10, TimeUnit.SECONDS).pollInterval(Duration.ofMillis(500)).untilAsserted(() -> {
            Optional<StockLevel> updatedStockOpt = stockLevelRepository.findById(sku);
            assertThat(updatedStockOpt).isPresent();
            StockLevel updatedStock = updatedStockOpt.get();
            assertThat(updatedStock.getAvailable()).isEqualTo(initialAvailable - quantityToReserve);
            assertThat(updatedStock.getReserved()).isEqualTo(quantityToReserve);
        });

        // Wait for OutboxEvent to be created
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            List<OutboxEvent> outboxEvents = outboxRepository.findAll();
            assertThat(outboxEvents).hasSize(1);
            assertThat(outboxEvents.get(0).getEventType()).isEqualTo(reservationSucceededTopic);
            assertThat(outboxEvents.get(0).getAggregateId()).isEqualTo(sku);
            Map<String, Object> payload = outboxEvents.get(0).getPayload();
            assertThat(payload.get("orderId")).isEqualTo(orderId);
        });

        // Wait for and consume ReservationSucceededEvent from Kafka (published by OutboxPoller)
        consumer.subscribe(Collections.singletonList(reservationSucceededTopic));
        ConsumerRecords<String, SpecificRecord> records = consumer.poll(Duration.ofSeconds(15)); // Increased timeout for poller

        assertThat(records.count()).isGreaterThanOrEqualTo(1); // Ensure at least one message received
        boolean found = false;
        for (ConsumerRecord<String, SpecificRecord> record : records) {
             if (record.key().equals(sku) && record.value() instanceof ReservationSucceededEvent) {
                 ReservationSucceededEvent successEvent = (ReservationSucceededEvent) record.value();
                 assertThat(successEvent.getOrderId()).isEqualTo(orderId);
                 found = true;
                 break;
             }
        }
        assertThat(found).isTrue().withFailMessage("ReservationSucceededEvent not found for order " + orderId + " and SKU " + sku);

        // Verify outbox event is marked processed
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            // Find the specific outbox event related to this test
            List<OutboxEvent> events = outboxRepository.findAll();
            Optional<OutboxEvent> outboxEventOpt = events.stream()
                .filter(e -> e.getAggregateId().equals(sku) && e.getEventType().equals(reservationSucceededTopic))
                .findFirst();
            assertThat(outboxEventOpt).isPresent().withFailMessage("Outbox event for SKU " + sku + " not found");
            assertThat(outboxEventOpt.get().isProcessed()).isTrue().withFailMessage("Outbox event for SKU " + sku + " was not marked processed");
        });
        consumer.unsubscribe();
    }


    @Test
    void shouldPublishFailedEvent_whenInsufficientStock() {
        // 1. Arrange: Initial stock (insufficient)
        String sku = "SKU_FAIL";
        int initialAvailable = 2;
        int quantityToReserve = 5; // More than available
        String orderId = UUID.randomUUID().toString();

        stockLevelRepository.save(new StockLevel(sku, initialAvailable, 0, 0L, null));

        ReservationRequestedEvent requestEvent = ReservationRequestedEvent.newBuilder()
                .setOrderId(orderId)
                .setItems(List.of(LineItem.newBuilder().setSku(sku).setQuantity(quantityToReserve).build()))
                .build();

        // 2. Act: Produce ReservationRequestedEvent
        producer.send(new ProducerRecord<>(reservationRequestedTopic, orderId, requestEvent));
        producer.flush();


        // 3. Assertions
        // StockLevel should NOT change significantly (no reservation)
        await().atMost(5, TimeUnit.SECONDS).pollDelay(Duration.ofSeconds(1)).untilAsserted(() -> {
            Optional<StockLevel> stockOpt = stockLevelRepository.findById(sku);
            assertThat(stockOpt).isPresent();
            assertThat(stockOpt.get().getAvailable()).isEqualTo(initialAvailable); // No change
            assertThat(stockOpt.get().getReserved()).isZero(); // No change
        });


        // Wait for OutboxEvent (failed)
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            List<OutboxEvent> outboxEvents = outboxRepository.findAll();
            assertThat(outboxEvents).hasSize(1);
            OutboxEvent failedOutboxEvent = outboxEvents.get(0);
            assertThat(failedOutboxEvent.getEventType()).isEqualTo(reservationFailedTopic);
            assertThat(failedOutboxEvent.getAggregateId()).isEqualTo(sku);
            Map<String, Object> payload = failedOutboxEvent.getPayload();
            assertThat(payload.get("orderId")).isEqualTo(orderId);
            assertThat(payload.get("reason")).isEqualTo("INSUFFICIENT_STOCK");
        });

        // Consume ReservationFailedEvent
        consumer.subscribe(Collections.singletonList(reservationFailedTopic));
        ConsumerRecords<String, SpecificRecord> records = consumer.poll(Duration.ofSeconds(15));

        assertThat(records.count()).isGreaterThanOrEqualTo(1);
        boolean found = false;
        for (ConsumerRecord<String, SpecificRecord> record : records) {
            if (record.key().equals(sku) && record.value() instanceof ReservationFailedEvent) {
                ReservationFailedEvent failedEvent = (ReservationFailedEvent) record.value();
                assertThat(failedEvent.getOrderId()).isEqualTo(orderId);
                assertThat(failedEvent.getSku()).isEqualTo(sku);
                assertThat(failedEvent.getReason()).isEqualTo("INSUFFICIENT_STOCK");
                // Check other fields if they were added to ReservationFailedEvent and populated
                assertThat(failedEvent.getRequestedQuantity()).isEqualTo(quantityToReserve);
                assertThat(failedEvent.getAvailableQuantity()).isEqualTo(initialAvailable);
                found = true;
                break;
            }
        }
         assertThat(found).isTrue().withFailMessage("ReservationFailedEvent not found for order " + orderId + " and SKU " + sku);


        // Verify outbox event is marked processed
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            List<OutboxEvent> events = outboxRepository.findAll();
            Optional<OutboxEvent> outboxEventOpt = events.stream()
                .filter(e -> e.getAggregateId().equals(sku) && e.getEventType().equals(reservationFailedTopic))
                .findFirst();
            assertThat(outboxEventOpt).isPresent().withFailMessage("Outbox event for SKU " + sku + " (failed) not found");
            assertThat(outboxEventOpt.get().isProcessed()).isTrue().withFailMessage("Outbox event for SKU " + sku + " (failed) was not marked processed");
        });
        consumer.unsubscribe();
    }

    // Add more tests: e.g., unknown SKU, multiple items in one request, etc.
}
