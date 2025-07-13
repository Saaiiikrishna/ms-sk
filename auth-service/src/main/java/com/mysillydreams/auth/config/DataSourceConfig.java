package com.mysillydreams.auth.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;

import jakarta.annotation.PostConstruct;
import javax.sql.DataSource;

/**
 * Custom DataSource configuration that loads properties from Zookeeper
 * after the Spring context is initialized.
 * 
 * This ensures that Zookeeper properties are available before DataSource creation.
 */
@Configuration
public class DataSourceConfig {

    private static final Logger logger = LoggerFactory.getLogger(DataSourceConfig.class);

    @Autowired
    private Environment environment;

    private String url;
    private String username;
    private String password;
    private String driverClassName;

    @PostConstruct
    public void loadDataSourceProperties() {
        logger.info("Loading DataSource properties from Environment...");
        
        // Load properties from Environment (includes Zookeeper properties)
        url = environment.getProperty("spring.datasource.url");
        username = environment.getProperty("spring.datasource.username");
        password = environment.getProperty("spring.datasource.password");
        driverClassName = environment.getProperty("spring.datasource.driver-class-name");
        
        logger.info("DataSource URL loaded: {}", url != null ? "YES" : "NO");
        logger.info("DataSource username loaded: {}", username != null ? "YES" : "NO");
        logger.info("DataSource password loaded: {}", password != null ? "YES" : "NO");
        logger.info("DataSource driver loaded: {}", driverClassName != null ? "YES" : "NO");
        
        // Try to get properties from specific property sources if not found in Environment
        if (url == null && environment instanceof ConfigurableEnvironment) {
            ConfigurableEnvironment configurableEnv = (ConfigurableEnvironment) environment;
            logger.info("Searching through {} property sources for DataSource properties...",
                       configurableEnv.getPropertySources().size());

            configurableEnv.getPropertySources().forEach(ps -> {
                logger.info("Checking property source: {} (type: {})", ps.getName(), ps.getClass().getSimpleName());

                if (ps.getName().contains("zookeeper") || ps.getName().contains("bootstrap") ||
                    ps.getName().contains("/mysillydreams")) {
                    logger.info("Found potential Zookeeper/Bootstrap property source: {}", ps.getName());

                    // Try to get properties directly from the property source
                    Object urlValue = ps.getProperty("spring.datasource.url");
                    Object usernameValue = ps.getProperty("spring.datasource.username");
                    Object passwordValue = ps.getProperty("spring.datasource.password");
                    Object driverValue = ps.getProperty("spring.datasource.driver-class-name");

                    logger.info("Direct property access from {}: URL={}, Username={}, Password={}, Driver={}",
                               ps.getName(),
                               urlValue != null ? "FOUND" : "NULL",
                               usernameValue != null ? "FOUND" : "NULL",
                               passwordValue != null ? "FOUND" : "NULL",
                               driverValue != null ? "FOUND" : "NULL");

                    if (urlValue != null && url == null) {
                        logger.info("Found DataSource properties in property source: {}", ps.getName());
                        url = (String) urlValue;
                        username = (String) usernameValue;
                        password = (String) passwordValue;
                        driverClassName = (String) driverValue;
                    }

                    // Also try Map access for other property sources
                    if (ps.getSource() instanceof java.util.Map) {
                        @SuppressWarnings("unchecked")
                        java.util.Map<String, Object> map = (java.util.Map<String, Object>) ps.getSource();
                        logger.info("Property source {} contains {} properties", ps.getName(), map.size());

                        // Log some key properties to debug
                        map.keySet().stream()
                           .filter(key -> key.toString().contains("datasource") || key.toString().contains("jwt"))
                           .forEach(key -> logger.info("  Found property: {} = {}", key, map.get(key)));

                        if (map.containsKey("spring.datasource.url") && url == null) {
                            logger.info("Found DataSource properties in Map from property source: {}", ps.getName());
                            url = (String) map.get("spring.datasource.url");
                            username = (String) map.get("spring.datasource.username");
                            password = (String) map.get("spring.datasource.password");
                            driverClassName = (String) map.get("spring.datasource.driver-class-name");
                        }
                    }
                }
            });
        }
        
        logger.info("Final DataSource properties loaded - URL: {}, Username: {}, Driver: {}", 
                   url != null ? "YES" : "NO", 
                   username != null ? "YES" : "NO", 
                   driverClassName != null ? "YES" : "NO");
    }

    @Bean
    @ConditionalOnMissingBean(DataSource.class)
    public DataSource dataSource() {
        logger.info("Creating DataSource with loaded properties...");
        
        if (url == null || username == null || password == null || driverClassName == null) {
            throw new IllegalStateException("DataSource properties not loaded from Zookeeper. " +
                    "URL: " + (url != null ? "OK" : "MISSING") + 
                    ", Username: " + (username != null ? "OK" : "MISSING") + 
                    ", Password: " + (password != null ? "OK" : "MISSING") + 
                    ", Driver: " + (driverClassName != null ? "OK" : "MISSING"));
        }
        
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName(driverClassName);
        
        // Set reasonable connection pool settings
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        config.setLeakDetectionThreshold(60000);
        
        logger.info("DataSource created successfully with URL: {}", url);
        return new HikariDataSource(config);
    }
}
