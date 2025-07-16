package com.mysillydreams.auth.service;

import com.mysillydreams.auth.domain.RefreshToken;
import com.mysillydreams.auth.repository.RefreshTokenRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing refresh tokens securely.
 * Provides token generation, validation, and cleanup.
 */
@Service
@Transactional
public class RefreshTokenService {

    private static final Logger logger = LoggerFactory.getLogger(RefreshTokenService.class);

    private final RefreshTokenRepository refreshTokenRepository;
    private final SecureRandom secureRandom;

    @Value("${jwt.refresh-expiration-hours:168}") // 7 days default
    private int refreshTokenExpirationHours;

    @Value("${jwt.max-concurrent-sessions:5}")
    private int maxConcurrentSessions;

    @Autowired
    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.secureRandom = new SecureRandom();
    }

    /**
     * Generate a new refresh token for a user
     */
    public RefreshToken generateRefreshToken(String username, UUID userId, HttpServletRequest request) {
        // Generate cryptographically secure random token
        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);

        LocalDateTime expiresAt = LocalDateTime.now().plusHours(refreshTokenExpirationHours);
        
        RefreshToken refreshToken = new RefreshToken(token, username, userId, expiresAt);
        
        // Add request metadata for security tracking
        if (request != null) {
            refreshToken.setIpAddress(getClientIpAddress(request));
            refreshToken.setUserAgent(request.getHeader("User-Agent"));
        }

        // Limit concurrent sessions
        limitConcurrentSessions(username);

        RefreshToken savedToken = refreshTokenRepository.save(refreshToken);
        logger.info("Generated refresh token for user: {} (expires: {})", username, expiresAt);
        
        return savedToken;
    }

    /**
     * Validate and retrieve refresh token
     */
    @Transactional(readOnly = true)
    public Optional<RefreshToken> validateRefreshToken(String token) {
        Optional<RefreshToken> refreshTokenOpt = refreshTokenRepository.findByToken(token);
        
        if (refreshTokenOpt.isEmpty()) {
            logger.warn("Refresh token not found: {}", token.substring(0, Math.min(token.length(), 10)) + "...");
            return Optional.empty();
        }

        RefreshToken refreshToken = refreshTokenOpt.get();
        
        if (!refreshToken.isValid()) {
            logger.warn("Invalid refresh token for user: {} (expired: {}, revoked: {})", 
                refreshToken.getUsername(), refreshToken.isExpired(), refreshToken.isRevoked());
            return Optional.empty();
        }

        logger.debug("Refresh token validated for user: {}", refreshToken.getUsername());
        return refreshTokenOpt;
    }

    /**
     * Revoke a specific refresh token
     */
    public void revokeRefreshToken(String token) {
        Optional<RefreshToken> refreshTokenOpt = refreshTokenRepository.findByToken(token);
        
        if (refreshTokenOpt.isPresent()) {
            RefreshToken refreshToken = refreshTokenOpt.get();
            refreshToken.revoke();
            refreshTokenRepository.save(refreshToken);
            logger.info("Revoked refresh token for user: {}", refreshToken.getUsername());
        }
    }

    /**
     * Revoke all refresh tokens for a user (logout from all devices)
     */
    public void revokeAllUserTokens(String username) {
        int revokedCount = refreshTokenRepository.revokeAllTokensByUsername(username, LocalDateTime.now());
        logger.info("Revoked {} refresh tokens for user: {}", revokedCount, username);
    }

    /**
     * Revoke all refresh tokens for a user ID
     */
    public void revokeAllUserTokens(UUID userId) {
        int revokedCount = refreshTokenRepository.revokeAllTokensByUserId(userId, LocalDateTime.now());
        logger.info("Revoked {} refresh tokens for user ID: {}", revokedCount, userId);
    }

    /**
     * Get all valid refresh tokens for a user
     */
    @Transactional(readOnly = true)
    public List<RefreshToken> getUserValidTokens(String username) {
        return refreshTokenRepository.findValidTokensByUsername(username, LocalDateTime.now());
    }

    /**
     * Limit concurrent sessions by revoking oldest tokens
     */
    private void limitConcurrentSessions(String username) {
        long currentTokenCount = refreshTokenRepository.countValidTokensByUsername(username, LocalDateTime.now());
        
        if (currentTokenCount >= maxConcurrentSessions) {
            List<RefreshToken> userTokens = refreshTokenRepository.findValidTokensByUsername(username, LocalDateTime.now());
            
            // Sort by creation date and revoke oldest tokens
            userTokens.stream()
                .sorted((t1, t2) -> t1.getCreatedAt().compareTo(t2.getCreatedAt()))
                .limit(currentTokenCount - maxConcurrentSessions + 1)
                .forEach(token -> {
                    token.revoke();
                    refreshTokenRepository.save(token);
                });
            
            logger.info("Limited concurrent sessions for user: {} (revoked {} old tokens)", 
                username, Math.max(0, currentTokenCount - maxConcurrentSessions + 1));
        }
    }

    /**
     * Get client IP address from request
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }

    /**
     * Cleanup expired tokens (scheduled task)
     */
    @Scheduled(fixedRate = 3600000) // Run every hour
    public void cleanupExpiredTokens() {
        int deletedCount = refreshTokenRepository.deleteExpiredTokens(LocalDateTime.now());
        if (deletedCount > 0) {
            logger.info("Cleaned up {} expired refresh tokens", deletedCount);
        }
    }
}
