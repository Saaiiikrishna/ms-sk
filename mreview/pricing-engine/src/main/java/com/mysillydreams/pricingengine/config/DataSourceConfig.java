package com.mysillydreams.pricingengine.config;

import org.springframework.context.annotation.Configuration;

/**
 * Data source specific configurations.
 * Most configurations will be handled by Spring Boot auto-configuration
 * based on application.yml properties. This class can be used for
 * more specific programmatic configuration if needed.
 */
@Configuration
public class DataSourceConfig {
    // Example: If you needed to configure a DataSource bean programmatically,
    // you could do it here. However, Spring Boot typically handles this
    // based on properties in application.yml (e.g., spring.datasource.url).

    // @Bean
    // public DataSource dataSource(@Value("${spring.datasource.url}") String url,
    //                              @Value("${spring.datasource.username}") String username,
    //                              @Value("${spring.datasource.password}") String password,
    //                              @Value("${spring.datasource.driver-class-name}") String driverClassName) {
    //     DriverManagerDataSource dataSource = new DriverManagerDataSource();
    //     dataSource.setDriverClassName(driverClassName);
    //     dataSource.setUrl(url);
    //     dataSource.setUsername(username);
    //     dataSource.setPassword(password);
    //     return dataSource;
    // }
}
