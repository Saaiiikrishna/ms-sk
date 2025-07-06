package com.mysillydreams.vendor.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.keycloak.adapters.springsecurity.KeycloakConfiguration;
import org.keycloak.adapters.springsecurity.config.KeycloakWebSecurityConfigurerAdapter;
import org.keycloak.adapters.KeycloakConfigResolver;
import org.keycloak.adapters.springboot.KeycloakSpringBootConfigResolver;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;


@Configuration
@EnableWebSecurity
@EnableMethodSecurity(jsr250Enabled = true) // Enables @RolesAllowed, equivalent to KeycloakWebSecurityConfigurerAdapter behavior
@KeycloakConfiguration // Indicates that this is a Keycloak-based security configuration
public class KeycloakConfig { // No need to extend KeycloakWebSecurityConfigurerAdapter with Spring Security 6+

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // Using lambda DSL for Spring Security 6+
            .authorizeHttpRequests(authz -> authz
                .requestMatchers(HttpMethod.POST, "/vendors/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/vendors/**").hasRole("ADMIN") // Assuming PUT also needs ADMIN
                .requestMatchers(HttpMethod.GET, "/vendors/**").hasAnyRole("ADMIN", "VENDOR")
                .requestMatchers("/actuator/health/**").permitAll() // Allow unauthenticated access to health checks
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll() // Allow swagger if you add it
                .anyRequest().authenticated()
            )
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS) // Typical for REST APIs
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt()); // Configure as an OAuth2 Resource Server validating JWTs

        return http.build();
    }

    // This bean is required by the Keycloak Spring Boot adapter to resolve Keycloak configuration
    // from Spring Boot properties (application.yml or application.properties).
    @Bean
    public KeycloakConfigResolver keycloakConfigResolver() {
        return new KeycloakSpringBootConfigResolver();
    }

    // Note: KeycloakWebSecurityConfigurerAdapter is deprecated and removed in newer Spring Security.
    // The above configuration uses the recommended SecurityFilterChain bean approach.
    // Role mappings (e.g., Keycloak roles to Spring Security authorities) are typically handled
    // by Keycloak's JWT converter or can be customized if needed.
    // For simple role checks like hasRole("ADMIN"), Keycloak roles should be prefixed with "ROLE_"
    // in the token, or you need a custom JwtAuthenticationConverter.
    // Alternatively, if your Keycloak roles are just "ADMIN", "VENDOR", you can use:
    // .requestMatchers(HttpMethod.POST, "/vendors/**").hasAuthority("ROLE_ADMIN")
    // or configure a JwtAuthenticationConverter to add the "ROLE_" prefix.
    // For simplicity, hasRole("ADMIN") assumes Keycloak roles are output as "ROLE_ADMIN".
    // If not, you may need a custom GrantedAuthoritiesMapper or JwtAuthenticationConverter.
}
