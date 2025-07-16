package com.mysillydreams.auth.repository;

import com.mysillydreams.auth.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for RefreshToken entities.
 */
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    /**
     * Find a refresh token by token string
     */
    Optional<RefreshToken> findByToken(String token);

    /**
     * Find all valid refresh tokens for a user
     */
    @Query("SELECT rt FROM RefreshToken rt WHERE rt.username = :username AND rt.revoked = false AND rt.expiresAt > :now")
    List<RefreshToken> findValidTokensByUsername(@Param("username") String username, @Param("now") LocalDateTime now);

    /**
     * Find all valid refresh tokens for a user ID
     */
    @Query("SELECT rt FROM RefreshToken rt WHERE rt.userId = :userId AND rt.revoked = false AND rt.expiresAt > :now")
    List<RefreshToken> findValidTokensByUserId(@Param("userId") UUID userId, @Param("now") LocalDateTime now);

    /**
     * Revoke all refresh tokens for a user
     */
    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revoked = true, rt.updatedAt = :now WHERE rt.username = :username AND rt.revoked = false")
    int revokeAllTokensByUsername(@Param("username") String username, @Param("now") LocalDateTime now);

    /**
     * Revoke all refresh tokens for a user ID
     */
    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revoked = true, rt.updatedAt = :now WHERE rt.userId = :userId AND rt.revoked = false")
    int revokeAllTokensByUserId(@Param("userId") UUID userId, @Param("now") LocalDateTime now);

    /**
     * Delete expired tokens (cleanup job)
     */
    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt < :now")
    int deleteExpiredTokens(@Param("now") LocalDateTime now);

    /**
     * Count valid tokens for a user (for limiting concurrent sessions)
     */
    @Query("SELECT COUNT(rt) FROM RefreshToken rt WHERE rt.username = :username AND rt.revoked = false AND rt.expiresAt > :now")
    long countValidTokensByUsername(@Param("username") String username, @Param("now") LocalDateTime now);

    /**
     * Find tokens by IP address (for security monitoring)
     */
    @Query("SELECT rt FROM RefreshToken rt WHERE rt.ipAddress = :ipAddress AND rt.revoked = false AND rt.expiresAt > :now")
    List<RefreshToken> findValidTokensByIpAddress(@Param("ipAddress") String ipAddress, @Param("now") LocalDateTime now);
}
