package com.mysillydreams.auth.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User; // Spring Security User
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class JwtTokenProvider {

    private static final Logger logger = LoggerFactory.getLogger(JwtTokenProvider.class);

    @Value("${jwt.expiration-ms}")
    private long jwtExpirationInMs;

    private final SecretKey jwtSecretKey;
    private final SecretKey jwtRefreshSecretKey;
    private static final String AUTHORITIES_KEY = "roles";

    // Constructor injection for Vault-based secret keys
    public JwtTokenProvider(
            @Qualifier("jwtSecretKey") SecretKey jwtSecretKey,
            @Qualifier("jwtRefreshSecretKey") SecretKey jwtRefreshSecretKey) {
        this.jwtSecretKey = jwtSecretKey;
        this.jwtRefreshSecretKey = jwtRefreshSecretKey;
        logger.info("JwtTokenProvider initialized with Vault-based secret keys");
    }

    // Fallback constructor for environments without Vault
    public JwtTokenProvider(@Value("${jwt.secret:}") String jwtSecretString) {
        if (jwtSecretString == null || jwtSecretString.length() < 64) {
            logger.warn("JWT secret is weak or not configured. Using a generated key. THIS IS NOT SAFE FOR PRODUCTION.");
            this.jwtSecretKey = Keys.secretKeyFor(SignatureAlgorithm.HS512);
            this.jwtRefreshSecretKey = Keys.secretKeyFor(SignatureAlgorithm.HS512);
        } else {
            this.jwtSecretKey = Keys.hmacShaKeyFor(jwtSecretString.getBytes(StandardCharsets.UTF_8));
            this.jwtRefreshSecretKey = Keys.hmacShaKeyFor((jwtSecretString + "_refresh").getBytes(StandardCharsets.UTF_8));
        }
        logger.info("JwtTokenProvider initialized with fallback configuration");
    }

    public String generateToken(Authentication authentication) {
        String username = authentication.getName();
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        return generateTokenForUser(username, authorities);
    }

    public String generateTokenForUser(String username, Collection<? extends GrantedAuthority> authorities) {
        String roles = authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));

        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationInMs);

        logger.debug("Generating JWT for user: {}, roles: {}", username, roles);

        return Jwts.builder()
                .setSubject(username)
                .claim(AUTHORITIES_KEY, roles)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(jwtSecretKey, SignatureAlgorithm.HS512)
                .compact();
    }

    /**
     * Generate refresh token with longer expiration
     */
    public String generateRefreshToken(String username, Collection<? extends GrantedAuthority> authorities) {
        String roles = authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));

        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + (jwtExpirationInMs * 7)); // 7 times longer than access token

        logger.debug("Generating refresh token for user: {}", username);

        return Jwts.builder()
                .setSubject(username)
                .claim(AUTHORITIES_KEY, roles)
                .claim("token_type", "refresh")
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(jwtRefreshSecretKey, SignatureAlgorithm.HS512)
                .compact();
    }

    /**
     * Validate refresh token using refresh secret key
     */
    public boolean validateRefreshToken(String token) {
        try {
            Jwts.parser()
                    .setSigningKey(jwtRefreshSecretKey)
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            logger.debug("Refresh token validation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Get authentication from refresh token
     */
    public Authentication getAuthenticationFromRefreshToken(String token) {
        Claims claims = Jwts.parser()
                .setSigningKey(jwtRefreshSecretKey)
                .build()
                .parseClaimsJws(token)
                .getBody();

        String username = claims.getSubject();
        String authoritiesStr = claims.get(AUTHORITIES_KEY, String.class);

        Collection<? extends GrantedAuthority> authorities = authoritiesStr != null ?
                Arrays.stream(authoritiesStr.split(","))
                        .map(SimpleGrantedAuthority::new)
                        .collect(Collectors.toList()) :
                List.of();

        User principal = new User(username, "", authorities);
        return new UsernamePasswordAuthenticationToken(principal, "", authorities);
    }

    public Authentication getAuthentication(String token) {
        Claims claims = Jwts.parser()
                .setSigningKey(jwtSecretKey)
                .build()
                .parseClaimsJws(token)
                .getBody();

        String username = claims.getSubject();
        Collection<? extends GrantedAuthority> authorities =
                Arrays.stream(claims.get(AUTHORITIES_KEY).toString().split(","))
                        .filter(auth -> auth != null && !auth.trim().isEmpty())
                        .map(SimpleGrantedAuthority::new)
                        .collect(Collectors.toList());

        logger.debug("Extracted authentication for user: {}, roles: {} from JWT", username, authorities);
        // Using Spring Security's User object for simplicity.
        // You might use a custom UserDetails object if you have more user-specific fields.
        User principal = new User(username, "", authorities);
        return new UsernamePasswordAuthenticationToken(principal, token, authorities);
    }

    public boolean validateToken(String authToken) {
        if (authToken == null || authToken.trim().isEmpty()) {
            logger.warn("JWT token is null or empty.");
            return false;
        }
        try {
            Jwts.parser().setSigningKey(jwtSecretKey).build().parseClaimsJws(authToken);
            logger.debug("JWT token validated successfully.");
            return true;
        } catch (SignatureException ex) {
            logger.error("Invalid JWT signature: {}", ex.getMessage());
        } catch (MalformedJwtException ex) {
            logger.error("Invalid JWT token: {}", ex.getMessage());
        } catch (ExpiredJwtException ex) {
            logger.error("Expired JWT token: {}", ex.getMessage());
        } catch (UnsupportedJwtException ex) {
            logger.error("Unsupported JWT token: {}", ex.getMessage());
        } catch (IllegalArgumentException ex) {
            logger.error("JWT claims string is empty: {}", ex.getMessage());
        }
        return false;
    }

    public String getUsernameFromToken(String token) {
        Claims claims = Jwts.parser()
                .setSigningKey(jwtSecretKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
        return claims.getSubject();
    }

    public Long getExpiryDateFromToken(String token) {
        Claims claims = Jwts.parser()
                .setSigningKey(jwtSecretKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
        return claims.getExpiration().getTime();
    }
}
