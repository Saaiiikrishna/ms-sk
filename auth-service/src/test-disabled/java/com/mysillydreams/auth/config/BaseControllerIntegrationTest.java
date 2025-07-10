package com.mysillydreams.auth.config;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.lifecycle.Startables;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

@ActiveProfiles("test") // Ensure a specific test profile is active if needed
@ContextConfiguration(initializers = BaseControllerIntegrationTest.Initializer.class)
public abstract class BaseControllerIntegrationTest {

    private static final Logger logger = LoggerFactory.getLogger(BaseControllerIntegrationTest.class);

    protected static final String TEST_REALM = "TestRealm";
    protected static final String TEST_CLIENT_ID = "auth-service-client-test"; // Match resource in application-test.yml
    protected static final String TEST_CLIENT_SECRET = "test-secret";
    protected static final String ADMIN_USER = "adminuser";
    protected static final String ADMIN_PASSWORD = "adminpassword";
    protected static final String NORMAL_USER = "normaluser";
    protected static final String NORMAL_PASSWORD = "normalpassword";

    public static PostgreSQLContainer<?> postgresContainer =
            new PostgreSQLContainer<>("postgres:14-alpine")
                    .withDatabaseName("testauthdb_it")
                    .withUsername("testuser_it")
                    .withPassword("testpass_it");

    // Use dasniko's KeycloakContainer for easier admin user setup
    public static KeycloakContainer keycloakContainer =
            new KeycloakContainer("quay.io/keycloak/keycloak:19.0.3") // Use same version as in pom.xml if possible
                    .withRealmImportFile("keycloak/test-realm.json") // We'll create this file
                    .withAdminUsername("admin") // Keycloak's own admin for master realm
                    .withAdminPassword("admin");


    @BeforeAll
    static void startContainers(@Autowired EmbeddedKafkaBroker embeddedKafkaBroker) {
        logger.info("Starting Testcontainers and Embedded Kafka...");
        Startables.deepStart(Stream.of(postgresContainer, keycloakContainer)).join();
        logger.info("PostgreSQL JDBC URL: {}", postgresContainer.getJdbcUrl());
        logger.info("Keycloak Auth Server URL: {}", keycloakContainer.getAuthServerUrl());
        logger.info("Embedded Kafka Brokers: {}", embeddedKafkaBroker.getBrokersAsString());
        // Create topics in EmbeddedKafka
        createTopics(embeddedKafkaBroker.getBrokersAsString(), "auth.events");
        logger.info("Testcontainers and Embedded Kafka started.");
    }

    private static void createTopics(String bootstrapServers, String... topics) {
        Map<String, Object> config = new HashMap<>();
        config.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        try (AdminClient admin = AdminClient.create(config)) {
            List<NewTopic> newTopics = Arrays.stream(topics)
                .map(topic -> new NewTopic(topic, 1, (short) 1))
                .toList();
            admin.createTopics(newTopics).all().get();
            logger.info("Topics created: {}", Arrays.toString(topics));
        } catch (ExecutionException | InterruptedException e) {
            if (e.getCause() instanceof org.apache.kafka.common.errors.TopicExistsException) {
                logger.warn("Topic(s) already exist: {}", Arrays.toString(topics));
            } else {
                logger.error("Error creating Kafka topics", e);
                throw new RuntimeException("Failed to create Kafka topics", e);
            }
        }
    }


    @AfterAll
    static void stopContainers() {
        logger.info("Stopping Testcontainers...");
        if (keycloakContainer != null) keycloakContainer.stop();
        if (postgresContainer != null) postgresContainer.stop();
        logger.info("Testcontainers stopped.");
    }

    public static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext configurableApplicationContext) {
            // Get broker list from the @EmbeddedKafka annotation provided EmbeddedKafkaBroker
            // This is a bit tricky as the broker isn't available statically here in the same way as Testcontainers.
            // We rely on @EmbeddedKafka being present on the test class itself.
            // For Kafka properties, it's often easier to set them in application-test.yml and ensure
            // @EmbeddedKafka uses a port that doesn't clash or let it assign dynamically.
            // Here, we assume @EmbeddedKafka on the test class will handle broker properties for Spring context.

            TestPropertyValues.of(
                    "spring.datasource.url=" + postgresContainer.getJdbcUrl(),
                    "spring.datasource.username=" + postgresContainer.getUsername(),
                    "spring.datasource.password=" + postgresContainer.getPassword(),
                    "spring.jpa.hibernate.ddl-auto=create-drop", // Important for tests
                    "keycloak.auth-server-url=" + keycloakContainer.getAuthServerUrl(),
                    "keycloak.realm=" + TEST_REALM,
                    "keycloak.resource=" + TEST_CLIENT_ID,
                    "keycloak.credentials.secret=" + TEST_CLIENT_SECRET,
                    // For Keycloak Admin Client in PasswordRotationService tests:
                    // The admin client in service might use these if configured to point to TEST_REALM
                    // Ensure application-test.yml or these properties correctly configure KeycloakAdminClientConfig
                    "logging.level.org.keycloak=DEBUG",
                    "logging.level.com.mysillydreams.auth=DEBUG"
            ).applyTo(configurableApplicationContext.getEnvironment());
        }
    }

    // Utility to create Keycloak setup programmatically if not using realm import file entirely
    // For this example, we'll rely more on test-realm.json
    protected static void setupKeycloakTestRealm() {
        // This method would typically be called if realm import file is not sufficient
        // or for more dynamic setups. For now, test-realm.json is preferred.
        try (Keycloak adminKeycloak = KeycloakBuilder.builder()
                .serverUrl(keycloakContainer.getAuthServerUrl())
                .realm("master")
                .grantType(OAuth2Constants.PASSWORD)
                .clientId("admin-cli")
                .username(keycloakContainer.getAdminUsername())
                .password(keycloakContainer.getAdminPassword())
                .build()) {

            RealmRepresentation realmRep = new RealmRepresentation();
            realmRep.setRealm(TEST_REALM);
            realmRep.setEnabled(true);
            // Define users, clients, roles here if not in JSON
            // Example: adminKeycloak.realms().create(realmRep);
            // This is complex. test-realm.json is simpler for static setup.
            logger.info("Keycloak realm '{}' setup checks can be performed here.", TEST_REALM);
        } catch (Exception e) {
            logger.error("Failed to perform additional Keycloak setup", e);
        }
    }
}
