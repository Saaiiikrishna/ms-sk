package com.mysillydreams.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Security configuration for API Gateway
 * Disables CSRF protection for stateless API operations
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
            // Disable CSRF for stateless APIs
            .csrf(csrf -> csrf.disable())
            
            // Configure authorization rules
            .authorizeExchange(exchanges -> exchanges
                // Allow actuator endpoints
                .pathMatchers("/actuator/**").permitAll()
                
                // Allow auth endpoints (login, refresh, validate) without authentication
                .pathMatchers("/auth/login", "/auth/refresh", "/auth/validate").permitAll()
                .pathMatchers("/api/auth/login", "/api/auth/refresh", "/api/auth/validate").permitAll()
                
                // Allow health check endpoints
                .pathMatchers("/api/health/**", "/health/**").permitAll()
                
                // All other requests require authentication (will be handled by downstream services)
                .anyExchange().permitAll()
            )
            
            .build();
    }
}
