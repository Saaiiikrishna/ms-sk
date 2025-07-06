package com.mysillydreams.orderapi.filter;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Bucket4j;
import io.github.bucket4j.Refill;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
// Run after security and idempotency, but before main controller logic.
// IdempotencyFilter is HIGHEST_PRECEDENCE + 1.
// Keycloak security filters are around Ordered.HIGHEST_PRECEDENCE.
// Let's place this after IdempotencyFilter.
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    private final Bucket bucket;

    // For per-IP rate limiting (more advanced, example shown)
    // private final Map<String, Bucket> ipBuckets = new ConcurrentHashMap<>();

    public RateLimitFilter(
            @Value("${app.ratelimit.capacity:100}") long capacity,
            @Value("${app.ratelimit.refill-tokens:100}") long refillTokens,
            @Value("${app.ratelimit.refill-duration-minutes:1}") long refillDurationMinutes) {

        Refill refill = Refill.greedy(refillTokens, Duration.ofMinutes(refillDurationMinutes));
        Bandwidth limit = Bandwidth.classic(capacity, refill);
        this.bucket = Bucket4j.builder().addLimit(limit).build();
        log.info("Rate limiter configured: capacity={}, refillTokens={}, refillDurationMinutes={}", capacity, refillTokens, refillDurationMinutes);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // Example for per-IP rate limiting (more complex state management needed for distributed env)
        /*
        String clientIp = getClientIp(request);
        Bucket ipBucket = ipBuckets.computeIfAbsent(clientIp, k -> {
            Refill refill = Refill.greedy(10, Duration.ofMinutes(1)); // e.g., 10 requests per minute per IP
            Bandwidth limit = Bandwidth.classic(10, refill);
            return Bucket4j.builder().addLimit(limit).build();
        });

        if (ipBucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            log.warn("Rate limit exceeded for IP: {}", clientIp);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.getWriter().write("Rate limit exceeded for your IP address.");
        }
        */

        // Global rate limiting as per the simpler plan
        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            log.warn("Global rate limit exceeded for request: {}", request.getRequestURI());
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            // Optionally, write a JSON error response similar to GlobalExceptionHandler
            response.setContentType("application/json"); // Ensure content type
            response.getWriter().write("{\"error\":\"TOO_MANY_REQUESTS\",\"message\":\"Global rate limit exceeded. Please try again later.\"}");
        }
    }

    // Helper to get client IP (simplified)
    /*
    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null || xfHeader.isEmpty() || "unknown".equalsIgnoreCase(xfHeader)) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0];
    }
    */

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        // Apply rate limiting to all API requests by default.
        // Can be configured to exclude certain paths, e.g., actuator health checks if they are public.
        // For now, let's assume it applies to /orders/**
        // If actuator endpoints are exposed via a different port or context path not covered by this filter's dispatcher types, they might be fine.
        // return !request.getRequestURI().startsWith("/orders"); // Example: only rate limit /orders
        return false; // Apply to all requests handled by this filter
    }
}
