package com.mysillydreams.zookeeper.service;

import com.mysillydreams.zookeeper.config.ZookeeperServiceProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Service for managing configuration templates
 * Provides secure, environment-specific configuration templates
 */
@Service
public class ConfigurationTemplateService {

    private static final Logger logger = LoggerFactory.getLogger(ConfigurationTemplateService.class);

    private final ZookeeperServiceProperties properties;

    @Autowired
    public ConfigurationTemplateService(ZookeeperServiceProperties properties) {
        this.properties = properties;
    }

    /**
     * Get configuration template for a service in a specific environment
     */
    public String getConfigurationTemplate(String environment, String service) {
        logger.debug("Getting configuration template for service: {} in environment: {}", service, environment);
        
        validateEnvironment(environment);
        validateService(service);
        
        return switch (service.toLowerCase()) {
            case "auth-service" -> getAuthServiceTemplate(environment);
            case "user-service" -> getUserServiceTemplate(environment);
            case "api-gateway" -> getApiGatewayTemplate(environment);
            case "admin-server" -> getAdminServerTemplate(environment);
            default -> getDefaultServiceTemplate(environment, service);
        };
    }

    /**
     * Get base configuration that's common across all services
     */
    public Map<String, String> getBaseConfiguration(String environment) {
        Map<String, String> baseConfig = new HashMap<>();
        
        // Eureka configuration
        baseConfig.put("eureka.client.service-url.defaultZone", 
            String.format("http://eureka-server.mysillydreams-%s:8761/eureka/", environment));
        baseConfig.put("eureka.client.fetch-registry", "true");
        baseConfig.put("eureka.client.register-with-eureka", "true");
        baseConfig.put("eureka.instance.prefer-ip-address", "true");
        baseConfig.put("eureka.instance.lease-renewal-interval-in-seconds", "10");
        baseConfig.put("eureka.instance.lease-expiration-duration-in-seconds", "30");
        
        // Management endpoints
        baseConfig.put("management.endpoints.web.exposure.include", "health,info,metrics,prometheus");
        baseConfig.put("management.endpoint.health.show-details", "when-authorized");
        baseConfig.put("management.endpoint.health.probes.enabled", "true");
        baseConfig.put("management.health.livenessstate.enabled", "true");
        baseConfig.put("management.health.readinessstate.enabled", "true");
        baseConfig.put("management.security.enabled", "false");
        
        // Zipkin tracing
        baseConfig.put("management.zipkin.tracing.endpoint", 
            String.format("http://zipkin.mysillydreams-%s:9411/api/v2/spans", environment));
        
        // Vault configuration
        baseConfig.put("vault.uri", String.format("http://vault.mysillydreams-%s:8200", environment));
        baseConfig.put("vault.token", "${VAULT_TOKEN:dev-only-token}");
        
        // Logging configuration
        baseConfig.put("logging.level.root", "${LOG_LEVEL_ROOT:INFO}");
        baseConfig.put("logging.level.org.springframework.security", "${LOG_LEVEL_SECURITY:WARN}");
        baseConfig.put("logging.level.org.springframework.web", "${LOG_LEVEL_WEB:WARN}");
        
        return baseConfig;
    }

