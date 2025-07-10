package com.mysillydreams.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

@SpringBootApplication
@EnableDiscoveryClient(
        // We are using Keycloak for user management, so we can exclude Spring Boot's
        // default UserDetailsService auto-configuration if it causes conflicts or
        // if we want to be explicit that Keycloak is the source of truth.
        // However, KeycloakSpringBootAdapterAutoConfiguration should handle this.
        // For now, let's keep it simple. If an in-memory user is created by default,
        // this exclude might be useful.
        // exclude = {UserDetailsServiceAutoConfiguration.class}
)
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }

}
