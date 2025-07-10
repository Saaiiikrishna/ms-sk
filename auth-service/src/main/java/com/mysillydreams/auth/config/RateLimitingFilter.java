package com.mysillydreams.auth.config;

import com.mysillydreams.auth.config.SecurityConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Rate limiting filter for authentication endpoints.
 * Implements a simple sliding window rate limiter to prevent brute force attacks.
 */
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitingFilter.class);
    
    // In-memory store for rate limiting (in production, consider Redis)
    private final ConcurrentMap<String, RateLimitInfo> rateLimitStore = new ConcurrentHashMap<>();
    
    // Cleanup interval to prevent memory leaks
    private static final long CLEANUP_INTERVAL_MINUTES = 30;
    private volatile Instant lastCleanup = Instant.now();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, 
                                  FilterChain filterChain) throws ServletException, IOException {
        
        String requestURI = request.getRequestURI();
        String method = request.getMethod();
        
        // Only apply rate limiting to login endpoint
        if ("POST".equals(method) && requestURI.endsWith("/auth/login")) {
            String clientIp = getClientIpAddress(request);
            
            if (isRateLimited(clientIp)) {
                logger.warn("Rate limit exceeded for IP: {} on login endpoint", clientIp);
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Too many login attempts. Please try again later.\"}");
                return;
            }
            
            // Record the attempt
            recordAttempt(clientIp);
        }
        
        // Periodic cleanup to prevent memory leaks
        performPeriodicCleanup();
        
        filterChain.doFilter(request, response);
    }

    private boolean isRateLimited(String clientIp) {
        RateLimitInfo info = rateLimitStore.get(clientIp);
        if (info == null) {
            return false;
        }
        
        Instant windowStart = Instant.now().minus(SecurityConstants.LOGIN_RATE_LIMIT_WINDOW_MINUTES, ChronoUnit.MINUTES);
        
        // Remove old attempts outside the window
        info.attempts.removeIf(attempt -> attempt.isBefore(windowStart));
        
        return info.attempts.size() >= SecurityConstants.LOGIN_RATE_LIMIT_REQUESTS;
    }

    private void recordAttempt(String clientIp) {
        rateLimitStore.computeIfAbsent(clientIp, k -> new RateLimitInfo()).attempts.add(Instant.now());
    }

    private String getClientIpAddress(HttpServletRequest request) {
        // Check for X-Forwarded-For header (common in load balancers/proxies)
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // X-Forwarded-For can contain multiple IPs, take the first one
            return xForwardedFor.split(",")[0].trim();
        }
        
        // Check for X-Real-IP header (common in nginx)
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        // Fallback to remote address
        return request.getRemoteAddr();
    }

    private void performPeriodicCleanup() {
        Instant now = Instant.now();
        if (now.isAfter(lastCleanup.plus(CLEANUP_INTERVAL_MINUTES, ChronoUnit.MINUTES))) {
            cleanupExpiredEntries();
            lastCleanup = now;
        }
    }

    private void cleanupExpiredEntries() {
        Instant cutoff = Instant.now().minus(SecurityConstants.LOGIN_RATE_LIMIT_WINDOW_MINUTES * 2, ChronoUnit.MINUTES);
        
        rateLimitStore.entrySet().removeIf(entry -> {
            RateLimitInfo info = entry.getValue();
            info.attempts.removeIf(attempt -> attempt.isBefore(cutoff));
            return info.attempts.isEmpty();
        });
        
        logger.debug("Rate limit store cleanup completed. Current entries: {}", rateLimitStore.size());
    }

    /**
     * Internal class to track rate limit information for each IP
     */
    private static class RateLimitInfo {
        private final java.util.List<Instant> attempts = new java.util.concurrent.CopyOnWriteArrayList<>();
    }
}
