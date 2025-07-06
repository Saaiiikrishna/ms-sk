package com.mysillydreams.e2etests;

import com.mysillydreams.orderapi.OrderApiApplication; // Would need these on classpath
import com.mysillydreams.ordercore.OrderCoreApplication; // Would need these on classpath

// Import Avro classes (assuming they are in a shared module or generated here too for test validation)
// Example: import com.mysillydreams.orderapi.dto.avro.OrderCreatedEvent as ApiOrderCreatedEvent;
// Example: import com.mysillydreams.ordercore.dto.avro.OrderCreatedEvent as CoreOrderCreatedEvent;

import dasniko.testcontainers.keycloak.KeycloakContainer; // Keycloak Testcontainer
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.*;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension; // Not a Testcontainers specific annotation for class
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers // Enables Testcontainers JUnit 5 extension
// @ExtendWith(SpringExtension.class) // Not strictly needed if not using @SpringBootTest on this E2E class itself directly for context
public class FullSagaSmokeTest {

    private static final Logger log = LoggerFactory.getLogger(FullSagaSmokeTest.class);

    private static Network network = Network.newNetwork();

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:13.3"))
            .withNetwork(network).withNetworkAliases("postgres");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:6-alpine"))
            .withNetwork(network).withNetworkAliases("redis").withExposedPorts(6379);

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.2.1"))
            .withNetwork(network).withNetworkAliases("kafka");

    @Container
    static GenericContainer<?> schemaRegistry = new GenericContainer<>(DockerImageName.parse("confluentinc/cp-schema-registry:7.2.1"))
            .withNetwork(network).withNetworkAliases("schema-registry")
            .withExposedPorts(8081)
            .withEnv("SCHEMA_REGISTRY_HOST_NAME", "schema-registry")
            .withEnv("SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS", "PLAINTEXT://kafka:9092")
            .dependsOn(kafka);

    @Container
    static KeycloakContainer keycloak = new KeycloakContainer("keycloak/keycloak:19.0.3") // Use a version consistent with app
            .withRealmImportFile("keycloak-realm-export.json") // Assume a realm export for users/clients
            .withNetwork(network).withNetworkAliases("keycloak");


    // Spring Boot applications - to be started programmatically
    private static ConfigurableApplicationContext orderApiContext;
    private static ConfigurableApplicationContext orderCoreContext;

    private static String orderApiBaseUrl;
    private static String orderCoreInternalBaseUrl;


    @DynamicPropertySource // This method provides properties to the Spring contexts started below
    static void registerDynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("DB_HOST", () -> "postgres"); // Using network alias
        registry.add("DB_PORT", () -> 5432); // Default postgres port
        registry.add("DB_NAME", postgres::getDatabaseName);
        registry.add("DB_USER", postgres::getUsername);
        registry.add("DB_PASS", postgres::getPassword);

        registry.add("REDIS_HOST", () -> "redis");
        registry.add("REDIS_PORT", redis::getFirstMappedPort);

        registry.add("KAFKA_BROKER", kafka::getBootstrapServers);
        registry.add("SCHEMA_REGISTRY_URL", () -> "http://localhost:" + schemaRegistry.getMappedPort(8081)); // Use mapped port for client access

