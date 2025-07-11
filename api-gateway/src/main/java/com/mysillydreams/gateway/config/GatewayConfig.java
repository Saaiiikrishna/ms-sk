package com.mysillydreams.gateway.config;

import com.mysillydreams.gateway.filter.AuthenticationFilter;
import com.mysillydreams.gateway.filter.TracingFilter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Arrays;

/**
 * Gateway routing and configuration
 */
@Configuration
public class GatewayConfig {

    private final AuthenticationFilter authenticationFilter;
    private final TracingFilter tracingFilter;

    public GatewayConfig(AuthenticationFilter authenticationFilter, TracingFilter tracingFilter) {
        this.authenticationFilter = authenticationFilter;
        this.tracingFilter = tracingFilter;
    }

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                // Auth Service Routes
                .route("auth-login", r -> r
                        .path("/api/auth/login", "/api/auth/refresh", "/api/auth/validate")
                        .filters(f -> f
                                .filter(tracingFilter)
                                .circuitBreaker(config -> config
                                        .setName("auth-service-cb")
                                        .setFallbackUri("forward:/fallback/auth"))
                                .retry(config -> config
                                        .setRetries(3)
                                        .setBackoff(Duration.ofMillis(100), Duration.ofMillis(1000), 2, false)))
                        .uri("lb://auth-service"))
                
                .route("auth-admin", r -> r
                        .path("/api/auth/admin/**", "/api/auth/password-rotate")
                        .filters(f -> f
                                .filter(authenticationFilter)
                                .filter(tracingFilter)
                                .circuitBreaker(config -> config
                                        .setName("auth-admin-cb")
                                        .setFallbackUri("forward:/fallback/auth")))
                        .uri("lb://auth-service"))

                // User Service Routes
                .route("user-service", r -> r
                        .path("/api/users/**")
                        .filters(f -> f
                                .filter(authenticationFilter)
                                .filter(tracingFilter)
                                .circuitBreaker(config -> config
                                        .setName("user-service-cb")
                                        .setFallbackUri("forward:/fallback/user")))
                        .uri("lb://user-service"))

                // Admin Server Routes (for monitoring)
                .route("admin-server", r -> r
                        .path("/api/admin-server/**")
                        .filters(f -> f
                                .filter(authenticationFilter)
                                .filter(tracingFilter)
                                .stripPrefix(1))
                        .uri("lb://admin-server"))

                // Internal Service Communication (highly secured)
                .route("internal-auth", r -> r
                        .path("/api/internal/auth/**")
                        .filters(f -> f
                                .filter(authenticationFilter)
                                .filter(tracingFilter)
                                .requestRateLimiter(config -> config
                                        .setRateLimiter(redisRateLimiter())
                                        .setKeyResolver(exchange ->
                                            Mono.just(exchange.getRequest().getHeaders().getFirst("X-Internal-Service") != null ?
                                                exchange.getRequest().getHeaders().getFirst("X-Internal-Service") :
                                                exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()))))
                        .uri("lb://auth-service"))

                // Zookeeper Configuration Service Routes (admin only)
                .route("zookeeper-service", r -> r
                        .path("/api/config/**")
                        .filters(f -> f
                                .filter(authenticationFilter)
                                .filter(tracingFilter)
                                .circuitBreaker(config -> config
                                        .setName("zookeeper-service-cb")
                                        .setFallbackUri("forward:/fallback/config")))
                        .uri("lb://zookeeper-service"))

                // Health Check Routes (no auth required)
                .route("health-checks", r -> r
                        .path("/api/health/**", "/api/actuator/health/**")
                        .filters(f -> f
                                .filter(tracingFilter)
                                .stripPrefix(1))
                        .uri("lb://auth-service"))

                .build();
    }

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration corsConfig = new CorsConfiguration();
        corsConfig.setAllowedOriginPatterns(Arrays.asList("*"));
        corsConfig.setMaxAge(3600L);
        corsConfig.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        corsConfig.setAllowedHeaders(Arrays.asList("*"));
        corsConfig.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfig);

        return new CorsWebFilter(source);
    }

    @Bean
    public org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter redisRateLimiter() {
        return new org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter(10, 20, 1);
    }
}
