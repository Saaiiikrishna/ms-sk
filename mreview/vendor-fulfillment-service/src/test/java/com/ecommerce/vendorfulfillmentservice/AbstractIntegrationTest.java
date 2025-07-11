package com.ecommerce.vendorfulfillmentservice;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test") // Use a dedicated test profile if needed for properties
@Testcontainers
public abstract class AbstractIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgresqlContainer = new PostgreSQLContainer<>(DockerImageName.parse("postgres:15-alpine"))
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass");

    @Container
    static final KafkaContainer kafkaContainer = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.3.2"))
            .withEmbeddedZookeeper(); // Older KafkaContainer versions might need Zookeeper, newer ones might not.
                                      // cp-kafka usually bundles ZK or uses KRaft.

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        // PostgreSQL properties
        registry.add("spring.datasource.url", postgresqlContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgresqlContainer::getUsername);
        registry.add("spring.datasource.password", postgresqlContainer::getPassword);
        registry.add("spring.flyway.url", postgresqlContainer::getJdbcUrl); // Flyway needs this too
        registry.add("spring.flyway.user", postgresqlContainer::getUsername);
        registry.add("spring.flyway.password", postgresqlContainer::getPassword);

        // Kafka properties
        registry.add("spring.kafka.bootstrap-servers", kafkaContainer::getBootstrapServers);

        // Schema Registry is not part of KafkaContainer by default.
        // For full Avro tests, a separate Schema Registry container would be needed,
        // or KafkaAvroSerializer/Deserializer configured with mock schema registry URLs if available for testing.
        // For now, tests involving Avro might need careful setup or mocking of schema registry interaction.
        // Let's assume for now that schema auto-registration by producer might work against a broker that allows it,
        // or we can use a mock schema registry URL if the client supports it.
        // Using a placeholder for schema registry URL for test profile.
        registry.add("spring.kafka.properties.schema.registry.url", () -> "mock://test-schema-registry");
        // This "mock://" URL is recognized by Confluent's MockSchemaRegistryClient for testing if it's on classpath
        // and serializer is configured to use it. If not, actual schema registry container is needed.
    }

    // Helper method to get Kafka bootstrap servers for test producers/consumers
    protected static String getKafkaBootstrapServers() {
        return kafkaContainer.getBootstrapServers();
    }
}
