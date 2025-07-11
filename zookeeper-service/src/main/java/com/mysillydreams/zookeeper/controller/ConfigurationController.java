package com.mysillydreams.zookeeper.controller;

import com.mysillydreams.zookeeper.dto.ConfigurationRequest;
import com.mysillydreams.zookeeper.dto.ConfigurationResponse;
import com.mysillydreams.zookeeper.service.ConfigurationService;
import io.micrometer.tracing.annotation.NewSpan;
import io.micrometer.tracing.annotation.SpanTag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.Map;

/**
 * REST API for Zookeeper configuration management
 */
@RestController
@RequestMapping("/api/config")

public class ConfigurationController {

    private static final Logger logger = LoggerFactory.getLogger(ConfigurationController.class);
    
    private final ConfigurationService configurationService;

    public ConfigurationController(ConfigurationService configurationService) {
        this.configurationService = configurationService;
    }

    /**
     * Set configuration value
     */
    @PostMapping("/{environment}/{service}")
    @PreAuthorize("hasRole('ADMIN')")
    @NewSpan("config.set")
    public ResponseEntity<ConfigurationResponse> setConfiguration(
            @PathVariable @SpanTag("environment") String environment,
            @PathVariable @SpanTag("service") String service,
            @Valid @RequestBody ConfigurationRequest request) {
        
        try {
            configurationService.setConfiguration(environment, service, request.getKey(), request.getValue());
            
            ConfigurationResponse response = new ConfigurationResponse();
            response.setSuccess(true);
            response.setMessage("Configuration set successfully");
            response.setEnvironment(environment);
            response.setService(service);
            response.setKey(request.getKey());
            response.setValue(request.getValue());
            
            logger.info("Configuration set: {}/{}/{} = {}", environment, service, request.getKey(), request.getValue());
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Failed to set configuration: {}", e.getMessage(), e);
            
            ConfigurationResponse response = new ConfigurationResponse();
            response.setSuccess(false);
            response.setMessage("Failed to set configuration: " + e.getMessage());
            
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Get configuration value
     */
    @GetMapping("/{environment}/{service}/{key}")
    @NewSpan("config.get")
    public ResponseEntity<ConfigurationResponse> getConfiguration(
            @PathVariable @SpanTag("environment") String environment,
            @PathVariable @SpanTag("service") String service,
            @PathVariable @SpanTag("key") String key) {
        
        try {
            String value = configurationService.getConfiguration(environment, service, key);
            
            ConfigurationResponse response = new ConfigurationResponse();
            response.setSuccess(true);
            response.setEnvironment(environment);
            response.setService(service);
            response.setKey(key);
            response.setValue(value);
            
            if (value != null) {
                response.setMessage("Configuration retrieved successfully");
            } else {
                response.setMessage("Configuration not found");
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Failed to get configuration: {}", e.getMessage(), e);
            
            ConfigurationResponse response = new ConfigurationResponse();
            response.setSuccess(false);
            response.setMessage("Failed to get configuration: " + e.getMessage());
            
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Get all configurations for a service
     */
    @GetMapping("/{environment}/{service}")
    @NewSpan("config.getService")
    public ResponseEntity<Map<String, String>> getServiceConfiguration(
            @PathVariable @SpanTag("environment") String environment,
            @PathVariable @SpanTag("service") String service) {
        
        try {
            Map<String, String> configurations = configurationService.getServiceConfiguration(environment, service);
            return ResponseEntity.ok(configurations);
            
        } catch (Exception e) {
            logger.error("Failed to get service configuration: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Delete configuration
     */
    @DeleteMapping("/{environment}/{service}/{key}")
    @PreAuthorize("hasRole('ADMIN')")
    @NewSpan("config.delete")
    public ResponseEntity<ConfigurationResponse> deleteConfiguration(
            @PathVariable @SpanTag("environment") String environment,
            @PathVariable @SpanTag("service") String service,
            @PathVariable @SpanTag("key") String key) {
        
        try {
            configurationService.deleteConfiguration(environment, service, key);
            
            ConfigurationResponse response = new ConfigurationResponse();
            response.setSuccess(true);
            response.setMessage("Configuration deleted successfully");
            response.setEnvironment(environment);
            response.setService(service);
            response.setKey(key);
            
            logger.info("Configuration deleted: {}/{}/{}", environment, service, key);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Failed to delete configuration: {}", e.getMessage(), e);
            
            ConfigurationResponse response = new ConfigurationResponse();
            response.setSuccess(false);
            response.setMessage("Failed to delete configuration: " + e.getMessage());
            
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Health check
     */
    @GetMapping("/health")
    @NewSpan("config.health")

    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = Map.of(
            "status", configurationService.isConnected() ? "UP" : "DOWN",
            "service", "zookeeper-service",
            "timestamp", System.currentTimeMillis()
        );
        
        return ResponseEntity.ok(health);
    }
}
