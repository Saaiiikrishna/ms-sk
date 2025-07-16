package com.mysillydreams.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

import java.util.Objects;

/**
 * Rate limiting configuration for API Gateway.
 * Implements multiple rate limiting strategies for different scenarios.
 */
@Configuration
public class RateLimitingConfiguration {

    @Value("${rate-limit.default.replenish-rate:10}")
    private int defaultReplenishRate;

    @Value("${rate-limit.default.burst-capacity:20}")
    private int defaultBurstCapacity;

    @Value("${rate-limit.auth.replenish-rate:5}")
    private int authReplenishRate;

    @Value("${rate-limit.auth.burst-capacity:10}")
    private int authBurstCapacity;

    @Value("${rate-limit.api.replenish-rate:100}")
    private int apiReplenishRate;

    @Value("${rate-limit.api.burst-capacity:200}")
    private int apiBurstCapacity;

    /**
     * Default rate limiter for general API requests
     */
    @Bean
    @Primary
    public RedisRateLimiter defaultRateLimiter() {
        return new RedisRateLimiter(defaultReplenishRate, defaultBurstCapacity, 1);
    }

    /**
     * Strict rate limiter for authentication endpoints
     */
    @Bean("authRateLimiter")
    public RedisRateLimiter authRateLimiter() {
        return new RedisRateLimiter(authReplenishRate, authBurstCapacity, 1);
    }

    /**
     * More permissive rate limiter for authenticated API requests
     */
    @Bean("apiRateLimiter")
    public RedisRateLimiter apiRateLimiter() {
        return new RedisRateLimiter(apiReplenishRate, apiBurstCapacity, 1);
    }

    /**
     * IP-based key resolver for anonymous requests
     */
    @Bean("ipKeyResolver")
    public KeyResolver ipKeyResolver() {
        return exchange -> {
            String clientIp = getClientIpAddress(exchange);
            return Mono.just("ip:" + clientIp);
        };
    }

    /**
     * User-based key resolver for authenticated requests
     */
    @Bean("userKeyResolver")
    public KeyResolver userKeyResolver() {
        return exchange -> {
            String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
            if (userId != null && !userId.isEmpty()) {
                return Mono.just("user:" + userId);
            }
            // Fallback to IP if no user ID
            String clientIp = getClientIpAddress(exchange);
            return Mono.just("ip:" + clientIp);
        };
    }

    /**
     * Combined key resolver using both IP and user (for stricter limits)
     */
    @Bean("combinedKeyResolver")
    public KeyResolver combinedKeyResolver() {
        return exchange -> {
            String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
            String clientIp = getClientIpAddress(exchange);
            
            if (userId != null && !userId.isEmpty()) {
                return Mono.just("combined:" + userId + ":" + clientIp);
            }
            return Mono.just("ip:" + clientIp);
        };
    }

    /**
     * Endpoint-specific key resolver for different rate limits per endpoint
     */
    @Bean("endpointKeyResolver")
    public KeyResolver endpointKeyResolver() {
        return exchange -> {
            String path = exchange.getRequest().getPath().value();
            String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
            String clientIp = getClientIpAddress(exchange);
            
            // Create key based on endpoint and user/IP
            String endpoint = extractEndpointCategory(path);
            String identifier = userId != null ? "user:" + userId : "ip:" + clientIp;
            
            return Mono.just("endpoint:" + endpoint + ":" + identifier);
        };
    }

    /**
     * Extract client IP address from request
     */
    private String getClientIpAddress(org.springframework.web.server.ServerWebExchange exchange) {
        String xForwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return Objects.requireNonNull(exchange.getRequest().getRemoteAddress()).getAddress().getHostAddress();
    }

    /**
     * Categorize endpoints for different rate limiting strategies
     */
    private String extractEndpointCategory(String path) {
        if (path.startsWith("/api/auth/login") || path.startsWith("/api/auth/refresh")) {
            return "auth";
        } else if (path.startsWith("/api/auth/")) {
            return "auth-other";
        } else if (path.startsWith("/api/users/")) {
            return "users";
        } else if (path.startsWith("/api/admin/")) {
            return "admin";
        } else {
            return "general";
        }
    }

    /**
     * Rate limiting configuration for different endpoint categories
     */
    public static class RateLimitConfig {
        public static final String AUTH_ENDPOINTS = "auth";
        public static final String API_ENDPOINTS = "api";
        public static final String ADMIN_ENDPOINTS = "admin";
        
        // Rate limits per minute
        public static final int AUTH_LIMIT = 5;      // 5 login attempts per minute
        public static final int API_LIMIT = 100;     // 100 API calls per minute
        public static final int ADMIN_LIMIT = 50;    // 50 admin calls per minute
        
        // Burst capacity (temporary spikes)
        public static final int AUTH_BURST = 10;
        public static final int API_BURST = 200;
        public static final int ADMIN_BURST = 100;
    }
}
