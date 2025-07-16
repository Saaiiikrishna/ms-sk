package com.mysillydreams.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Security headers configuration for enhanced web security.
 * Implements OWASP recommended security headers.
 */
@Configuration
public class SecurityHeadersConfiguration {

    @Value("${security.headers.csp.enabled:true}")
    private boolean cspEnabled;

    @Value("${security.headers.hsts.enabled:true}")
    private boolean hstsEnabled;

    @Value("${security.headers.hsts.max-age:31536000}")
    private long hstsMaxAge;

    @Value("${security.headers.frame-options:DENY}")
    private String frameOptions;

    @Value("${security.headers.content-type-options:nosniff}")
    private String contentTypeOptions;

    @Value("${security.headers.referrer-policy:strict-origin-when-cross-origin}")
    private String referrerPolicy;

    @Value("${security.headers.permissions-policy:geolocation=(), microphone=(), camera=()}")
    private String permissionsPolicy;

    /**
     * Web filter to add security headers to all responses
     */
    @Bean
    public WebFilter securityHeadersFilter() {
        return new SecurityHeadersFilter();
    }

    /**
     * Security headers filter implementation
     */
    public class SecurityHeadersFilter implements WebFilter {

        @Override
        public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
            return chain.filter(exchange).then(Mono.fromRunnable(() -> {
                var response = exchange.getResponse();
                var headers = response.getHeaders();

                // Content Security Policy (CSP)
                if (cspEnabled) {
                    String cspValue = buildContentSecurityPolicy(exchange);
                    headers.add("Content-Security-Policy", cspValue);
                }

                // HTTP Strict Transport Security (HSTS)
                if (hstsEnabled && isHttpsRequest(exchange)) {
                    headers.add("Strict-Transport-Security", 
                        "max-age=" + hstsMaxAge + "; includeSubDomains; preload");
                }

                // X-Frame-Options
                headers.add("X-Frame-Options", frameOptions);

                // X-Content-Type-Options
                headers.add("X-Content-Type-Options", contentTypeOptions);

                // X-XSS-Protection (legacy but still useful)
                headers.add("X-XSS-Protection", "1; mode=block");

                // Referrer Policy
                headers.add("Referrer-Policy", referrerPolicy);

                // Permissions Policy (formerly Feature Policy)
                headers.add("Permissions-Policy", permissionsPolicy);

                // Cache Control for sensitive endpoints
                if (isSensitiveEndpoint(exchange)) {
                    headers.add("Cache-Control", "no-cache, no-store, must-revalidate");
                    headers.add("Pragma", "no-cache");
                    headers.add("Expires", "0");
                }

                // Remove server information
                headers.remove("Server");
                headers.remove("X-Powered-By");

                // Add security-related headers for API responses
                if (isApiEndpoint(exchange)) {
                    headers.add("X-API-Version", "1.0");
                    headers.add("X-Rate-Limit-Remaining", getRateLimitRemaining(exchange));
                }
            }));
        }

        /**
         * Build Content Security Policy based on endpoint type
         */
        private String buildContentSecurityPolicy(ServerWebExchange exchange) {
            String path = exchange.getRequest().getPath().value();
            
            if (path.startsWith("/api/")) {
                // Strict CSP for API endpoints
                return "default-src 'none'; " +
                       "script-src 'none'; " +
                       "style-src 'none'; " +
                       "img-src 'none'; " +
                       "connect-src 'self'; " +
                       "font-src 'none'; " +
                       "object-src 'none'; " +
                       "media-src 'none'; " +
                       "frame-src 'none'; " +
                       "base-uri 'none'; " +
                       "form-action 'none';";
            } else {
                // More permissive CSP for web content
                return "default-src 'self'; " +
                       "script-src 'self' 'unsafe-inline' 'unsafe-eval'; " +
                       "style-src 'self' 'unsafe-inline'; " +
                       "img-src 'self' data: https:; " +
                       "connect-src 'self' ws: wss:; " +
                       "font-src 'self' data:; " +
                       "object-src 'none'; " +
                       "media-src 'self'; " +
                       "frame-src 'none'; " +
                       "base-uri 'self'; " +
                       "form-action 'self';";
            }
        }

        /**
         * Check if request is over HTTPS
         */
        private boolean isHttpsRequest(ServerWebExchange exchange) {
            String scheme = exchange.getRequest().getURI().getScheme();
            return "https".equals(scheme) || 
                   "true".equals(exchange.getRequest().getHeaders().getFirst("X-Forwarded-Proto"));
        }

        /**
         * Check if endpoint contains sensitive data
         */
        private boolean isSensitiveEndpoint(ServerWebExchange exchange) {
            String path = exchange.getRequest().getPath().value();
            return path.contains("/auth/") || 
                   path.contains("/admin/") || 
                   path.contains("/users/") ||
                   path.contains("/password") ||
                   path.contains("/token");
        }

        /**
         * Check if endpoint is an API endpoint
         */
        private boolean isApiEndpoint(ServerWebExchange exchange) {
            String path = exchange.getRequest().getPath().value();
            return path.startsWith("/api/");
        }

        /**
         * Get rate limit remaining from headers (if available)
         */
        private String getRateLimitRemaining(ServerWebExchange exchange) {
            // This would be set by the rate limiting filter
            String remaining = exchange.getResponse().getHeaders().getFirst("X-RateLimit-Remaining");
            return remaining != null ? remaining : "unknown";
        }
    }

    /**
     * Security headers configuration for different environments
     */
    public static class SecurityHeadersConfig {
        
        // Production security headers
        public static final String PROD_CSP = 
            "default-src 'self'; " +
            "script-src 'self'; " +
            "style-src 'self' 'unsafe-inline'; " +
            "img-src 'self' data: https:; " +
            "connect-src 'self'; " +
            "font-src 'self'; " +
            "object-src 'none'; " +
            "media-src 'self'; " +
            "frame-src 'none'; " +
            "base-uri 'self'; " +
            "form-action 'self';";

        // Development security headers (more permissive)
        public static final String DEV_CSP = 
            "default-src 'self' 'unsafe-inline' 'unsafe-eval'; " +
            "connect-src 'self' ws: wss: http://localhost:* https://localhost:*;";

        // HSTS settings
        public static final long HSTS_MAX_AGE_PROD = 31536000L; // 1 year
        public static final long HSTS_MAX_AGE_DEV = 86400L;     // 1 day

        // Frame options
        public static final String FRAME_DENY = "DENY";
        public static final String FRAME_SAMEORIGIN = "SAMEORIGIN";

        // Referrer policies
        public static final String REFERRER_STRICT = "strict-origin-when-cross-origin";
        public static final String REFERRER_SAME_ORIGIN = "same-origin";
    }
}