        registry.add("KEYCLOAK_URL", keycloak::getAuthServerUrl);
        // Secrets for clients (order-api-client, order-core-client) would be in realm export or set via Keycloak admin REST API
        // For simplicity, assume clients are configured in realm export.
    }

    @BeforeAll
    static void startApplications() {
        // Start Testcontainers (JUnit5 @Container handles this automatically)
        // postgres.start(); keycloak.start(); redis.start(); kafka.start(); schemaRegistry.start();

        // Programmatically start Spring Boot applications
        // Pass Testcontainer-derived properties to them
        Map<String, Object> appProps = new HashMap<>();
        appProps.put("spring.datasource.url", postgres.getJdbcUrl());
        appProps.put("spring.datasource.username", postgres.getUsername());
        appProps.put("spring.datasource.password", postgres.getPassword());
        appProps.put("spring.flyway.url", postgres.getJdbcUrl());
        appProps.put("spring.flyway.user", postgres.getUsername());
        appProps.put("spring.flyway.password", postgres.getPassword());

        appProps.put("spring.redis.host", redis.getHost());
        appProps.put("spring.redis.port", redis.getMappedPort(6379).toString());

        appProps.put("kafka.bootstrap-servers", kafka.getBootstrapServers());
        appProps.put("kafka.schema-registry-url", "http://" + schemaRegistry.getHost() + ":" + schemaRegistry.getMappedPort(8081));

        appProps.put("keycloak.auth-server-url", keycloak.getAuthServerUrl());
        // Set client secrets if they are not in realm import and need to be passed as properties
        // appProps.put("keycloak.credentials.secret", "order-api-client-secret"); // For Order-API

        // Start Order-API
        // Assign a random port for Order-API
        int orderApiPort = findFreePort();
        orderApiBaseUrl = "http://localhost:" + orderApiPort;
        appProps.put("server.port", String.valueOf(orderApiPort));
        orderApiContext = new SpringApplicationBuilder(OrderApiApplication.class)
            .properties(appProps)
            // .profiles("test", "e2e") // Optional: specific profiles for E2E tests
            .run();
        log.info("OrderApiApplication started on port {}", orderApiPort);

        // Start Order-Core
        // Assign a random port for Order-Core's internal API (if active)
        int orderCorePort = findFreePort();
        orderCoreInternalBaseUrl = "http://localhost:" + orderCorePort;
        // Create a new map or modify for Order-Core specific properties if needed
        Map<String, Object> orderCoreAppProps = new HashMap<>(appProps);
        orderCoreAppProps.put("server.port", String.valueOf(orderCorePort));
        // orderCoreAppProps.put("keycloak.credentials.secret", "order-core-client-secret"); // For Order-Core
        orderCoreContext = new SpringApplicationBuilder(OrderCoreApplication.class)
            .properties(orderCoreAppProps)
            .run();
        log.info("OrderCoreApplication started on port {}", orderCorePort);

        // Wait for applications to be ready (e.g., health checks)
        // This is a simple wait, actual health checks would be better.
        try { Thread.sleep(15000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        log.info("Assumed applications are ready.");
    }

    @AfterAll
    static void stopApplications() {
        if (orderApiContext != null) {
            orderApiContext.close();
        }
        if (orderCoreContext != null) {
            orderCoreContext.close();
        }
        // Testcontainers are stopped automatically by JUnit5 extension
    }

    @Test
    void fullOrderSagaSmokeTest() {
        log.info("Starting full order saga smoke test...");
        TestRestTemplate restTemplate = new TestRestTemplate();

        // Step 1: Create Order via Order-API
        String customerId = UUID.randomUUID().toString();
        String idempotencyKey = UUID.randomUUID().toString();
        String createOrderPayload = String.format("{\"items\":[{\"productId\":\"%s\",\"quantity\":1,\"price\":99.99}],\"currency\":\"USD\"}", UUID.randomUUID().toString());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", idempotencyKey);
        // For Keycloak: Obtain a token and add it to Authorization header
        // String authToken = obtainTokenForTestUser(); // Placeholder for token logic
        // headers.setBearerAuth(authToken);


        HttpEntity<String> requestEntity = new HttpEntity<>(createOrderPayload, headers);
        ResponseEntity<String> createResponse = restTemplate.postForEntity(orderApiBaseUrl + "/orders", requestEntity, String.class);

        log.info("Order-API create response: {} - {}", createResponse.getStatusCode(), createResponse.getBody());
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        // Extract orderId (assuming JSON response with "orderId" field)
        // String orderId = ... extract from createResponse.getBody() using Jackson or similar ...
        // For now, let's assume we get an orderId. This part needs robust JSON parsing.
        // String orderId = "mock-order-id-from-response"; // Placeholder

        // TODO:
        // 2. Verify OrderCreatedEvent on Kafka (from Order-API, consumed by Order-Core).
        //    - This requires a Kafka consumer setup in the test to listen to the relevant topic.
        //    - Deserialize the Avro event and validate its contents.

        // 3. Simulate downstream events (Inventory Reserved, Payment Succeeded) by publishing them to Kafka.
        //    - These events would be consumed by Order-Core's saga listeners.
        //    - Example: Publish ReservationSucceededEvent (Avro)
        //      ReservationSucceededEvent resSuccess = new ReservationSucceededEvent(orderId, UUID.randomUUID().toString());
        //      getTestKafkaTemplate().send("inventory.reservation.succeeded", orderId, resSuccess);

        // 4. Verify Order status changes in Order-Core DB or via its Internal API.
        //    - After ReservationSucceededEvent, check if status becomes PAID.
        //    - After PaymentSucceededEvent, check if status becomes CONFIRMED.
        //    - Example: ResponseEntity<String> coreGetResponse = restTemplate.getForEntity(orderCoreInternalBaseUrl + "/internal/orders/" + orderId, String.class);
        //               assertThat(coreGetResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        //               // Parse response and check status field

        // 5. (Optional) Test cancellation flow.

        log.warn("E2E test is a scaffold. Full event flow, Kafka consumption/production in test, and detailed assertions need implementation.");
        assertThat(true).as("Placeholder assertion for E2E test structure").isTrue();
    }

    private static int findFreePort() {
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        } catch (java.io.IOException e) {
            throw new RuntimeException("Unable to allocate free port", e);
        }
    }

    // Helper to get a KafkaTemplate configured for Avro for publishing test events
    /*
    private KafkaTemplate<String, Object> getTestKafkaTemplate() {
        Map<String, Object> producerProps = new HashMap<>();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class);
        producerProps.put("schema.registry.url", "http://localhost:" + schemaRegistry.getMappedPort(8081));
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(producerProps));
    }
    */
    // Helper to obtain Keycloak token (complex, involves REST calls to Keycloak or using a library)
    /*
    private String obtainTokenForTestUser() {
        // ... logic to get token from Keycloak container for a test user ...
        return "dummy-token";
    }
    */
}
