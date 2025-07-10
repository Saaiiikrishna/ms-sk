package com.mysillydreams.auth.repository;

import com.mysillydreams.auth.entity.AdminAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repository for admin audit log operations.
 */
@Repository
public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, UUID> {

    /**
     * Find audit logs by admin ID with pagination.
     */
    Page<AdminAuditLog> findByAdminIdOrderByCreatedAtDesc(UUID adminId, Pageable pageable);

    /**
     * Find audit logs by target admin ID.
     */
    Page<AdminAuditLog> findByTargetAdminIdOrderByCreatedAtDesc(UUID targetAdminId, Pageable pageable);

    /**
     * Find audit logs by action type.
     */
    Page<AdminAuditLog> findByActionOrderByCreatedAtDesc(AdminAuditLog.AdminAction action, Pageable pageable);

    /**
     * Find audit logs by admin and action.
     */
    List<AdminAuditLog> findByAdminIdAndActionOrderByCreatedAtDesc(UUID adminId, AdminAuditLog.AdminAction action);

    /**
     * Find audit logs within a date range.
     */
    @Query("SELECT a FROM AdminAuditLog a WHERE a.createdAt BETWEEN :startDate AND :endDate ORDER BY a.createdAt DESC")
    Page<AdminAuditLog> findByDateRange(@Param("startDate") LocalDateTime startDate, 
                                       @Param("endDate") LocalDateTime endDate, 
                                       Pageable pageable);

    /**
     * Find failed operations.
     */
    Page<AdminAuditLog> findBySuccessFalseOrderByCreatedAtDesc(Pageable pageable);

    /**
     * Find audit logs by session ID.
     */
    List<AdminAuditLog> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);

    /**
     * Count operations by admin in a time period.
     */
    @Query("SELECT COUNT(a) FROM AdminAuditLog a WHERE a.adminId = :adminId AND a.createdAt >= :since")
    long countOperationsByAdminSince(@Param("adminId") UUID adminId, @Param("since") LocalDateTime since);

    /**
     * Count failed operations by admin in a time period.
     */
    @Query("SELECT COUNT(a) FROM AdminAuditLog a WHERE a.adminId = :adminId AND a.success = false AND a.createdAt >= :since")
    long countFailedOperationsByAdminSince(@Param("adminId") UUID adminId, @Param("since") LocalDateTime since);

    /**
     * Find recent login attempts for an admin.
     */
    @Query("SELECT a FROM AdminAuditLog a WHERE a.adminId = :adminId AND a.action = 'LOGIN' AND a.createdAt >= :since ORDER BY a.createdAt DESC")
    List<AdminAuditLog> findRecentLoginAttempts(@Param("adminId") UUID adminId, @Param("since") LocalDateTime since);

    /**
     * Find all admin creation activities.
     */
    @Query("SELECT a FROM AdminAuditLog a WHERE a.action IN ('ADMIN_CREATE_START', 'ADMIN_CREATE_STEP1', 'ADMIN_CREATE_STEP2', 'ADMIN_CREATE_FINALIZE') ORDER BY a.createdAt DESC")
    Page<AdminAuditLog> findAdminCreationActivities(Pageable pageable);

    /**
     * Find security events (unauthorized access, invalid MFA, etc.).
     */
    @Query("SELECT a FROM AdminAuditLog a WHERE a.action IN ('UNAUTHORIZED_ACCESS', 'INVALID_MFA_CODE', 'SESSION_EXPIRED') ORDER BY a.createdAt DESC")
    Page<AdminAuditLog> findSecurityEvents(Pageable pageable);

    /**
     * Get audit summary for an admin.
     */
    @Query("SELECT a.action, COUNT(a), MAX(a.createdAt) FROM AdminAuditLog a WHERE a.adminId = :adminId GROUP BY a.action")
    List<Object[]> getAuditSummaryByAdmin(@Param("adminId") UUID adminId);

    /**
     * Find audit logs with specific details.
     */
    @Query("SELECT a FROM AdminAuditLog a WHERE JSON_EXTRACT(a.details, '$.key') = :value ORDER BY a.createdAt DESC")
    List<AdminAuditLog> findByDetailsKey(@Param("value") String value);

    /**
     * Delete old audit logs (for cleanup).
     */
    @Query("DELETE FROM AdminAuditLog a WHERE a.createdAt < :cutoffDate")
    int deleteOldAuditLogs(@Param("cutoffDate") LocalDateTime cutoffDate);
}
