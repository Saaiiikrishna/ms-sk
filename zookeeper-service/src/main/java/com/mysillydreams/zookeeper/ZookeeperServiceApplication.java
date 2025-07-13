package com.mysillydreams.zookeeper;

import com.mysillydreams.zookeeper.config.ZookeeperServiceProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Zookeeper Configuration Management Service for MySillyDreams Platform
 * 
 * This service provides:
 * - Centralized configuration management for all environments (dev, qa, staging)
 * - Dynamic configuration updates
 * - Service coordination and synchronization
 * - Configuration versioning and rollback
 * - Environment-specific configuration isolation
 * 
 * Features:
 * - REST API for configuration management
 * - Environment-based configuration separation
 * - Real-time configuration updates
 * - Configuration validation and schema enforcement
 * - Audit logging for configuration changes
 * - Integration with all microservices
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableConfigurationProperties(ZookeeperServiceProperties.class)
public class ZookeeperServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZookeeperServiceApplication.class, args);
    }
}
