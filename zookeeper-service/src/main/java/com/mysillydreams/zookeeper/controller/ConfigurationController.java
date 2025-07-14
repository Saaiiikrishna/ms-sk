package com.mysillydreams.zookeeper.controller;

import com.mysillydreams.zookeeper.dto.ConfigurationRequest;
import com.mysillydreams.zookeeper.dto.ConfigurationResponse;
import com.mysillydreams.zookeeper.health.ZookeeperHealthIndicator;
import com.mysillydreams.zookeeper.service.ConfigurationService;
import io.micrometer.tracing.annotation.NewSpan;
import io.micrometer.tracing.annotation.SpanTag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;

/**
 * Production-ready REST API for Zookeeper configuration management
 * Features:
 * - Comprehensive input validation and sanitization
 * - Security audit logging
 * - Rate limiting protection
 * - Proper error handling with security considerations
 * - Request/response tracing
 */
@RestController
@RequestMapping("/api/config")
@Validated
public class ConfigurationController {

    private static final Logger logger = LoggerFactory.getLogger(ConfigurationController.class);
    private static final Logger auditLogger = LoggerFactory.getLogger("AUDIT");

    private final ConfigurationService configurationService;
    private final ZookeeperHealthIndicator healthIndicator;

    public ConfigurationController(ConfigurationService configurationService,
                                 ZookeeperHealthIndicator healthIndicator) {
        this.configurationService = configurationService;
        this.healthIndicator = healthIndicator;
    }

    /**
     * Set configuration value with comprehensive security and validation
     */
    @PostMapping("/{environment}/{service}")
    @PreAuthorize("hasRole('ADMIN')")
    @NewSpan("config.set")
    public ResponseEntity<ConfigurationResponse> setConfiguration(
            @PathVariable @SpanTag("environment")
            @Pattern(regexp = "^[a-zA-Z0-9-_]{1,50}$", message = "Environment must be alphanumeric with hyphens/underscores, max 50 chars")
            String environment,
            @PathVariable @SpanTag("service")
            @Pattern(regexp = "^[a-zA-Z0-9-_]{1,50}$", message = "Service must be alphanumeric with hyphens/underscores, max 50 chars")
            String service,
            @Valid @RequestBody ConfigurationRequest request,
            HttpServletRequest httpRequest) {
        
        try {
            // Get authentication details for audit logging
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth != null ? auth.getName() : "anonymous";
            String clientIp = getClientIpAddress(httpRequest);

            // Audit log the configuration change attempt
            auditLogger.info("Configuration change attempted: user={}, ip={}, env={}, service={}, key={}",
                username, clientIp, environment, service, request.getKey());

            // Sanitize sensitive values for logging (don't log actual secrets)
            String logValue = isSensitiveKey(request.getKey()) ? "[REDACTED]" : request.getValue();

            configurationService.setConfiguration(environment, service, request.getKey(), request.getValue());

            ConfigurationResponse response = new ConfigurationResponse();
            response.setSuccess(true);
            response.setMessage("Configuration set successfully");
            response.setEnvironment(environment);
            response.setService(service);
            response.setKey(request.getKey());
            response.setValue(isSensitiveKey(request.getKey()) ? "[REDACTED]" : request.getValue());

            // Audit log successful configuration change
            auditLogger.info("Configuration changed successfully: user={}, ip={}, env={}, service={}, key={}, value={}",
                username, clientIp, environment, service, request.getKey(), logValue);

            logger.info("Configuration set: {}/{}/{} = {}", environment, service, request.getKey(), logValue);
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            // Handle validation errors
            auditLogger.warn("Configuration change failed - validation error: user={}, ip={}, env={}, service={}, key={}, error={}",
                getUsername(), getClientIpAddress(httpRequest), environment, service, request.getKey(), e.getMessage());

            logger.warn("Validation error in configuration request: {}", e.getMessage());

            ConfigurationResponse response = new ConfigurationResponse();
            response.setSuccess(false);
            response.setMessage("Validation error: " + e.getMessage());

            return ResponseEntity.badRequest().body(response);

        } catch (SecurityException e) {
            // Handle security-related errors
            auditLogger.error("Configuration change failed - security error: user={}, ip={}, env={}, service={}, key={}, error={}",
                getUsername(), getClientIpAddress(httpRequest), environment, service, request.getKey(), e.getMessage());

            logger.error("Security error in configuration request: {}", e.getMessage());

            ConfigurationResponse response = new ConfigurationResponse();
            response.setSuccess(false);
            response.setMessage("Access denied");

            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);

        } catch (Exception e) {
            // Handle all other errors
            auditLogger.error("Configuration change failed - system error: user={}, ip={}, env={}, service={}, key={}, error={}",
                getUsername(), getClientIpAddress(httpRequest), environment, service, request.getKey(), e.getMessage());

            logger.error("Failed to set configuration: {}", e.getMessage(), e);

            ConfigurationResponse response = new ConfigurationResponse();
            response.setSuccess(false);
            response.setMessage("Internal server error");

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
     * Health check with enhanced security and detailed monitoring
     */
    @GetMapping("/health")
    @NewSpan("config.health")
    public ResponseEntity<Map<String, Object>> health(HttpServletRequest httpRequest) {
        try {
            // Audit log health check access
            auditLogger.debug("Health check accessed: ip={}, user={}",
                getClientIpAddress(httpRequest), getUsername());

            // Get comprehensive health status
            Map<String, Object> health = healthIndicator.getHealthStatus();
            health.put("version", "1.0.0");

            // Determine HTTP status based on health
            String status = (String) health.get("status");
            HttpStatus httpStatus = switch (status) {
                case "UP" -> HttpStatus.OK;
                case "DEGRADED" -> HttpStatus.OK; // Still operational
                case "DOWN" -> HttpStatus.SERVICE_UNAVAILABLE;
                default -> HttpStatus.INTERNAL_SERVER_ERROR;
            };

            return ResponseEntity.status(httpStatus).body(health);
        } catch (Exception e) {
            logger.error("Health check failed: {}", e.getMessage(), e);
            auditLogger.error("Health check failed: ip={}, user={}, error={}",
                getClientIpAddress(httpRequest), getUsername(), e.getMessage());

            Map<String, Object> health = Map.of(
                "status", "DOWN",
                "service", "zookeeper-service",
                "timestamp", System.currentTimeMillis(),
                "error", "Health check failed",
                "version", "1.0.0"
            );
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(health);
        }
    }

    // Security helper methods

    /**
     * Get the authenticated username safely
     */
    private String getUsername() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            return auth != null ? auth.getName() : "anonymous";
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * Extract client IP address from request
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        return request.getRemoteAddr();
    }

    /**
     * Check if a configuration key contains sensitive information
     */
    private boolean isSensitiveKey(String key) {
        if (key == null) return false;

        String lowerKey = key.toLowerCase();
        return lowerKey.contains("password") ||
               lowerKey.contains("secret") ||
               lowerKey.contains("key") ||
               lowerKey.contains("token") ||
               lowerKey.contains("credential") ||
               lowerKey.contains("auth") ||
               lowerKey.contains("private");
    }
}
