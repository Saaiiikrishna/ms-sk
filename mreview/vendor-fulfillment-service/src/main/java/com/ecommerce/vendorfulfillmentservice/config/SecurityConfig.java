package com.ecommerce.vendorfulfillmentservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(jsr250Enabled = true, securedEnabled = true) // Enables @Secured, @RolesAllowed
public class SecurityConfig {

    private final KeycloakRoleConverter keycloakRoleConverter;

    public SecurityConfig(KeycloakRoleConverter keycloakRoleConverter) {
        this.keycloakRoleConverter = keycloakRoleConverter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // Disable CSRF for stateless REST APIs if using tokens, or configure properly
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authorize -> authorize
                // Permit all actuator endpoints for now, can be restricted later
                .requestMatchers("/actuator/**").permitAll()

                // VENDOR specific endpoints
                .requestMatchers(HttpMethod.POST, "/fulfillment/assignments/{id}/ack").hasRole("VENDOR")
                .requestMatchers(HttpMethod.POST, "/fulfillment/assignments/{id}/pack").hasRole("VENDOR")
                .requestMatchers(HttpMethod.POST, "/fulfillment/assignments/{id}/ship").hasRole("VENDOR")
                .requestMatchers(HttpMethod.POST, "/fulfillment/assignments/{id}/complete").hasRole("VENDOR")

                // ADMIN specific endpoints
                .requestMatchers(HttpMethod.PUT, "/fulfillment/assignments/{id}/reassign").hasRole("ADMIN")

                // GET endpoints accessible by VENDOR or ADMIN (more granular checks might be needed in service/controller)
                .requestMatchers(HttpMethod.GET, "/fulfillment/assignments/{id}").hasAnyRole("VENDOR", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/fulfillment/assignments").hasAnyRole("VENDOR", "ADMIN")

                .anyRequest().authenticated() // All other requests require authentication
            )
            // Configure OAuth2 resource server for JWT validation (actual validation happens against Keycloak)
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt ->
                jwt.jwtAuthenticationConverter(keycloakRoleConverter) // Use custom converter
            ));

        return http.build();
    }

    // The KeycloakRoleConverter is now injected and used directly.
    // If a more traditional JwtAuthenticationConverter that extracts authorities and then combines
    // them with a principal extractor is needed, that can be configured here too.
    // For direct role mapping from Keycloak, the current KeycloakRoleConverter (which returns an AbstractAuthenticationToken)
    // is suitable to be plugged into jwtAuthenticationConverter().
}
