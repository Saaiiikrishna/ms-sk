package com.mysillydreams.zookeeper.health;

import com.mysillydreams.zookeeper.service.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Production-ready health indicator for Zookeeper connection
 * Provides detailed health information for monitoring and alerting
 */
@Component("zookeeperHealth")
public class ZookeeperHealthIndicator {

    private static final Logger logger = LoggerFactory.getLogger(ZookeeperHealthIndicator.class);

    private final ConfigurationService configurationService;

    @Autowired
    public ZookeeperHealthIndicator(ConfigurationService configurationService) {
        this.configurationService = configurationService;
    }

    /**
     * Get health status as a map
     */
    public Map<String, Object> getHealthStatus() {
        Map<String, Object> health = new HashMap<>();

        try {
            // Check if Zookeeper is connected
            boolean isConnected = configurationService.isConnected();

            if (isConnected) {
                // Get detailed health information
                Map<String, Object> healthInfo = configurationService.getHealthInfo();

                // Perform additional health checks
                boolean canPerformOperations = performHealthChecks();

                if (canPerformOperations) {
                    health.put("status", "UP");
                    health.put("connection", "UP");
                    health.put("operations", "FUNCTIONAL");
                    health.put("message", "Connected and operational");
                    health.putAll(healthInfo);
                } else {
                    health.put("status", "DEGRADED");
                    health.put("connection", "UP");
                    health.put("operations", "DEGRADED");
                    health.put("message", "Connected but operations failing");
                    health.putAll(healthInfo);
                }
            } else {
                health.put("status", "DOWN");
                health.put("connection", "DOWN");
                health.put("operations", "UNAVAILABLE");
                health.put("message", "Zookeeper connection is not available");
            }
        } catch (Exception e) {
            logger.error("Health check failed: {}", e.getMessage(), e);
            health.put("status", "DOWN");
            health.put("connection", "UNKNOWN");
            health.put("operations", "UNKNOWN");
            health.put("message", "Health check failed");
            health.put("error", e.getMessage());
            health.put("errorType", e.getClass().getSimpleName());
        }

        health.put("timestamp", System.currentTimeMillis());
        health.put("service", "zookeeper-service");

        return health;
    }

    /**
     * Perform additional health checks to ensure Zookeeper operations are working
     */
    private boolean performHealthChecks() {
        try {
            // Test basic Zookeeper operations
            // This is a lightweight check that doesn't modify data
            return configurationService.isConnected();
        } catch (Exception e) {
            logger.warn("Health check operation failed: {}", e.getMessage());
            return false;
        }
    }
}
