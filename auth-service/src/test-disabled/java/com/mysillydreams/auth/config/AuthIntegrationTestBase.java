package com.mysillydreams.auth.config;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.lifecycle.Startables;

import java.util.stream.Stream;

@ActiveProfiles("test")
@ContextConfiguration(initializers = AuthIntegrationTestBase.Initializer.class)
public abstract class AuthIntegrationTestBase {

    private static final Logger logger = LoggerFactory.getLogger(AuthIntegrationTestBase.class);

    public static PostgreSQLContainer<?> postgresContainer =
            new PostgreSQLContainer<>("postgres:14-alpine")
                    .withDatabaseName("test_auth_db")
                    .withUsername("testauth")
                    .withPassword("testauthpass");

    public static KeycloakContainer keycloakContainer = new KeycloakContainer("quay.io/keycloak/keycloak:19.0.3")
            .withRealmImportFile("keycloak/auth-service-test-realm.json") // Will create this file next
            .waitingFor(Wait.forHttp("/auth").forStatusCode(200));

    static {
        logger.info("Starting PostgreSQL and Keycloak Testcontainers for Auth Service integration tests...");
        // keycloakContainer.withReuse(true); // Optional: for faster test runs locally, disable for CI
        // postgresContainer.withReuse(true);
        Startables.deepStart(Stream.of(postgresContainer, keycloakContainer)).join();
        logger.info("PostgreSQL JDBC URL for Auth Service: {}", postgresContainer.getJdbcUrl());
        logger.info("Keycloak Auth Server URL for Auth Service: {}", keycloakContainer.getAuthServerUrl());
        logger.info("Testcontainers for Auth Service started.");
    }

    public static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext applicationContext) {
            String keycloakAuthServerUrl = keycloakContainer.getAuthServerUrl();
            logger.info("Keycloak auth server URL for Initializer: {}", keycloakAuthServerUrl);

            TestPropertyValues.of(
                    "spring.datasource.url=" + postgresContainer.getJdbcUrl(),
                    "spring.datasource.username=" + postgresContainer.getUsername(),
                    "spring.datasource.password=" + postgresContainer.getPassword(),
                    "spring.jpa.hibernate.ddl-auto=create-drop",

                    "app.simple-encryption.secret-key=TestSecretKeyForAuthService123456", // 16, 24, or 32 bytes. This is 32.

                    "keycloak.realm=AuthTestRealm", // Must match realm name in auth-service-test-realm.json
                    "keycloak.auth-server-url=" + keycloakAuthServerUrl,
                    "keycloak.resource=auth-service-client", // Client ID in auth-service-test-realm.json
                    "keycloak.credentials.secret=test-client-secret", // Client secret
                    "keycloak.ssl-required=none",
                    "keycloak.bearer-only=false", // For testing login
                    "keycloak.principal-attribute=sub", // Use 'sub' (subject) as principal name (usually user ID)
                    // For Keycloak Admin Client (used by PasswordRotationService)
                    // Ensure the client 'auth-service-client' has service account enabled and 'manage-users' role for 'realm-management' client.
                    // Or use a different client for admin operations.
                    // The PasswordRotationService uses @Value for these, so they should be set by properties:
                    // keycloak.auth-server-url (already set)
                    // keycloak.realm (already set)
                    // keycloak.resource (already set - this is client ID)
                    // keycloak.credentials.secret (already set)
                    "logging.level.org.keycloak=INFO", // DEBUG for verbose Keycloak adapter logging
                    "logging.level.com.mysillydreams.auth=DEBUG"

            ).applyTo(applicationContext.getEnvironment());
            logger.info("Applied Auth Service Testcontainer properties to Spring context.");
        }
    }
}
