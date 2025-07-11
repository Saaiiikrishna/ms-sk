package com.mysillydreams.auth.config;

import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder; // Correct import for newer Resteasy versions
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class KeycloakAdminClientConfig {

    private static final Logger logger = LoggerFactory.getLogger(KeycloakAdminClientConfig.class);

    @Value("${keycloak.auth-server-url}")
    private String keycloakServerUrl;

    @Value("${keycloak.realm}")
    private String serviceRealm; // The realm this service operates under, e.g., MySillyDreams-Realm

    // For Admin client, we usually authenticate against the 'master' realm initially,
    // or a specific realm if the client has direct permissions there.
    // The service account for 'auth-service-client' needs appropriate roles in the target realm (MySillyDreams-Realm)
    // to manage users (e.g., 'manage-users' client role from 'realm-management' client).

    @Value("${keycloak.admin-client.client-id}") // This is our client-id: admin-cli
    private String adminClientId;

    @Value("${keycloak.admin-client.client-secret}")
    private String adminClientSecret;

    @Value("${keycloak.admin-client.username}")
    private String adminUsername;

    @Value("${keycloak.admin-client.password}")
    private String adminPassword;

    // It's often better to use a dedicated admin client or service account for admin operations
    // rather than the same client used for user authentication, if possible and if permissions differ.
    // For this PRD, we assume 'auth-service-client' has a service account enabled and
    // has necessary roles (like 'manage-users' from 'realm-management' client) mapped to it.

    @Bean
    public Keycloak keycloakAdminClient() {
        logger.info("Initializing Keycloak Admin Client for server: {}, realm: {}, client ID: {}",
                keycloakServerUrl, serviceRealm, adminClientId);

        // KeycloakBuilder uses an internal ResteasyClientBuilder.
        // If you need to customize the Resteasy client (e.g., timeouts, proxy),
        // you can build one and pass it to KeycloakBuilder.
        ResteasyClientBuilder resteasyClientBuilder = (ResteasyClientBuilder) ResteasyClientBuilder.newBuilder() // Cast needed
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS);

        return KeycloakBuilder.builder()
                .serverUrl(keycloakServerUrl)
                .realm("master") // Admin operations typically use master realm
                .grantType(OAuth2Constants.PASSWORD) // Use password grant for admin user
                .clientId(adminClientId)
                .clientSecret(adminClientSecret)
                .username(adminUsername)
                .password(adminPassword)
                .resteasyClient(resteasyClientBuilder.build()) // Pass the customized Resteasy client
                .build();
    }
}
