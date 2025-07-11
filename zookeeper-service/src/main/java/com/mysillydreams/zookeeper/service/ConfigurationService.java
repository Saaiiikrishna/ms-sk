package com.mysillydreams.zookeeper.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.tracing.annotation.NewSpan;
import io.micrometer.tracing.annotation.SpanTag;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.CreateMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration management service using Zookeeper
 */
@Service
public class ConfigurationService {

    private static final Logger logger = LoggerFactory.getLogger(ConfigurationService.class);
    
    @Value("${zookeeper.connection-string:localhost:2181}")
    private String connectionString;
    
    @Value("${zookeeper.session-timeout:60000}")
    private int sessionTimeout;
    
    @Value("${zookeeper.connection-timeout:15000}")
    private int connectionTimeout;
    
    private CuratorFramework client;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @PostConstruct
    public void init() {
        try {
            client = CuratorFrameworkFactory.newClient(
                connectionString,
                sessionTimeout,
                connectionTimeout,
                new ExponentialBackoffRetry(1000, 3)
            );
            client.start();
            client.blockUntilConnected();
            
            // Initialize configuration structure
            initializeConfigurationStructure();
            
            logger.info("Zookeeper client connected successfully to: {}", connectionString);
        } catch (Exception e) {
            logger.error("Failed to initialize Zookeeper client: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize Zookeeper client", e);
        }
    }
    
    @PreDestroy
    public void cleanup() {
        if (client != null) {
            client.close();
            logger.info("Zookeeper client connection closed");
        }
    }
    
    /**
     * Initialize the configuration structure for all environments
     */
    private void initializeConfigurationStructure() {
        try {
            String[] environments = {"dev", "qa", "staging"};
            String[] services = {"api-gateway", "auth-service", "user-service", "admin-server", 
                               "eureka-server", "zipkin", "redis", "vault", "keycloak"};
            
            // Create root path
            createNodeIfNotExists("/mysillydreams", "MySillyDreams Platform Configuration Root");
            
            // Create environment and service paths
            for (String env : environments) {
                String envPath = "/mysillydreams/" + env;
                createNodeIfNotExists(envPath, env + " environment configuration");
                
                for (String service : services) {
                    String servicePath = envPath + "/" + service;
                    createNodeIfNotExists(servicePath, service + " configuration for " + env);
                }
            }
            
            logger.info("Configuration structure initialized successfully");
        } catch (Exception e) {
            logger.error("Failed to initialize configuration structure: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Set configuration value for a specific environment and service
     */
    @NewSpan("zookeeper.setConfig")
    public void setConfiguration(@SpanTag("environment") String environment, 
                               @SpanTag("service") String service, 
                               @SpanTag("key") String key, 
                               String value) {
        try {
            String path = buildConfigPath(environment, service, key);
            
            if (client.checkExists().forPath(path) != null) {
                client.setData().forPath(path, value.getBytes());
            } else {
                client.create()
                    .creatingParentsIfNeeded()
                    .withMode(CreateMode.PERSISTENT)
                    .forPath(path, value.getBytes());
            }
            
            logger.info("Configuration set: {} = {}", path, value);
        } catch (Exception e) {
            logger.error("Failed to set configuration {}/{}/{}: {}", environment, service, key, e.getMessage(), e);
            throw new RuntimeException("Failed to set configuration", e);
        }
    }
    
    /**
     * Get configuration value for a specific environment and service
     */
    @NewSpan("zookeeper.getConfig")
    public String getConfiguration(@SpanTag("environment") String environment, 
                                 @SpanTag("service") String service, 
                                 @SpanTag("key") String key) {
        try {
            String path = buildConfigPath(environment, service, key);
            
            if (client.checkExists().forPath(path) != null) {
                byte[] data = client.getData().forPath(path);
                return new String(data);
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
    public Map<String, String> getServiceConfiguration(@SpanTag("environment") String environment, 
                                                      @SpanTag("service") String service) {
        try {
            String servicePath = "/mysillydreams/" + environment + "/" + service;
            Map<String, String> configs = new HashMap<>();
            
            if (client.checkExists().forPath(servicePath) != null) {
                List<String> children = client.getChildren().forPath(servicePath);
                
                for (String child : children) {
                    String childPath = servicePath + "/" + child;
                    byte[] data = client.getData().forPath(childPath);
                    configs.put(child, new String(data));
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
    public void deleteConfiguration(@SpanTag("environment") String environment, 
                                  @SpanTag("service") String service, 
                                  @SpanTag("key") String key) {
        try {
            String path = buildConfigPath(environment, service, key);
            
            if (client.checkExists().forPath(path) != null) {
                client.delete().forPath(path);
                logger.info("Configuration deleted: {}", path);
            }
        } catch (Exception e) {
            logger.error("Failed to delete configuration {}/{}/{}: {}", environment, service, key, e.getMessage(), e);
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
     * Create node if it doesn't exist
     */
    private void createNodeIfNotExists(String path, String data) throws Exception {
        if (client.checkExists().forPath(path) == null) {
            client.create()
                .creatingParentsIfNeeded()
                .withMode(CreateMode.PERSISTENT)
                .forPath(path, data.getBytes());
        }
    }
    
    /**
     * Build configuration path
     */
    private String buildConfigPath(String environment, String service, String key) {
        return "/mysillydreams/" + environment + "/" + service + "/" + key;
    }
}
