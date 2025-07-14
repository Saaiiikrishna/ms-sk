package com.mysillydreams.gateway.filter;

import com.mysillydreams.gateway.service.JwtService;
import io.micrometer.tracing.annotation.NewSpan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Authentication filter for validating JWT tokens
 */
@Component
public class AuthenticationFilter implements GatewayFilter {

    private static final Logger logger = LoggerFactory.getLogger(AuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String AUTHORIZATION_HEADER = "Authorization";

    private final JwtService jwtService;

    public AuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    @NewSpan("gateway.authentication")
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        
        // Extract JWT token from Authorization header
        String authHeader = request.getHeaders().getFirst(AUTHORIZATION_HEADER);
        
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            logger.warn("Missing or invalid Authorization header for path: {}", request.getPath());
            return handleUnauthorized(exchange);
        }

        String token = authHeader.substring(BEARER_PREFIX.length());
        
        try {
            // Validate JWT token
            if (!jwtService.validateToken(token)) {
                logger.warn("Invalid JWT token for path: {}", request.getPath());
                return handleUnauthorized(exchange);
            }

            // Extract user information from token
            String userId = jwtService.extractUserId(token);
            String username = jwtService.extractUsername(token);
            String roles = jwtService.extractRoles(token);

            // Add user information to request headers for downstream services
            ServerHttpRequest modifiedRequest = request.mutate()
                    .header("X-User-Id", userId)
                    .header("X-Username", username)
                    .header("X-User-Roles", roles)
                    .header("X-Gateway-Validated", "true")
                    .build();

            logger.debug("Authentication successful for user: {} on path: {}", username, request.getPath());
            
            return chain.filter(exchange.mutate().request(modifiedRequest).build());
            
        } catch (Exception e) {
            logger.error("Authentication error for path: {}: {}", request.getPath(), e.getMessage());
            return handleUnauthorized(exchange);
        }
    }

    private Mono<Void> handleUnauthorized(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add(HttpHeaders.CONTENT_TYPE, "application/json");
        
        String body = "{\"error\":\"Unauthorized\",\"message\":\"Invalid or missing authentication token\"}";
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body.getBytes())));
    }
}
