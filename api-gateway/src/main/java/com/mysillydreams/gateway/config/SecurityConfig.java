package com.mysillydreams.gateway.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.csrf.ServerCsrfTokenRepository;

/**
 * Security configuration for API Gateway
 * Disables CSRF protection for stateless API operations
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Autowired
    private ServerCsrfTokenRepository csrfTokenRepository;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
            // Configure CSRF protection for browser requests
            .csrf(csrf -> csrf
                .csrfTokenRepository(csrfTokenRepository)
                .requireCsrfProtectionMatcher(exchange -> {
                    String path = exchange.getRequest().getPath().value();
                    String method = exchange.getRequest().getMethod().name();

                    // Skip CSRF for GET, HEAD, OPTIONS requests
                    if ("GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method)) {
                        return false;
                    }

                    // Skip CSRF for exempt paths
                    return !CsrfConfiguration.CsrfMatcher.isCsrfExempt(path);
                })
            )

            // Configure authorization rules
            .authorizeExchange(exchanges -> exchanges
                // Allow actuator endpoints
                .pathMatchers("/actuator/**").permitAll()

                // Allow auth endpoints (login, refresh, validate) without authentication
                .pathMatchers("/api/auth/login", "/api/auth/refresh", "/api/auth/validate").permitAll()

                // Allow health check endpoints
                .pathMatchers("/api/health/**", "/health/**").permitAll()

                // All other API requests are handled by custom filters (AuthenticationFilter)
                // The gateway validates JWT tokens and adds user info headers for downstream services
                .pathMatchers("/api/**").permitAll()

                // Deny all other requests that don't go through /api/ prefix
                .anyExchange().denyAll()
            )

            .build();
    }
}
