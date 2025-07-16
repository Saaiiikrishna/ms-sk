package com.mysillydreams.auth.service;

import com.mysillydreams.auth.entity.Admin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Hybrid Authentication Service that handles authentication for both regular users and admins.
 * 
 * Architecture:
 * - Regular users: Authenticated through Keycloak (existing flow)
 * - Admin users: Authenticated through hybrid approach (internal DB + Keycloak APIs)
 * - ADMIN_ROLE managed internally, not in Keycloak
 */
@Service
public class HybridAuthenticationService {

    private static final Logger logger = LoggerFactory.getLogger(HybridAuthenticationService.class);

    private final KeycloakAuthenticationService keycloakAuthenticationService;
    private final HybridAdminService hybridAdminService;
    private final JwtService jwtService;

    @Autowired
    public HybridAuthenticationService(KeycloakAuthenticationService keycloakAuthenticationService,
                                     HybridAdminService hybridAdminService,
                                     JwtService jwtService) {
        this.keycloakAuthenticationService = keycloakAuthenticationService;
        this.hybridAdminService = hybridAdminService;
        this.jwtService = jwtService;
    }

    /**
     * Authenticates user using hybrid approach.
     * First checks if user is an admin (internal DB), then falls back to regular user authentication.
     */
    public Map<String, Object> authenticateUser(String username, String password) {
        logger.info("Authenticating user: {} using hybrid approach", username);

        try {
            // Step 1: Check if user is an admin in internal database
            if (hybridAdminService.getAdminByUsername(username).isPresent()) {
                return authenticateAdmin(username, password);
            }
            
            // Step 2: Fall back to regular user authentication through Keycloak
            return authenticateRegularUser(username, password);
            
        } catch (Exception e) {
            logger.error("Authentication failed for user: {}", username, e);
            throw new RuntimeException("Authentication failed", e);
        }
    }

    /**
     * Authenticates admin user using hybrid approach.
     */
    private Map<String, Object> authenticateAdmin(String username, String password) {
        logger.info("Authenticating admin user: {}", username);

        try {
            // Authenticate admin using hybrid service
            Admin admin = hybridAdminService.authenticateAdmin(username, password);
            
            // Generate JWT token with ADMIN_ROLE
            String token = jwtService.generateToken(username, "ROLE_ADMIN");
            
            Map<String, Object> response = new HashMap<>();
            response.put("access_token", token);
            response.put("token_type", "Bearer");
            response.put("expires_in", jwtService.getExpirationTime());
            response.put("user", username);
            response.put("user_id", admin.getId().toString());
            response.put("roles", "ROLE_ADMIN");
            response.put("user_type", "ADMIN");
            response.put("full_name", admin.getFullName());
            response.put("email", admin.getEmail());
            
            logger.info("Admin authentication successful: {}", username);
            return response;
            
        } catch (Exception e) {
            logger.error("Admin authentication failed: {}", username, e);
            throw new RuntimeException("Admin authentication failed: " + e.getMessage(), e);
        }
    }

    /**
     * Authenticates regular user through existing Keycloak flow.
     */
    private Map<String, Object> authenticateRegularUser(String username, String password) {
        logger.info("Authenticating regular user: {}", username);

        try {
            // Use existing Keycloak authentication service
            KeycloakAuthenticationService.KeycloakUserInfo userInfo =
                keycloakAuthenticationService.authenticateUser(username, password);

            // Generate JWT token for regular user
            String roles = userInfo.getAuthorities().stream()
                .map(auth -> auth.getAuthority())
                .findFirst()
                .orElse("ROLE_USER");

            String token = jwtService.generateToken(username, roles);

            Map<String, Object> response = new HashMap<>();
            response.put("access_token", token);
            response.put("token_type", "Bearer");
            response.put("expires_in", jwtService.getExpirationTime());
            response.put("user", username);
            response.put("user_id", userInfo.getUserId());
            response.put("roles", roles);
            response.put("user_type", "REGULAR");
            response.put("full_name", userInfo.getFirstName() + " " + userInfo.getLastName());
            response.put("email", userInfo.getEmail());

            logger.info("Regular user authentication successful: {}", username);
            return response;

        } catch (Exception e) {
            logger.error("Regular user authentication failed: {}", username, e);
            throw new RuntimeException("Regular user authentication failed: " + e.getMessage(), e);
        }
    }

    /**
     * Validates JWT token and returns user information.
     */
    public Map<String, Object> validateToken(String token) {
        try {
            // Extract username from token
            String username = jwtService.extractUsername(token);
            
            // Check if token is valid
            if (!jwtService.isTokenValid(token, username)) {
                throw new RuntimeException("Invalid token");
            }
            
            // Extract roles from token
            String roles = jwtService.extractRoles(token);
            
            Map<String, Object> response = new HashMap<>();
            response.put("valid", true);
            response.put("username", username);
            response.put("roles", roles);
            
            // If admin, get additional admin information
            if ("ROLE_ADMIN".equals(roles)) {
                hybridAdminService.getAdminByUsername(username).ifPresent(admin -> {
                    response.put("user_id", admin.getId().toString());
                    response.put("user_type", "ADMIN");
                    response.put("full_name", admin.getFullName());
                    response.put("email", admin.getEmail());
                });
            } else {
                response.put("user_type", "REGULAR");
            }
            
            return response;
            
        } catch (Exception e) {
            logger.error("Token validation failed: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("valid", false);
            response.put("error", e.getMessage());
            return response;
        }
    }

    /**
     * Checks if a username belongs to an admin user.
     */
    public boolean isAdminUser(String username) {
        return hybridAdminService.getAdminByUsername(username).isPresent();
    }

    /**
     * Gets admin user information by username.
     */
    public Admin getAdminUser(String username) {
        return hybridAdminService.getAdminByUsername(username)
            .orElseThrow(() -> new IllegalArgumentException("Admin not found: " + username));
    }
}
