package com.mysillydreams.zookeeper.service;

import com.mysillydreams.zookeeper.config.ZookeeperServiceProperties;
import io.micrometer.tracing.annotation.NewSpan;
import io.micrometer.tracing.annotation.SpanTag;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.CreateMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Production-ready Configuration management service using Zookeeper
 * Features:
 * - Secure configuration management with environment variables
 * - Dynamic service configuration using templates
 * - Input validation and sanitization
 * - Audit logging and monitoring
 * - Connection pooling and retry mechanisms
 */
@Service
@EnableConfigurationProperties(ZookeeperServiceProperties.class)
public class ConfigurationService {

    private static final Logger logger = LoggerFactory.getLogger(ConfigurationService.class);
    private static final Logger auditLogger = LoggerFactory.getLogger("AUDIT");
    
    @Value("${zookeeper.connection-string}")
    private String connectionString;
    
    @Value("${zookeeper.session-timeout}")
    private int sessionTimeout;
    
    @Value("${zookeeper.connection-timeout}")
    private int connectionTimeout;
    
    @Value("${zookeeper.retry.max-attempts:3}")
    private int maxRetryAttempts;
    
    @Value("${zookeeper.retry.base-sleep-time:1000}")
    private int baseSleepTime;
    
    private final ZookeeperServiceProperties properties;
    private final ConfigurationTemplateService templateService;
    private CuratorFramework client;
    private final Map<String, String> configurationCache = new ConcurrentHashMap<>();
    
    @Autowired
    public ConfigurationService(ZookeeperServiceProperties properties,
                              ConfigurationTemplateService templateService) {
        this.properties = properties;
        this.templateService = templateService;
    }
    
    @PostConstruct
    public void init() {
        try {
            logger.info("Initializing Zookeeper client with connection string: {}", 
                maskConnectionString(connectionString));
            
            client = CuratorFrameworkFactory.newClient(
                connectionString,
                sessionTimeout,
                connectionTimeout,
                new ExponentialBackoffRetry(baseSleepTime, maxRetryAttempts)
            );
            
            client.start();
            client.blockUntilConnected();
            
            // Initialize configuration structure
            initializeConfigurationStructure();
            
            logger.info("Zookeeper client connected successfully");
            auditLogger.info("Zookeeper service initialized successfully");
        } catch (Exception e) {
            logger.error("Failed to initialize Zookeeper client: {}", e.getMessage(), e);
            auditLogger.error("Failed to initialize Zookeeper service: {}", e.getMessage());
            throw new RuntimeException("Failed to initialize Zookeeper client", e);
        }
    }
    
    @PreDestroy
    public void cleanup() {
        if (client != null) {
            client.close();
            logger.info("Zookeeper client connection closed");
            auditLogger.info("Zookeeper service shutdown completed");
        }
    }
    
    /**
     * Initialize the configuration structure for all environments
     */
    private void initializeConfigurationStructure() {
        try {
            logger.info("Initializing configuration structure...");
            
            // Create root path
            createNodeIfNotExists("/mysillydreams", "MySillyDreams Platform Configuration Root");
            
            // Create environment and service paths
            for (String env : properties.getEnvironments()) {
                String envPath = "/mysillydreams/" + env;
                createNodeIfNotExists(envPath, env + " environment configuration");
                
                for (String service : properties.getServices()) {
                    String servicePath = envPath + "/" + service;
                    createNodeIfNotExists(servicePath, service + " configuration for " + env);
                }
            }
            
            // Initialize default configurations using templates
            initializeDefaultConfigurations();
            
            logger.info("Configuration structure initialized successfully");
        } catch (Exception e) {
            logger.error("Failed to initialize configuration structure: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize configuration structure", e);
        }
    }
    
