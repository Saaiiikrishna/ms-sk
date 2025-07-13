package com.mysillydreams.auth.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;

import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true, jsr250Enabled = true) // Updated for Spring Security 6.x
public class SecurityConfig {

    @Autowired
    private RateLimitingFilter rateLimitingFilter;

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;





    /**
     * Configures HTTP security rules using SecurityFilterChain (Spring Security 6.x style).
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // Add custom filters for security
            .addFilterBefore(new AdditionalSecurityHeadersFilter(), BasicAuthenticationFilter.class)
            .addFilterBefore(rateLimitingFilter, BasicAuthenticationFilter.class)
            .addFilterBefore(jwtAuthenticationFilter, BasicAuthenticationFilter.class)
            .cors(cors -> cors.configurationSource(corsConfigurationSource())) // Apply CORS configuration
            .csrf(csrf -> csrf.disable()) // Disable CSRF for stateless APIs
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authz -> authz
                // Permit all requests to actuator health and info endpoints for monitoring
                .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                // Permit all requests to /auth/** for login, refresh etc.
                .requestMatchers(HttpMethod.POST, "/auth/login").permitAll()
                .requestMatchers(HttpMethod.POST, "/auth/refresh").permitAll()
                .requestMatchers(HttpMethod.GET, "/auth/validate").permitAll()
                // Permit internal API endpoints (they have their own API key authentication)
                .requestMatchers("/internal/auth/**").permitAll()
                // Secure password rotation endpoint - will be further secured by @PreAuthorize
                .requestMatchers(HttpMethod.POST, "/auth/password-rotate").authenticated()
                // All other requests must be authenticated
                .anyRequest().authenticated()
            );

        return http.build();
    }

    /**
     * Configures CORS.
     * Replace with more restrictive settings for production.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Production-ready CORS configuration
        String allowedOriginsProperty = System.getProperty("app.cors.allowed-origins",
            System.getenv("APP_CORS_ALLOWED_ORIGINS"));

        if (allowedOriginsProperty != null && !allowedOriginsProperty.trim().isEmpty()) {
            // Production: Use configured origins
            configuration.setAllowedOrigins(Arrays.asList(allowedOriginsProperty.split(",")));
        } else {
            // Development: Allow localhost origins only (no wildcard for security)
            configuration.setAllowedOrigins(Arrays.asList(
                "http://localhost:3000",
                "http://localhost:3001",
                "http://localhost:8080",
                "http://127.0.0.1:3000"
            ));
        }
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS")); // PATCH is less common, include if used
        // Consider restricting allowed headers further if possible
        configuration.setAllowedHeaders(Arrays.asList(
                HttpHeaders.AUTHORIZATION,
                HttpHeaders.CONTENT_TYPE,
                HttpHeaders.ACCEPT,
                HttpHeaders.ORIGIN, // Important for CORS
                HttpHeaders.CACHE_CONTROL,
                "X-Requested-With"
        ));
        // Expose headers that clients might need to read
        configuration.setExposedHeaders(Arrays.asList(
                HttpHeaders.LOCATION,
                HttpHeaders.CONTENT_DISPOSITION,
                HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS
        ));
        configuration.setAllowCredentials(true); // Crucial for passing cookies or auth headers from frontend if origins are specific
        configuration.setMaxAge(3600L); // How long the results of a preflight request can be cached

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration); // Apply this configuration to all paths
        return source;
    }
}
