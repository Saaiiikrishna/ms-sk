package com.mysillydreams.userservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration for User Service.
 * Validates JWT tokens passed from API Gateway via headers.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authz -> authz
                // Allow actuator endpoints for health checks
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                // Allow Swagger/OpenAPI endpoints in development
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                // All user endpoints require authentication (validated by API Gateway)
                .requestMatchers("/users/**").authenticated()
                // Deny all other requests
                .anyRequest().denyAll()
            )
            // Add custom filter to validate API Gateway headers
            .addFilterBefore(new ApiGatewayAuthenticationFilter(), org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
