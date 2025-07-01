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
        // Consider `includeSubDomains` and `preload` directives for stronger HSTS.
        response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");

        // X-Content-Type-Options
        // Prevents browsers from MIME-sniffing a response away from the declared content-type.
        response.setHeader("X-Content-Type-Options", "nosniff");

        // X-Frame-Options
        // Provides Clickjacking protection. DENY prevents framing entirely.
        response.setHeader("X-Frame-Options", "DENY");

        // X-XSS-Protection (Deprecated by many browsers in favor of CSP, but can still be set for older ones)
        // response.setHeader("X-XSS-Protection", "1; mode=block");

        // Content Security Policy (CSP)
        // For an API, a restrictive policy is good. This prevents loading of unexpected resources.
        // 'default-src 'none'' is very restrictive. Adjust if your API serves any static content or specific needs.
        // 'frame-ancestors 'none'' is another clickjacking protection.
        response.setHeader("Content-Security-Policy", "default-src 'self'; script-src 'self'; object-src 'none'; frame-ancestors 'none'; form-action 'self'; base-uri 'self';");
        // 'self' allows loading resources from the same origin.

        filterChain.doFilter(request, response);
    }
}
