package com.mysillydreams.auth.config;

import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

public class AdditionalSecurityHeadersFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // HTTP Strict Transport Security (HSTS)
        // Tells browsers to always use HTTPS for this domain. Max-age is in seconds.
        // Ensure your service is indeed always served over HTTPS in production before enabling widely.
        response.setHeader(SecurityConstants.HEADER_STRICT_TRANSPORT_SECURITY,
            "max-age=31536000; includeSubDomains; preload");

        // X-Content-Type-Options
        // Prevents browsers from MIME-sniffing a response away from the declared content-type.
        response.setHeader(SecurityConstants.HEADER_X_CONTENT_TYPE_OPTIONS, "nosniff");

        // X-Frame-Options
        // Provides Clickjacking protection. DENY prevents framing entirely.
        response.setHeader(SecurityConstants.HEADER_X_FRAME_OPTIONS, "DENY");

        // X-XSS-Protection (Deprecated by many browsers in favor of CSP, but can still be set for older ones)
        response.setHeader(SecurityConstants.HEADER_X_XSS_PROTECTION, "1; mode=block");

        // Content Security Policy (CSP)
        // For an API, a restrictive policy is good. This prevents loading of unexpected resources.
        response.setHeader(SecurityConstants.HEADER_CONTENT_SECURITY_POLICY,
            "default-src 'self'; script-src 'self'; object-src 'none'; frame-ancestors 'none'; form-action 'self'; base-uri 'self';");

        // Referrer Policy
        // Controls how much referrer information is included with requests
        response.setHeader(SecurityConstants.HEADER_REFERRER_POLICY, "strict-origin-when-cross-origin");

        filterChain.doFilter(request, response);
    }
}
