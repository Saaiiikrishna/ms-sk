package com.mysillydreams.auth.service;

import com.mysillydreams.auth.util.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * JWT Service wrapper around JwtTokenProvider for hybrid authentication.
 */
@Service
public class JwtService {

    private final JwtTokenProvider jwtTokenProvider;

    @Value("${jwt.expiration-ms}")
    private long jwtExpirationInMs;

    @Autowired
    public JwtService(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    /**
     * Generate JWT token for user with specific role.
     */
    public String generateToken(String username, String role) {
        return jwtTokenProvider.generateTokenForUser(username, 
            Collections.singletonList(new SimpleGrantedAuthority(role)));
    }

    /**
     * Extract username from JWT token.
     */
    public String extractUsername(String token) {
        return jwtTokenProvider.getUsernameFromToken(token);
    }

    /**
     * Extract roles from JWT token.
     */
    public String extractRoles(String token) {
        try {
            return jwtTokenProvider.getAuthentication(token)
                .getAuthorities()
                .iterator()
                .next()
                .getAuthority();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Check if token is valid for the given username.
     */
    public boolean isTokenValid(String token, String username) {
        try {
            String tokenUsername = extractUsername(token);
            return username.equals(tokenUsername) && jwtTokenProvider.validateToken(token);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get token expiration time in milliseconds.
     */
    public long getExpirationTime() {
        return jwtExpirationInMs / 1000; // Convert to seconds
    }
}
