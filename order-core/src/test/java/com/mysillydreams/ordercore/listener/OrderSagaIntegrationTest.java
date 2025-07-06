package com.mysillydreams.ordercore.listener;

import com.mysillydreams.ordercore.OrderCoreApplication;
// Import Avro classes that will be produced or consumed in tests
import com.mysillydreams.orderapi.dto.avro.OrderCreatedEvent as OrderApiOrderCreatedEvent;
import com.mysillydreams.orderapi.dto.avro.LineItem as OrderApiLineItem;
import com.mysillydreams.ordercore.dto.avro.ReservationSucceededEvent; // Placeholder from earlier

import com.mysillydreams.ordercore.domain.Order;
import com.mysillydreams.ordercore.domain.enums.OrderStatus;
import com.mysillydreams.ordercore.repository.OrderRepository;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate; // If testing any controller
import org.springframework.boot.web.server.LocalServerPort; // If testing any controller
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.GenericContainer; // For Schema Registry
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import io.confluent.kafka.serializers.KafkaAvroSerializer;


import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;


@SpringBootTest(classes = OrderCoreApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
@ExtendWith(SpringExtension.class)
public class OrderSagaIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(OrderSagaIntegrationTest.class);

    // Define a network for containers to communicate
    private static Network network = Network.newNetwork();

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:13.3"))
            .withDatabaseName("ordercore_test_db")
            .withUsername("testuser")
            .withPassword("testpass")
            .withNetwork(network)
            .withNetworkAliases("postgres");

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.2.1")) // Use a version compatible with Avro tools
            .withNetwork(network)
            .withNetworkAliases("kafka");
            // .withEmbeddedZookeeper(); // No longer needed for recent Kafka versions

    @Container
    static GenericContainer<?> schemaRegistry = new GenericContainer<>(DockerImageName.parse("confluentinc/cp-schema-registry:7.2.1"))
            .withNetwork(network)
            .withNetworkAliases("schema-registry")
            .withExposedPorts(8081)
            .withEnv("SCHEMA_REGISTRY_HOST_NAME", "schema-registry")
            .withEnv("SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS", "PLAINTEXT://kafka:9092")
            .dependsOn(kafka);

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.url", postgres::getJdbcUrl); // Ensure Flyway uses testcontainer DB
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);

        registry.add("kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("kafka.schema-registry-url", () -> "http://" + schemaRegistry.getHost() + ":" + schemaRegistry.getMappedPort(8081));
    }

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate; // For quick DB checks if needed

    private KafkaTemplate<String, Object> avroKafkaTemplate; // For producing Avro events in tests
    private BlockingQueue<ConsumerRecord<String, Object>> orderCoreOutputRecords; // To consume events produced by Order-Core
    private KafkaMessageListenerContainer<String, Object> orderCoreOutputListenerContainer;

    // Topics (match application-test.yml or define explicitly for test)
    private final String ORDER_API_CREATED_TOPIC = "order.api.created"; // Consumed by OrderCoreSagaService
    private final String INVENTORY_RESERVATION_SUCCEEDED_TOPIC = "inventory.reservation.succeeded"; // Consumed
    private final String ORDER_CORE_CREATED_TOPIC = "order.core.created"; // Produced by OrderCore

    @BeforeAll
    static void setupContainers() {
        // postgres.start(); // JUnit5 @Container handles this
        // kafka.start();
        // schemaRegistry.start();
    }

    @BeforeEach
    void setUpKafkaTestUtils() {
        // Producer for test input events (Avro)
        Map<String, Object> producerProps = KafkaTestUtils.producerProps(kafka.getBootstrapServers());
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class.getName());
        producerProps.put("schema.registry.url", "http://" + schemaRegistry.getHost() + ":" + schemaRegistry.getMappedPort(8081));
        DefaultKafkaProducerFactory<String, Object> pf = new DefaultKafkaProducerFactory<>(producerProps);
        avroKafkaTemplate = new KafkaTemplate<>(pf);

        // Consumer for events produced by Order-Core (e.g., order.core.created)
        orderCoreOutputRecords = new LinkedBlockingQueue<>();
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("orderCoreTestConsumerGroup", "true", kafka);
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class.getName());
        consumerProps.put(KafkaAvroDeserializerConfig.SCHEMA_REGISTRY_URL_CONFIG, "http://" + schemaRegistry.getHost() + ":" + schemaRegistry.getMappedPort(8081));
        consumerProps.put(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, "true");
        // Add package containing Avro classes if SPECIFIC_AVRO_READER_CONFIG requires it for class resolution,
        // or ensure generated Avro classes are on classpath and their schema is registered.
        // consumerProps.put(JsonDeserializer.TRUSTED_PACKAGES, "*"); // Not for Avro

        DefaultKafkaConsumerFactory<String, Object> cf = new DefaultKafkaConsumerFactory<>(consumerProps);
        ContainerProperties containerProps = new ContainerProperties(ORDER_CORE_CREATED_TOPIC); // Listen to Order-Core's output
        orderCoreOutputListenerContainer = new KafkaMessageListenerContainer<>(cf, containerProps);
        orderCoreOutputListenerContainer.setupMessageListener((MessageListener<String, Object>) record -> {
            log.debug("Test consumer received record from Order-Core: {}", record);
            orderCoreOutputRecords.add(record);
        });
        orderCoreOutputListenerContainer.start();
        ContainerTestUtils.waitForAssignment(orderCoreOutputListenerContainer, kafka.getPartitionsPerTopic(ORDER_CORE_CREATED_TOPIC));
    }

    @AfterAll
    static void tearDownContainers() {
        // postgres.stop(); // JUnit5 @Container handles this
        // kafka.stop();
        // schemaRegistry.stop();
        if (network != null) {
            network.close();
        }
    }
     @BeforeEach
    void cleanupDb() {
        // Truncate tables to ensure clean state for each test
        // Order matters due to foreign keys. Outbox, history, items, then orders.
        jdbcTemplate.execute("TRUNCATE TABLE outbox_events, order_status_history, order_items, orders RESTART IDENTITY CASCADE");
    }


    @Test
    void whenOrderApiCreatedEventConsumed_thenOrderIsCreatedAndOrderCoreCreatedEventPublished() throws Exception {
        // 1. Prepare and send OrderApiOrderCreatedEvent (Avro) to ORDER_API_CREATED_TOPIC
        String apiOrderId = UUID.randomUUID().toString();
        String customerId = UUID.randomUUID().toString();
        OrderApiLineItem apiItem = OrderApiLineItem.newBuilder()
            .setProductId(UUID.randomUUID().toString()).setProductSku("SKU-001").setQuantity(1).setPrice(100.0).setDiscount(0.0).setTotalPrice(100.0).build();

        OrderApiOrderCreatedEvent apiEvent = OrderApiOrderCreatedEvent.newBuilder()
            .setOrderId(apiOrderId)
            .setCustomerId(customerId)
            .setOrderType(com.mysillydreams.orderapi.dto.avro.OrderTypeAvro.CUSTOMER)
            .setItems(Collections.singletonList(apiItem))
            .setTotalAmount(100.0)
            .setCurrency("USD")
            .setCreatedAt(Instant.now().toEpochMilli())
            .build();

        log.info("Publishing OrderApiOrderCreatedEvent to topic {}: {}", ORDER_API_CREATED_TOPIC, apiEvent);
        avroKafkaTemplate.send(ORDER_API_CREATED_TOPIC, apiEvent.getOrderId(), apiEvent).get(10, TimeUnit.SECONDS);

        // 2. Verify Order entity is created in DB
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<Order> orders = orderRepository.findAll();
            assertThat(orders).hasSize(1);
            Order createdOrder = orders.get(0);
            assertThat(createdOrder.getCustomerId().toString()).isEqualTo(customerId);
            assertThat(createdOrder.getCurrentStatus()).isEqualTo(OrderStatus.CREATED);
            // Further assertions on order details...
        });

        // 3. Verify Order-Core publishes its own OrderCreatedEvent (Avro) to ORDER_CORE_CREATED_TOPIC
        ConsumerRecord<String, Object> outputRecord = orderCoreOutputRecords.poll(10, TimeUnit.SECONDS);
        assertThat(outputRecord).isNotNull();
        assertThat(outputRecord.value()).isInstanceOf(com.mysillydreams.ordercore.dto.avro.OrderCreatedEvent.class);
        com.mysillydreams.ordercore.dto.avro.OrderCreatedEvent coreEvent = (com.mysillydreams.ordercore.dto.avro.OrderCreatedEvent) outputRecord.value();

        Order createdOrderInDb = orderRepository.findAll().get(0); // Get for comparison
        assertThat(coreEvent.getOrderId()).isEqualTo(createdOrderInDb.getId().toString());
        assertThat(coreEvent.getCustomerId()).isEqualTo(customerId);
        // Assert other fields...
    }

    @Test
    void whenReservationSucceededEventConsumed_thenOrderStatusIsPaid() throws Exception {
        // 1. First, create an order in CREATED state (e.g., by publishing OrderApiCreatedEvent or calling service directly if possible)
        // For simplicity, let's assume an order already exists with ID `existingOrderId` in status RESERVATION_PENDING or similar.
        // This might require a setup step or a prior test action if tests are ordered, or direct DB insertion.
        // Or, publish an OrderApiCreatedEvent and wait for it to be processed to CREATED.

        // Setup: Create an order directly in DB for this test scenario to simplify.
        UUID orderId = UUID.randomUUID();
        Order testOrder = new Order();
        testOrder.setId(orderId);
        testOrder.setCustomerId(UUID.randomUUID());
        testOrder.setType(com.mysillydreams.ordercore.domain.enums.OrderType.CUSTOMER);
        testOrder.setTotalAmount(java.math.BigDecimal.valueOf(50.0));
        testOrder.setCurrency("USD");
        testOrder.setCurrentStatus(OrderStatus.RESERVATION_PENDING); // State before reservation succeeded
        orderRepository.saveAndFlush(testOrder);
        log.info("Test setup: Created order {} in status {}", orderId, testOrder.getCurrentStatus());

        // 2. Prepare and send ReservationSucceededEvent (Avro)
        ReservationSucceededEvent reservationEvent = new ReservationSucceededEvent(orderId.toString(), UUID.randomUUID().toString());
        log.info("Publishing ReservationSucceededEvent to topic {}: {}", INVENTORY_RESERVATION_SUCCEEDED_TOPIC, reservationEvent);
        avroKafkaTemplate.send(INVENTORY_RESERVATION_SUCCEEDED_TOPIC, reservationEvent.orderId(), reservationEvent).get(10, TimeUnit.SECONDS);

        // 3. Verify Order status is updated to PAID in DB
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            Optional<Order> updatedOrderOpt = orderRepository.findById(orderId);
            assertThat(updatedOrderOpt).isPresent();
            Order updatedOrder = updatedOrderOpt.get();
            log.info("Order {} status after event: {}", orderId, updatedOrder.getCurrentStatus());
            assertThat(updatedOrder.getCurrentStatus()).isEqualTo(OrderStatus.PAID);
        });

        // 4. Optionally, verify that an OrderStatusUpdatedEvent (or similar) was published by Order-Core
        // This would require another consumer for that specific event type / topic.
        // For now, focus on DB state change.
    }

    // TODO: More tests for other saga steps, failure scenarios, etc.
}
