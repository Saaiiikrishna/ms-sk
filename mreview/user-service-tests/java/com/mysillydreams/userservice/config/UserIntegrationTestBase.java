package com.mysillydreams.userservice.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.VaultContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;


import java.util.stream.Stream;

@ActiveProfiles("test")
@ContextConfiguration(initializers = UserIntegrationTestBase.Initializer.class)
public abstract class UserIntegrationTestBase {

    private static final Logger logger = LoggerFactory.getLogger(UserIntegrationTestBase.class);

    protected static final String VAULT_TOKEN = "testroottoken";
    protected static final String TRANSIT_KEY_NAME = "user-service-key"; // Must match application-test.yml or default

    public static PostgreSQLContainer<?> postgresContainer =
            new PostgreSQLContainer<>("postgres:14-alpine")
                    .withDatabaseName("test_user_db")
                    .withUsername("testuser")
                    .withPassword("testpass");

    @SuppressWarnings("rawtypes") // VaultContainer is raw type
    public static VaultContainer vaultContainer =
            new VaultContainer<>("vault:1.13.3")
                    .withVaultToken(VAULT_TOKEN)
                    .withInitCommand(
                        "secrets enable transit",
                        "write -f transit/keys/" + TRANSIT_KEY_NAME
                    );

    public static LocalStackContainer localstack =
            new LocalStackContainer(DockerImageName.parse("localstack/localstack:1.4.0")) // Use a recent version
                    .withServices(LocalStackContainer.Service.S3);
                    // .withEnv("DEBUG", "1"); // For debugging LocalStack

    static {
        logger.info("Starting PostgreSQL, Vault, and LocalStack Testcontainers for User Service integration tests...");
        Startables.deepStart(Stream.of(postgresContainer, vaultContainer, localstack)).join();
        logger.info("PostgreSQL JDBC URL: {}", postgresContainer.getJdbcUrl());
        logger.info("Vault Address: {}", vaultContainer.getHttpHostAddress());
        logger.info("LocalStack S3 Endpoint: {}", localstack.getEndpointOverride(LocalStackContainer.Service.S3).toString());
        logger.info("Vault Token: {}", VAULT_TOKEN);
        logger.info("Testcontainers started.");
    }

    public static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext applicationContext) {
            TestPropertyValues.of(
                    "spring.datasource.url=" + postgresContainer.getJdbcUrl(),
                    "spring.datasource.username=" + postgresContainer.getUsername(),
                    "spring.datasource.password=" + postgresContainer.getPassword(),
                    "spring.jpa.hibernate.ddl-auto=create-drop",

                    "spring.cloud.vault.host=" + vaultContainer.getHost(),
                    "spring.cloud.vault.port=" + vaultContainer.getFirstMappedPort(),
                    "spring.cloud.vault.scheme=http", // Testcontainer Vault runs on HTTP
                    "spring.cloud.vault.authentication=TOKEN", // Use token auth for test container
                    "spring.cloud.vault.token=" + VAULT_TOKEN,
                    "spring.cloud.vault.kv.enabled=false",
                    "spring.cloud.vault.transit.default-key-name=" + TRANSIT_KEY_NAME,
                    "spring.cloud.vault.fail-fast=true",

                    // S3 properties for DocumentService tests
                    "vendor.s3.endpoint-override=" + localstack.getEndpointOverride(LocalStackContainer.Service.S3).toString(),
                    "vendor.s3.region=" + localstack.getRegion(), // Use LocalStack's region
                    "vendor.s3.bucket=test-bucket" // Define a test bucket name, ensure it's created if needed
                    // AWS SDK specific properties for test if needed (e.g. dummy access/secret keys for LocalStack)
                    // "aws.accessKeyId=test",
                    // "aws.secretKey=test"
                    // Spring Cloud AWS would use these, but SDK v2 default provider chain should also work with endpoint override for LocalStack
            ).applyTo(applicationContext.getEnvironment());
            logger.info("Applied Testcontainer properties to Spring context.");
        }
    }
}