    private String getAuthServiceTemplate(String environment) {
        Map<String, String> config = new HashMap<>(getBaseConfiguration(environment));
        
        // Database configuration
        config.put("spring.datasource.url", 
            String.format("jdbc:postgresql://postgres-auth.mysillydreams-%s:5432/authdb", environment));
        config.put("spring.datasource.username", "${DB_AUTH_USERNAME:" + System.getenv().getOrDefault("DB_AUTH_USERNAME", "authuser") + "}");
        config.put("spring.datasource.password", "${DB_AUTH_PASSWORD:" + System.getenv().getOrDefault("DB_AUTH_PASSWORD", "authpass123") + "}");
        config.put("spring.datasource.driver-class-name", "org.postgresql.Driver");
        
        // JPA configuration
        config.put("spring.jpa.database-platform", "org.hibernate.dialect.PostgreSQLDialect");
        config.put("spring.jpa.hibernate.ddl-auto", "${JPA_DDL_AUTO:validate}");
        
        // Redis configuration
        config.put("spring.redis.host", String.format("redis.mysillydreams-%s", environment));
        config.put("spring.redis.port", "6379");
        config.put("spring.redis.database", "0");
        config.put("spring.redis.timeout", "2000ms");
        config.put("spring.redis.password", "${REDIS_PASSWORD:" + System.getenv().getOrDefault("REDIS_PASSWORD", "") + "}");

        // JWT configuration
        config.put("jwt.secret", System.getenv().getOrDefault("JWT_SECRET", "mySecretKey123456789012345678901234567890"));
        config.put("jwt.expiration-ms", "${JWT_EXPIRATION_MS:86400000}");

        // Keycloak configuration
        config.put("keycloak.auth-server-url",
            String.format("http://keycloak.mysillydreams-%s:8080", environment));
        config.put("keycloak.realm", "${KEYCLOAK_REALM:MySillyDreams-Realm}");
        config.put("keycloak.admin-client.client-id", "${KEYCLOAK_CLIENT_ID:admin-cli}");
        config.put("keycloak.admin-client.username", "${KEYCLOAK_USERNAME:" + System.getenv().getOrDefault("KEYCLOAK_USERNAME", "admin") + "}");
        config.put("keycloak.admin-client.password", "${KEYCLOAK_PASSWORD:" + System.getenv().getOrDefault("KEYCLOAK_PASSWORD", "admin123") + "}");

        // Application specific
        config.put("app.simple-encryption.secret-key", System.getenv().getOrDefault("APP_ENCRYPTION_KEY", "TestEncryptionKeyForProduction123456789!"));
        config.put("app.mfa.issuer-name", "${MFA_ISSUER:MySillyDreamsPlatform}");
        config.put("app.internal-api.secret-key", System.getenv().getOrDefault("INTERNAL_API_KEY", "TestInternalApiKeyForProduction123456789!"));
        config.put("app.cors.allowed-origins", "${CORS_ORIGINS:http://localhost:3000}");
        
        // Service-specific logging
        config.put("logging.level.com.mysillydreams.auth", "${LOG_LEVEL_AUTH:INFO}");
        
        return convertMapToPropertiesString(config);
    }

    private String getUserServiceTemplate(String environment) {
        Map<String, String> config = new HashMap<>(getBaseConfiguration(environment));
        
        // Database configuration
        config.put("spring.datasource.url", 
            String.format("jdbc:postgresql://postgres-user.mysillydreams-%s:5432/userdb", environment));
        config.put("spring.datasource.username", "${DB_USER_USERNAME:" + System.getenv().getOrDefault("DB_USER_USERNAME", "useruser") + "}");
        config.put("spring.datasource.password", "${DB_USER_PASSWORD:" + System.getenv().getOrDefault("DB_USER_PASSWORD", "userpass123") + "}");
        config.put("spring.datasource.driver-class-name", "org.postgresql.Driver");

        // JPA configuration
        config.put("spring.jpa.database-platform", "org.hibernate.dialect.PostgreSQLDialect");
        config.put("spring.jpa.hibernate.ddl-auto", "${JPA_DDL_AUTO:update}");

        // Redis configuration
        config.put("spring.redis.host", String.format("redis.mysillydreams-%s", environment));
        config.put("spring.redis.port", "6379");
        config.put("spring.redis.database", "1");
        config.put("spring.redis.timeout", "2000ms");
        config.put("spring.redis.password", "${REDIS_PASSWORD:" + System.getenv().getOrDefault("REDIS_PASSWORD", "") + "}");
        
        // JWT configuration
        config.put("jwt.secret", System.getenv().getOrDefault("JWT_SECRET", "mySecretKey123456789012345678901234567890"));
        config.put("jwt.expiration-ms", "${JWT_EXPIRATION_MS:86400000}");

        // Keycloak configuration
        config.put("keycloak.auth-server-url",
            String.format("http://keycloak.mysillydreams-%s:8080", environment));
        config.put("keycloak.realm", "${KEYCLOAK_REALM:MySillyDreams-Realm}");
        config.put("keycloak.admin-client.client-id", "${KEYCLOAK_CLIENT_ID:admin-cli}");
        config.put("keycloak.admin-client.username", "${KEYCLOAK_USERNAME:" + System.getenv().getOrDefault("KEYCLOAK_USERNAME", "admin") + "}");
        config.put("keycloak.admin-client.password", "${KEYCLOAK_PASSWORD:" + System.getenv().getOrDefault("KEYCLOAK_PASSWORD", "admin123") + "}");

        // Application specific
        config.put("app.simple-encryption.secret-key", System.getenv().getOrDefault("APP_ENCRYPTION_KEY", "TestEncryptionKeyForProduction123456789!"));
        config.put("app.mfa.issuer-name", "${MFA_ISSUER:MySillyDreamsPlatform}");
        config.put("app.internal-api.secret-key", System.getenv().getOrDefault("INTERNAL_API_KEY", "TestInternalApiKeyForProduction123456789!"));
        config.put("app.cors.allowed-origins", "${CORS_ORIGINS:http://localhost:3000}");
        
        // Service-specific logging
        config.put("logging.level.com.mysillydreams.user", "${LOG_LEVEL_USER:INFO}");
        
        return convertMapToPropertiesString(config);
    }

