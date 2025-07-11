package com.mysillydreams.payment.config;

import org.springframework.context.annotation.Configuration;

// Basic Flyway configuration. Spring Boot auto-configures Flyway if it's on the classpath
// and a DataSource is available. This class can be used for customizations if needed.
// By default, migration scripts are expected in "classpath:db/migration".

@Configuration
public class FlywayConfig {
    // No beans defined here by default, allowing Spring Boot's auto-configuration for Flyway.
    // Add FlywayConfigurationCustomizer or a direct Flyway bean if specific customizations are needed.
    // Example:
    /*
    @Bean
    public FlywayConfigurationCustomizer flywayCustomizer() {
        return configuration -> {
            // configuration.baselineVersion("0");
            // configuration.locations("classpath:db/custom_migrations");
        };
    }
    */
}
