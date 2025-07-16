package com.mysillydreams.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.server.csrf.CookieServerCsrfTokenRepository;
import org.springframework.security.web.server.csrf.ServerCsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * CSRF and CORS configuration for API Gateway.
 * Provides protection against Cross-Site Request Forgery attacks.
 */
@Configuration
public class CsrfConfiguration {

    /**
     * CSRF token repository using cookies
     */
    @Bean
    public ServerCsrfTokenRepository csrfTokenRepository() {
        CookieServerCsrfTokenRepository repository = CookieServerCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieName("XSRF-TOKEN");
        repository.setHeaderName("X-XSRF-TOKEN");
        repository.setCookiePath("/");
        repository.setCookieMaxAge(3600); // 1 hour
        return repository;
    }

    /**
     * Enhanced CORS configuration for production
     */
    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration corsConfig = new CorsConfiguration();
        
        // Allow specific origins (configure based on environment)
        corsConfig.setAllowedOriginPatterns(Arrays.asList(
            "http://localhost:3000",    // React dev server
            "http://localhost:3001",    // Alternative React port
            "http://localhost:5173",    // Vite dev server
            "http://localhost:8080",    // Gateway itself
            "https://*.mysillydreams.com" // Production domains
        ));
        
        // Allow specific methods
        corsConfig.setAllowedMethods(Arrays.asList(
            "GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"
        ));
        
        // Allow specific headers
        corsConfig.setAllowedHeaders(Arrays.asList(
            "Authorization",
            "Content-Type",
            "X-Requested-With",
            "X-XSRF-TOKEN",
            "Cache-Control",
            "Pragma"
        ));
        
        // Expose headers that frontend can read
        corsConfig.setExposedHeaders(Arrays.asList(
            "X-XSRF-TOKEN",
            "Authorization"
        ));
        
        // Allow credentials (cookies, authorization headers)
        corsConfig.setAllowCredentials(true);
        
        // Cache preflight requests for 1 hour
        corsConfig.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfig);
        
        return new CorsWebFilter(source);
    }

    /**
     * CSRF matcher to determine which requests need CSRF protection
     */
    public static class CsrfMatcher {
        
        /**
         * Paths that should be exempt from CSRF protection
         */
        public static final List<String> CSRF_EXEMPT_PATHS = Arrays.asList(
            "/api/auth/login",
            "/api/auth/refresh",
            "/api/auth/validate",
            "/actuator/**",
            "/api/health/**"
        );
        
        /**
         * Check if a path should be exempt from CSRF protection
         */
        public static boolean isCsrfExempt(String path) {
            return CSRF_EXEMPT_PATHS.stream()
                .anyMatch(exemptPath -> {
                    if (exemptPath.endsWith("/**")) {
                        String prefix = exemptPath.substring(0, exemptPath.length() - 3);
                        return path.startsWith(prefix);
                    }
                    return path.equals(exemptPath);
                });
        }
    }
}
