package com.mysillydreams.auth.config;

import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.lifecycle.Startables;

public class PostgresTestContainerInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final PostgreSQLContainer<?> postgresContainer =
            new PostgreSQLContainer<>("postgres:14-alpine")
                    .withDatabaseName("testauthdb")
                    .withUsername("testuser")
                    .withPassword("testpass");

    static {
        Startables.deepStart(postgresContainer).join();
         // Alternatively, could use .start() but deepStart is more thorough for dependent containers if any were nested.
    }

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        TestPropertyValues.of(
                "spring.datasource.url=" + postgresContainer.getJdbcUrl(),
                "spring.datasource.username=" + postgresContainer.getUsername(),
                "spring.datasource.password=" + postgresContainer.getPassword(),
                "spring.jpa.hibernate.ddl-auto=create-drop" // Ensure schema is created for tests
        ).applyTo(applicationContext.getEnvironment());
    }
}