    /**
     * Initialize default configurations for all microservices using secure templates
     */
    private void initializeDefaultConfigurations() {
        try {
            logger.info("Initializing default configurations for microservices...");
            
            for (String environment : properties.getEnvironments()) {
                for (String service : properties.getServices()) {
                    try {
                        String servicePath = "/mysillydreams/" + environment + "/" + service;
                        
                        // Check if configuration already exists
                        if (client.checkExists().forPath(servicePath) != null) {
                            byte[] existingData = client.getData().forPath(servicePath);
                            if (existingData != null && existingData.length > 0) {
                                String existingConfig = new String(existingData);
                                if (!isPlaceholderConfig(existingConfig)) {
                                    logger.debug("Configuration already exists for {}/{}, skipping initialization", 
                                        environment, service);
                                    continue;
                                }
                            }
                        }
                        
                        // Get template configuration
                        String templateConfig = templateService.getConfigurationTemplate(environment, service);
                        
                        // Set the configuration
                        if (client.checkExists().forPath(servicePath) != null) {
                            client.setData().forPath(servicePath, templateConfig.getBytes());
                        } else {
                            client.create()
                                .creatingParentsIfNeeded()
                                .withMode(CreateMode.PERSISTENT)
                                .forPath(servicePath, templateConfig.getBytes());
                        }
                        
                        logger.info("Initialized configuration for {}/{}", environment, service);
                        auditLogger.info("Configuration initialized for service: {} in environment: {}", 
                            service, environment);
                        
                    } catch (Exception e) {
                        logger.error("Failed to initialize configuration for {}/{}: {}", 
                            environment, service, e.getMessage(), e);
                    }
                }
            }
            
            logger.info("Default configurations initialized successfully");
        } catch (Exception e) {
            logger.error("Failed to initialize default configurations: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Set configuration value for a specific environment and service
     */
    @NewSpan("zookeeper.setConfig")
    public void setConfiguration(@SpanTag("environment") @NotBlank String environment, 
                               @SpanTag("service") @NotBlank String service, 
                               @SpanTag("key") @NotBlank String key, 
                               @NotNull String value) {
        try {
            validateInput(environment, service, key);
            
            String path = buildConfigPath(environment, service, key);
            
            if (client.checkExists().forPath(path) != null) {
                client.setData().forPath(path, value.getBytes());
            } else {
                client.create()
                    .creatingParentsIfNeeded()
                    .withMode(CreateMode.PERSISTENT)
                    .forPath(path, value.getBytes());
            }
            
            // Update cache
            String cacheKey = environment + "/" + service + "/" + key;
            configurationCache.put(cacheKey, value);
            
            logger.info("Configuration set: {}/{}/{}", environment, service, key);
            auditLogger.info("Configuration updated: {}/{}/{} by system", environment, service, key);
        } catch (Exception e) {
            logger.error("Failed to set configuration {}/{}/{}: {}", environment, service, key, e.getMessage(), e);
            auditLogger.error("Failed to set configuration {}/{}/{}: {}", environment, service, key, e.getMessage());
            throw new RuntimeException("Failed to set configuration", e);
        }
    }
    
    /**
     * Get configuration value for a specific environment and service
     */
    @NewSpan("zookeeper.getConfig")
    public String getConfiguration(@SpanTag("environment") @NotBlank String environment, 
                                 @SpanTag("service") @NotBlank String service, 
                                 @SpanTag("key") @NotBlank String key) {
        try {
            validateInput(environment, service, key);
            
            // Check cache first if enabled
            if (properties.getPerformance().getCache().isEnabled()) {
                String cacheKey = environment + "/" + service + "/" + key;
                String cachedValue = configurationCache.get(cacheKey);
                if (cachedValue != null) {
                    return cachedValue;
                }
            }
            
            String path = buildConfigPath(environment, service, key);
            
            if (client.checkExists().forPath(path) != null) {
                byte[] data = client.getData().forPath(path);
                String value = new String(data);
                
                // Update cache
                if (properties.getPerformance().getCache().isEnabled()) {
                    String cacheKey = environment + "/" + service + "/" + key;
                    configurationCache.put(cacheKey, value);
                }
                
                return value;
            }
            
            return null;
        } catch (Exception e) {
            logger.error("Failed to get configuration {}/{}/{}: {}", environment, service, key, e.getMessage(), e);
            throw new RuntimeException("Failed to get configuration", e);
        }
    }
    
    /**
     * Get all configurations for a service in an environment
     */
    @NewSpan("zookeeper.getServiceConfig")
    public Map<String, String> getServiceConfiguration(@SpanTag("environment") @NotBlank String environment, 
                                                      @SpanTag("service") @NotBlank String service) {
        try {
            validateEnvironmentAndService(environment, service);
            
            String servicePath = "/mysillydreams/" + environment + "/" + service;
            Map<String, String> configs = new HashMap<>();
            
            if (client.checkExists().forPath(servicePath) != null) {
                byte[] data = client.getData().forPath(servicePath);
                if (data != null && data.length > 0) {
                    // Return the entire configuration as a single entry
                    configs.put("configuration", new String(data));
                } else {
                    // If no data at service level, check for individual keys
                    List<String> children = client.getChildren().forPath(servicePath);
                    
                    for (String child : children) {
                        String childPath = servicePath + "/" + child;
                        byte[] childData = client.getData().forPath(childPath);
                        configs.put(child, new String(childData));
                    }
                }
            }
            
            return configs;
        } catch (Exception e) {
            logger.error("Failed to get service configuration {}/{}: {}", environment, service, e.getMessage(), e);
            throw new RuntimeException("Failed to get service configuration", e);
        }
    }

    /**
     * Delete configuration
     */
    @NewSpan("zookeeper.deleteConfig")
    public void deleteConfiguration(@SpanTag("environment") @NotBlank String environment,
                                  @SpanTag("service") @NotBlank String service,
                                  @SpanTag("key") @NotBlank String key) {
        try {
            validateInput(environment, service, key);

            String path = buildConfigPath(environment, service, key);

            if (client.checkExists().forPath(path) != null) {
                client.delete().forPath(path);

                // Remove from cache
                String cacheKey = environment + "/" + service + "/" + key;
                configurationCache.remove(cacheKey);

                logger.info("Configuration deleted: {}", path);
                auditLogger.info("Configuration deleted: {}/{}/{} by system", environment, service, key);
            }
        } catch (Exception e) {
            logger.error("Failed to delete configuration {}/{}/{}: {}", environment, service, key, e.getMessage(), e);
            auditLogger.error("Failed to delete configuration {}/{}/{}: {}", environment, service, key, e.getMessage());
            throw new RuntimeException("Failed to delete configuration", e);
        }
    }

    /**
     * Check if Zookeeper is connected
     */
    public boolean isConnected() {
        return client != null && client.getZookeeperClient().isConnected();
    }

    /**
     * Get health information
     */
    public Map<String, Object> getHealthInfo() {
        Map<String, Object> health = new HashMap<>();
        health.put("connected", isConnected());
        health.put("connectionString", maskConnectionString(connectionString));
        health.put("sessionTimeout", sessionTimeout);
        health.put("connectionTimeout", connectionTimeout);
        health.put("cacheSize", configurationCache.size());
        health.put("cacheEnabled", properties.getPerformance().getCache().isEnabled());
        return health;
    }

    // Private helper methods

    private void createNodeIfNotExists(String path, String data) throws Exception {
        if (client.checkExists().forPath(path) == null) {
            client.create()
                .creatingParentsIfNeeded()
                .withMode(CreateMode.PERSISTENT)
                .forPath(path, data.getBytes());
        }
    }

    private String buildConfigPath(String environment, String service, String key) {
        return "/mysillydreams/" + environment + "/" + service + "/" + key;
    }

    private void validateInput(String environment, String service, String key) {
        validateEnvironmentAndService(environment, service);

        if (!StringUtils.hasText(key)) {
            throw new IllegalArgumentException("Key cannot be null or empty");
        }

        // Sanitize key to prevent path traversal
        if (key.contains("..") || key.contains("/") || key.contains("\\")) {
            throw new IllegalArgumentException("Invalid key format: " + key);
        }
    }

    private void validateEnvironmentAndService(String environment, String service) {
        if (!StringUtils.hasText(environment)) {
            throw new IllegalArgumentException("Environment cannot be null or empty");
        }

        if (!StringUtils.hasText(service)) {
            throw new IllegalArgumentException("Service cannot be null or empty");
        }

        if (!properties.getEnvironments().contains(environment)) {
            throw new IllegalArgumentException("Invalid environment: " + environment);
        }

        if (!properties.getServices().contains(service)) {
            throw new IllegalArgumentException("Invalid service: " + service);
        }
    }

    private boolean isPlaceholderConfig(String config) {
        return config.contains("placeholder") || config.contains("TODO") || config.trim().isEmpty() ||
               (config.contains("${") && config.contains(":}")) ||
               config.trim().equals("jwt:") || config.length() < 50; // Detect incomplete/corrupted configs
    }

    private String maskConnectionString(String connectionString) {
        if (connectionString == null) {
            return "null";
        }

        // Mask sensitive information in connection string
        return connectionString.replaceAll("(password=)[^&]*", "$1***")
                              .replaceAll("(user=)[^&]*", "$1***");
    }
}
