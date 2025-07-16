package com.mysillydreams.userservice.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Authentication filter that validates requests from API Gateway.
 * The API Gateway validates JWT tokens and passes user information via headers.
 */
public class ApiGatewayAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(ApiGatewayAuthenticationFilter.class);
    
    private static final String GATEWAY_VALIDATED_HEADER = "X-Gateway-Validated";
    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USERNAME_HEADER = "X-Username";
    private static final String USER_ROLES_HEADER = "X-User-Roles";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, 
                                  FilterChain filterChain) throws ServletException, IOException {
        
        String path = request.getRequestURI();
        
        // Skip authentication for public endpoints
        if (isPublicEndpoint(path)) {
            filterChain.doFilter(request, response);
            return;
        }
        
        // Check if request was validated by API Gateway
        String gatewayValidated = request.getHeader(GATEWAY_VALIDATED_HEADER);
        if (!"true".equals(gatewayValidated)) {
            logger.warn("Request to {} not validated by API Gateway", path);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\":\"Unauthorized - Request must go through API Gateway\"}");
            return;
        }
        
        // Extract user information from headers
        String userId = request.getHeader(USER_ID_HEADER);
        String username = request.getHeader(USERNAME_HEADER);
        String rolesHeader = request.getHeader(USER_ROLES_HEADER);
        
        if (username == null || username.trim().isEmpty()) {
            logger.warn("Missing username in API Gateway headers for path: {}", path);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\":\"Unauthorized - Missing user information\"}");
            return;
        }
        
        // Parse roles
        List<SimpleGrantedAuthority> authorities = parseRoles(rolesHeader);
        
        // Create authentication token
        UsernamePasswordAuthenticationToken authentication = 
            new UsernamePasswordAuthenticationToken(username, null, authorities);
        
        // Add user ID as detail for easy access in controllers
        authentication.setDetails(userId);
        
        // Set authentication in security context
        SecurityContextHolder.getContext().setAuthentication(authentication);
        
        logger.debug("Authenticated user {} with roles {} for path {}", username, rolesHeader, path);
        
        filterChain.doFilter(request, response);
    }
    
    private boolean isPublicEndpoint(String path) {
        return path.startsWith("/actuator/health") || 
               path.startsWith("/actuator/info") ||
               path.startsWith("/swagger-ui") ||
               path.startsWith("/v3/api-docs");
    }
    
    private List<SimpleGrantedAuthority> parseRoles(String rolesHeader) {
        if (rolesHeader == null || rolesHeader.trim().isEmpty()) {
            return List.of();
        }
        
        return Arrays.stream(rolesHeader.split(","))
                .map(String::trim)
                .filter(role -> !role.isEmpty())
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
    }
}
