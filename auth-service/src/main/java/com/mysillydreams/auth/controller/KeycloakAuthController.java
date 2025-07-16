package com.mysillydreams.auth.controller;

import com.mysillydreams.auth.entity.Admin;
import com.mysillydreams.auth.service.HybridAdminService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

/**
 * REST Controller for Keycloak Authentication Integration
 * 
 * This controller provides REST endpoints that Keycloak can call to authenticate
 * admin users against the Auth Service database without storing admin data in Keycloak.
 * 
 * This enables admins to login to Keycloak UI while maintaining complete data separation.
 */
@RestController
@RequestMapping("/keycloak")
@CrossOrigin(origins = "*")
public class KeycloakAuthController {

    private static final Logger logger = LoggerFactory.getLogger(KeycloakAuthController.class);

    private final HybridAdminService hybridAdminService;

    @Autowired
    public KeycloakAuthController(HybridAdminService hybridAdminService) {
        this.hybridAdminService = hybridAdminService;
    }

    /**
     * Validates admin credentials for Keycloak authentication
     * 
     * This endpoint is called by Keycloak to validate admin credentials
     * against the Auth Service database.
     * 
     * @param request Authentication request with username and password
     * @return Authentication response with user details if valid
     */
    @PostMapping("/validate-admin")
    public ResponseEntity<?> validateAdmin(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String password = request.get("password");

        logger.info("Keycloak admin validation request for user: {}", username);

        try {
            // Authenticate admin using internal Auth Service
            Admin admin = hybridAdminService.authenticateAdmin(username, password);
            
            // Return admin details for Keycloak session
            Map<String, Object> response = Map.of(
                "valid", true,
                "userId", admin.getId().toString(),
                "username", admin.getUsername(),
                "email", admin.getEmail(),
                "firstName", admin.getFirstName(),
                "lastName", admin.getLastName(),
                "enabled", admin.getEnabled(),
                "emailVerified", true,
                "roles", new String[]{"admin", "keycloak-admin"}
            );

            logger.info("Admin validation successful for Keycloak: {}", username);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.warn("Admin validation failed for Keycloak: {} - {}", username, e.getMessage());
            
            Map<String, Object> response = Map.of(
                "valid", false,
                "error", "Invalid credentials"
            );
            
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
    }

    /**
     * Gets admin user details by username for Keycloak
     * 
     * This endpoint is called by Keycloak to get admin user details
     * for session management.
     * 
     * @param username Admin username
     * @return Admin user details if found
     */
    @GetMapping("/admin/{username}")
    public ResponseEntity<?> getAdmin(@PathVariable String username) {
        logger.debug("Keycloak admin lookup request for user: {}", username);

        try {
            Optional<Admin> adminOpt = hybridAdminService.getAdminByUsername(username);
            
            if (adminOpt.isPresent()) {
                Admin admin = adminOpt.get();
                
                Map<String, Object> response = Map.of(
                    "found", true,
                    "userId", admin.getId().toString(),
                    "username", admin.getUsername(),
                    "email", admin.getEmail(),
                    "firstName", admin.getFirstName(),
                    "lastName", admin.getLastName(),
                    "enabled", admin.getEnabled(),
                    "emailVerified", true,
                    "roles", new String[]{"admin", "keycloak-admin"}
                );

                logger.debug("Admin found for Keycloak: {}", username);
                return ResponseEntity.ok(response);
            } else {
                Map<String, Object> response = Map.of(
                    "found", false,
                    "error", "Admin not found"
                );
                
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }

        } catch (Exception e) {
            logger.error("Error looking up admin for Keycloak: {} - {}", username, e.getMessage());
            
            Map<String, Object> response = Map.of(
                "found", false,
                "error", "Internal error"
            );
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Health check endpoint for Keycloak integration
     * 
     * @return Health status
     */
    @GetMapping("/health")
    public ResponseEntity<?> health() {
        Map<String, Object> response = Map.of(
            "status", "healthy",
            "service", "Auth Service Keycloak Integration",
            "timestamp", System.currentTimeMillis()
        );
        
        return ResponseEntity.ok(response);
    }
}
