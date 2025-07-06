package com.mysillydreams.delivery.listener; // Package name matches plan structure

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mysillydreams.delivery.DeliveryApplication;
// Avro classes for events produced/consumed in tests
import com.mysillydreams.delivery.dto.avro.*; // Using wildcard for all Avro DTOs in this package
import com.mysillydreams.delivery.dto.PhotoOtpDto; // For controller tests

import com.mysillydreams.delivery.domain.DeliveryAssignment;
import com.mysillydreams.delivery.domain.enums.DeliveryAssignmentStatus;
import com.mysillydreams.delivery.repository.DeliveryAssignmentRepository;
import com.mysillydreams.delivery.repository.OutboxRepository; // To check outbox state

import dasniko.testcontainers.keycloak.KeycloakContainer;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import io.confluent.kafka.serializers.KafkaAvroSerializer;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

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

@SpringBootTest(classes = DeliveryApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test") // Uses application-test.yml
@Testcontainers
public class DeliveryIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(DeliveryIntegrationTest.class);
    private static final Network network = Network.newNetwork();

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:13.3"))
            .withNetwork(network).withNetworkAliases("postgres_delivery");

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.2.1"))
            .withNetwork(network).withNetworkAliases("kafka_delivery");

    @Container
    static GenericContainer<?> schemaRegistry = new GenericContainer<>(DockerImageName.parse("confluentinc/cp-schema-registry:7.2.1"))
            .withNetwork(network).withNetworkAliases("schema_registry_delivery")
            .withExposedPorts(8081)
            .withEnv("SCHEMA_REGISTRY_HOST_NAME", "schema-registry-delivery")
            .withEnv("SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS", "PLAINTEXT://kafka_delivery:9092")
            .dependsOn(kafka);

    // Keycloak Testcontainer (optional, if testing secured endpoints)
    /*
    @Container
    static KeycloakContainer keycloak = new KeycloakContainer("keycloak/keycloak:19.0.3")
            .withRealmImportFile("keycloak-delivery-realm.json") // Needs a realm file
            .withNetwork(network).withNetworkAliases("keycloak_delivery");
    */

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.url", postgres::getJdbcUrl);
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);

        registry.add("kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("kafka.schema-registry-url", () -> "http://" + schemaRegistry.getHost() + ":" + schemaRegistry.getMappedPort(8081));

        // registry.add("keycloak.auth-server-url", keycloak::getAuthServerUrl); // If Keycloak container is used
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate; // For calling controller endpoints

    @Autowired
    private DeliveryAssignmentRepository assignmentRepository;
    @Autowired
    private OutboxRepository outboxRepository; // Real OutboxRepository
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ObjectMapper objectMapper; // For constructing payloads if needed

    private KafkaTemplate<String, Object> testAvroKafkaProducer;
    private BlockingQueue<ConsumerRecord<String, Object>> consumedDeliveryEvents;
    private KafkaMessageListenerContainer<String, Object> deliveryEventsListenerContainer;

    // Topic names from application-test.yml (or defaults)
    @Value("${kafka.topics.orderShipmentRequested:order.shipment.requested}")
    private String orderShipmentRequestedTopic;
    @Value("${kafka.topics.deliveryAssignmentCreated:delivery.assignment.created}")
    private String deliveryAssignmentCreatedTopic;
    @Value("${kafka.topics.deliveryPickedUp:delivery.picked_up}")
    private String deliveryPickedUpTopic;
     @Value("${kafka.topics.deliveryDelivered:delivery.delivered}")
    private String deliveryDeliveredTopic;


    @BeforeEach
    void setUpKafkaTestInfrastructure() {
        // Producer for sending test events (e.g., ShipmentRequestedEvent)
        Map<String, Object> producerProps = KafkaTestUtils.producerProps(kafka.getBootstrapServers());
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class.getName());
        producerProps.put("schema.registry.url", "http://" + schemaRegistry.getHost() + ":" + schemaRegistry.getMappedPort(8081));
        producerProps.put("auto.register.schemas", true);
        testAvroKafkaProducer = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(producerProps));

        // Consumer for events produced by DeliveryService (e.g., DeliveryAssignmentCreatedEvent)
        consumedDeliveryEvents = new LinkedBlockingQueue<>();
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("deliveryIntegrationTestGroup", "true", kafka);
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class.getName());
        consumerProps.put(KafkaAvroDeserializerConfig.SCHEMA_REGISTRY_URL_CONFIG, "http://" + schemaRegistry.getHost() + ":" + schemaRegistry.getMappedPort(8081));
        consumerProps.put(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, "true");
        // For complex Avro schemas or if specific classes are in different packages, might need:
        // props.put(KafkaAvroDeserializerConfig.SCHEMA_REFLECTION_CONFIG, "true"); // If using reflection for schema
        // props.put(KafkaAvroDeserializerConfig.VALUE_SUBJECT_NAME_STRATEGY, RecordNameStrategy.class.getName());

        DefaultKafkaConsumerFactory<String, Object> cf = new DefaultKafkaConsumerFactory<>(consumerProps);
        // Listen to multiple topics produced by DeliveryService outbox
        ContainerProperties containerProps = new ContainerProperties(deliveryAssignmentCreatedTopic, deliveryPickedUpTopic, deliveryDeliveredTopic);
        deliveryEventsListenerContainer = new KafkaMessageListenerContainer<>(cf, containerProps);
        deliveryEventsListenerContainer.setupMessageListener((MessageListener<String, Object>) record -> {
            log.info("Test consumer received DeliveryService event: {} from topic {}", record.value(), record.topic());
            consumedDeliveryEvents.add(record);
        });
        deliveryEventsListenerContainer.start();
        // Wait for assignment for all listened topics
        ContainerTestUtils.waitForAssignment(deliveryEventsListenerContainer,
            kafka.getPartitionsPerTopic(deliveryAssignmentCreatedTopic) +
            kafka.getPartitionsPerTopic(deliveryPickedUpTopic) +
            kafka.getPartitionsPerTopic(deliveryDeliveredTopic)
        );
    }

    @BeforeEach
    void cleanupDatabase() {
        // Order of truncation matters due to foreign keys
        jdbcTemplate.execute("TRUNCATE TABLE delivery_events, outbox_events, delivery_assignments, delivery_profiles RESTART IDENTITY CASCADE");
    }

    @AfterEach
    void tearDownKafkaListeners() {
        if (deliveryEventsListenerContainer != null) {
            deliveryEventsListenerContainer.stop();
        }
    }


    @Test
    void whenShipmentRequestedEventConsumed_thenAssignmentCreatedAndEventPublished() throws Exception {
        // 1. Prepare and send ShipmentRequestedEvent (Avro)
        UUID orderId = UUID.randomUUID();
        UUID vendorId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        AddressAvro pickupAddr = AddressAvro.newBuilder().setStreet("1 Main St").setCity("Vendorville").setStateOrProvince("VS").setPostalCode("V0V0V0").setCountryCode("VC").build();
        AddressAvro dropoffAddr = AddressAvro.newBuilder().setStreet("100 End Rd").setCity("Custburg").setStateOrProvince("CS").setPostalCode("C0C0C0").setCountryCode("CC").build();

        ShipmentRequestedEvent shipmentEvent = ShipmentRequestedEvent.newBuilder()
                .setOrderId(orderId.toString())
                .setVendorId(vendorId.toString())
                .setCustomerId(customerId.toString())
                .setPickupAddress(pickupAddr)
                .setDropoffAddress(dropoffAddr)
                .build();

        // Create a dummy courier profile for assignment
        DeliveryProfile courier = new DeliveryProfile(UUID.randomUUID(), "Test Courier", "555-1234", null, "ACTIVE", null, null, null, null, null);
        // Must save it using Autowired repository if service relies on it being in DB
        // For now, assume service can find/create one or it's mocked if this were unit test.
        // In integration test, service will query DB. So, we need to create one.
        // @Autowired DeliveryProfileRepository profileRepository; // Add to class
        // profileRepository.save(courier); // This needs profileRepository to be autowired

        log.info("Publishing ShipmentRequestedEvent to topic {}: {}", orderShipmentRequestedTopic, shipmentEvent);
        testAvroKafkaProducer.send(orderShipmentRequestedTopic, shipmentEvent.getOrderId(), shipmentEvent).get(10, TimeUnit.SECONDS);

        // 2. Verify DeliveryAssignment entity is created in DB
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> { // Increased timeout for Kafka + DB
            DeliveryAssignment assignment = assignmentRepository.findByOrderId(orderId).orElse(null);
            assertThat(assignment).isNotNull();
            assertThat(assignment.getStatus()).isEqualTo(DeliveryAssignmentStatus.ASSIGNED);
            assertThat(assignment.getVendorId()).isEqualTo(vendorId);
            // Courier ID would be assigned by the service logic (e.g. first available)
            assertThat(assignment.getCourier()).isNotNull(); // Check if a courier was assigned
        });

        // 3. Verify OutboxEvent is created for delivery.assignment.created
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(outboxRepository.count()).isGreaterThanOrEqualTo(1);
        });

        // 4. Verify DeliveryService publishes DeliveryAssignmentCreatedEvent (Avro) via OutboxPoller
        ConsumerRecord<String, Object> outputRecord = consumedDeliveryEvents.poll(20, TimeUnit.SECONDS); // Increased timeout for poller
        assertThat(outputRecord).isNotNull();
        assertThat(outputRecord.topic()).isEqualTo(deliveryAssignmentCreatedTopic);
        assertThat(outputRecord.value()).isInstanceOf(DeliveryAssignmentCreatedEvent.class);

        DeliveryAssignmentCreatedEvent daCreatedEvent = (DeliveryAssignmentCreatedEvent) outputRecord.value();
        DeliveryAssignment createdAssignmentInDb = assignmentRepository.findByOrderId(orderId).get();

        assertThat(daCreatedEvent.getAssignmentId()).isEqualTo(createdAssignmentInDb.getId().toString());
        assertThat(daCreatedEvent.getOrderId()).isEqualTo(orderId.toString());
        assertThat(daCreatedEvent.getCourierId()).isEqualTo(createdAssignmentInDb.getCourier().getId().toString());
    }

    // TODO: Test for markPickedUp flow:
    // 1. Create an assignment (as above, or directly in DB for test setup).
    // 2. Make a POST request to /delivery/assignments/{id}/pickup-photo.
    //    - This requires a way to get a Keycloak token for "DELIVERY" role if security is active.
    // 3. Verify DB status changes to PICKED_UP.
    // 4. Verify OutboxEvent for delivery.picked_up.
    // 5. Verify DeliveryPickedUpEvent (Avro) on Kafka via consumedDeliveryEvents.

    // TODO: Test for GPS update publishing (direct Kafka, no outbox).
    // TODO: Test for markDelivered flow.
    // TODO: Test WebSocket GPS updates (more complex, involves setting up WS client).
}
