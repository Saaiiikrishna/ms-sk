package com.mysillydreams.auth.config;

/**
 * Security constants used throughout the auth service.
 * Centralizes security-related constants for consistency and maintainability.
 */
public final class SecurityConstants {
    
    // Role constants
    public static final String ROLE_ADMIN = "ROLE_ADMIN";
    public static final String ROLE_USER = "ROLE_USER";
    public static final String ROLE_VENDOR = "ROLE_VENDOR";
    public static final String ROLE_DELIVERY = "ROLE_DELIVERY";
    
    // JWT constants
    public static final String JWT_TOKEN_PREFIX = "Bearer ";
    public static final String JWT_HEADER_STRING = "Authorization";
    public static final String JWT_AUTHORITIES_KEY = "roles";
    
    // MFA constants
    public static final String MFA_ISSUER_NAME_DEFAULT = "MySillyDreamsPlatform";
    public static final int MFA_CODE_DIGITS = 6;
    public static final int MFA_TIME_PERIOD_SECONDS = 30;
    
    // Security headers
    public static final String HEADER_X_FRAME_OPTIONS = "X-Frame-Options";
    public static final String HEADER_X_CONTENT_TYPE_OPTIONS = "X-Content-Type-Options";
    public static final String HEADER_X_XSS_PROTECTION = "X-XSS-Protection";
    public static final String HEADER_STRICT_TRANSPORT_SECURITY = "Strict-Transport-Security";
    public static final String HEADER_CONTENT_SECURITY_POLICY = "Content-Security-Policy";
    public static final String HEADER_REFERRER_POLICY = "Referrer-Policy";
    
    // Internal API constants
    public static final String INTERNAL_API_KEY_HEADER = "X-Internal-API-Key";
    
    // Rate limiting constants
    public static final int LOGIN_RATE_LIMIT_REQUESTS = 5;
    public static final int LOGIN_RATE_LIMIT_WINDOW_MINUTES = 15;
    
    // Password rotation constants
    public static final String KEYCLOAK_UPDATE_PASSWORD_ACTION = "UPDATE_PASSWORD";
    
    // Private constructor to prevent instantiation
    private SecurityConstants() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}
