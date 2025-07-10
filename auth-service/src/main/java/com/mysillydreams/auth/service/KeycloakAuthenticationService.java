package com.mysillydreams.auth.service;

import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

import jakarta.ws.rs.core.Response;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for authenticating users against Keycloak.
 * This service handles direct authentication with Keycloak and user information retrieval.
 */
@Service
public class KeycloakAuthenticationService {

    private static final Logger logger = LoggerFactory.getLogger(KeycloakAuthenticationService.class);

    private final Keycloak keycloakAdminClient;

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${keycloak.auth-server-url}")
    private String keycloakServerUrl;

    @Autowired
    public KeycloakAuthenticationService(Keycloak keycloakAdminClient) {
        this.keycloakAdminClient = keycloakAdminClient;
    }

    /**
     * Authenticates a user against Keycloak and returns user information.
     */
    public KeycloakUserInfo authenticateUser(String username, String password) {
        logger.info("Attempting to authenticate user: {}", username);

        try {
            // Create a temporary Keycloak client for user authentication
            Keycloak userKeycloak = Keycloak.getInstance(
                keycloakServerUrl,
                realm,
                username,
                password,
                "account" // Use account client for user authentication
            );

            // Test the authentication by making a simple call
            userKeycloak.tokenManager().getAccessToken();
            
            // If we get here, authentication was successful
            logger.info("User {} authenticated successfully against Keycloak", username);

            // Get user information from admin client
            RealmResource realmResource = keycloakAdminClient.realm(realm);
            UsersResource usersResource = realmResource.users();
            
            List<UserRepresentation> users = usersResource.search(username, true);
            if (users.isEmpty()) {
                logger.error("User {} not found in Keycloak after successful authentication", username);
                throw new BadCredentialsException("User not found");
            }

            UserRepresentation user = users.get(0);
            
            // Get user roles
            Set<String> roles = getUserRoles(realmResource, user.getId());
            
            // Convert roles to authorities
            Collection<GrantedAuthority> authorities = roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                .collect(Collectors.toList());

            userKeycloak.close();
            
            return new KeycloakUserInfo(
                UUID.fromString(user.getId()),
                user.getUsername(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                authorities
            );

        } catch (Exception e) {
            logger.warn("Authentication failed for user {}: {}", username, e.getMessage());
            throw new BadCredentialsException("Invalid username or password", e);
        }
    }

    /**
     * Gets user roles from Keycloak.
     */
    private Set<String> getUserRoles(RealmResource realmResource, String userId) {
        Set<String> roles = new HashSet<>();
        
        try {
            // Get realm roles
            realmResource.users().get(userId).roles().realmLevel().listAll()
                .forEach(role -> roles.add(role.getName()));
            
            logger.debug("Retrieved roles for user {}: {}", userId, roles);
        } catch (Exception e) {
            logger.warn("Failed to retrieve roles for user {}: {}", userId, e.getMessage());
        }
        
        return roles;
    }

    /**
     * Data class for Keycloak user information.
     */
    public static class KeycloakUserInfo {
        private final UUID userId;
        private final String username;
        private final String email;
        private final String firstName;
        private final String lastName;
        private final Collection<GrantedAuthority> authorities;

        public KeycloakUserInfo(UUID userId, String username, String email, String firstName, 
                               String lastName, Collection<GrantedAuthority> authorities) {
            this.userId = userId;
            this.username = username;
            this.email = email;
            this.firstName = firstName;
            this.lastName = lastName;
            this.authorities = authorities;
        }

        public UUID getUserId() { return userId; }
        public String getUsername() { return username; }
        public String getEmail() { return email; }
        public String getFirstName() { return firstName; }
        public String getLastName() { return lastName; }
        public Collection<GrantedAuthority> getAuthorities() { return authorities; }
    }
}
