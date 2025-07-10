package com.mysillydreams.auth.repository;

import com.mysillydreams.auth.entity.AdminCreationSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for managing admin creation sessions.
 */
@Repository
public interface AdminCreationSessionRepository extends JpaRepository<AdminCreationSession, UUID> {

    /**
     * Find session by session ID and ensure it's not expired.
     */
    @Query("SELECT s FROM AdminCreationSession s WHERE s.sessionId = :sessionId AND s.expiresAt > :now")
    Optional<AdminCreationSession> findBySessionIdAndNotExpired(@Param("sessionId") UUID sessionId, @Param("now") LocalDateTime now);

    /**
     * Find all sessions created by a specific admin.
     */
    List<AdminCreationSession> findByCurrentAdminIdOrderByCreatedAtDesc(UUID currentAdminId);

    /**
     * Find sessions by step.
     */
    List<AdminCreationSession> findByStep(Integer step);

    /**
     * Find expired sessions for cleanup.
     */
    @Query("SELECT s FROM AdminCreationSession s WHERE s.expiresAt <= :now")
    List<AdminCreationSession> findExpiredSessions(@Param("now") LocalDateTime now);

    /**
     * Delete expired sessions.
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM AdminCreationSession s WHERE s.expiresAt <= :now")
    int deleteExpiredSessions(@Param("now") LocalDateTime now);

    /**
     * Count active sessions for a specific admin.
     */
    @Query("SELECT COUNT(s) FROM AdminCreationSession s WHERE s.currentAdminId = :adminId AND s.expiresAt > :now")
    long countActiveSessionsByAdmin(@Param("adminId") UUID adminId, @Param("now") LocalDateTime now);

    /**
     * Find sessions that are stuck in a specific step for too long.
     */
    @Query("SELECT s FROM AdminCreationSession s WHERE s.step = :step AND s.createdAt < :cutoffTime")
    List<AdminCreationSession> findStuckSessions(@Param("step") Integer step, @Param("cutoffTime") LocalDateTime cutoffTime);

    /**
     * Update session step.
     */
    @Modifying
    @Transactional
    @Query("UPDATE AdminCreationSession s SET s.step = :step WHERE s.sessionId = :sessionId")
    int updateSessionStep(@Param("sessionId") UUID sessionId, @Param("step") Integer step);

    /**
     * Set new admin ID for a session.
     */
    @Modifying
    @Transactional
    @Query("UPDATE AdminCreationSession s SET s.newAdminId = :newAdminId WHERE s.sessionId = :sessionId")
    int setNewAdminId(@Param("sessionId") UUID sessionId, @Param("newAdminId") UUID newAdminId);

    /**
     * Set MFA secret for a session.
     */
    @Modifying
    @Transactional
    @Query("UPDATE AdminCreationSession s SET s.mfaSecret = :mfaSecret WHERE s.sessionId = :sessionId")
    int setMfaSecret(@Param("sessionId") UUID sessionId, @Param("mfaSecret") String mfaSecret);
}