    private String getApiGatewayTemplate(String environment) {
        Map<String, String> config = new HashMap<>(getBaseConfiguration(environment));
        
        // Redis configuration
        config.put("spring.redis.host", String.format("redis.mysillydreams-%s", environment));
        config.put("spring.redis.port", "6379");
        config.put("spring.redis.database", "2");
        config.put("spring.redis.timeout", "2000ms");
        config.put("spring.redis.password", "${REDIS_PASSWORD:}");
        
        // Gateway routes
        config.put("spring.cloud.gateway.routes[0].id", "auth-service");
        config.put("spring.cloud.gateway.routes[0].uri", 
            String.format("http://auth-service.mysillydreams-%s:8081", environment));
        config.put("spring.cloud.gateway.routes[0].predicates[0]", "Path=/auth/**");
        config.put("spring.cloud.gateway.routes[0].filters[0]", "StripPrefix=1");
        
        config.put("spring.cloud.gateway.routes[1].id", "user-service");
        config.put("spring.cloud.gateway.routes[1].uri", 
            String.format("http://user-service.mysillydreams-%s:8082", environment));
        config.put("spring.cloud.gateway.routes[1].predicates[0]", "Path=/users/**");
        config.put("spring.cloud.gateway.routes[1].filters[0]", "StripPrefix=1");
        
        // JWT configuration
        config.put("jwt.secret", "${JWT_SECRET:}");
        config.put("jwt.expiration-ms", "${JWT_EXPIRATION_MS:86400000}");
        
        // CORS configuration
        config.put("app.cors.allowed-origins", "${CORS_ORIGINS:http://localhost:3000}");
        
        // Gateway-specific management endpoints
        config.put("management.endpoints.web.exposure.include", "health,info,metrics,prometheus,gateway");
        
        // Service-specific logging
        config.put("logging.level.com.mysillydreams.gateway", "${LOG_LEVEL_GATEWAY:INFO}");
        config.put("logging.level.org.springframework.cloud.gateway", "${LOG_LEVEL_GATEWAY_FRAMEWORK:INFO}");
        
        return convertMapToPropertiesString(config);
    }

    private String getAdminServerTemplate(String environment) {
        Map<String, String> config = new HashMap<>(getBaseConfiguration(environment));
        
        // Admin server specific configuration
        config.put("spring.boot.admin.server.enabled", "true");
        config.put("spring.boot.admin.server.ui.title", "MySillyDreams Admin Server");
        
        // Service-specific logging
        config.put("logging.level.com.mysillydreams.admin", "${LOG_LEVEL_ADMIN:INFO}");
        
        return convertMapToPropertiesString(config);
    }

    private String getDefaultServiceTemplate(String environment, String service) {
        Map<String, String> config = new HashMap<>(getBaseConfiguration(environment));
        
        // Service-specific logging
        config.put(String.format("logging.level.com.mysillydreams.%s", service.replace("-", "")), 
            String.format("${LOG_LEVEL_%s:INFO}", service.toUpperCase().replace("-", "_")));
        
        return convertMapToPropertiesString(config);
    }

    private String convertMapToPropertiesString(Map<String, String> configMap) {
        StringBuilder sb = new StringBuilder();
        configMap.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> sb.append(entry.getKey()).append("=").append(entry.getValue()).append("\n"));
        return sb.toString();
    }

    private void validateEnvironment(String environment) {
        if (!properties.getEnvironments().contains(environment)) {
            throw new IllegalArgumentException("Invalid environment: " + environment);
        }
    }

    private void validateService(String service) {
        if (!properties.getServices().contains(service)) {
            throw new IllegalArgumentException("Invalid service: " + service);
        }
    }
}
