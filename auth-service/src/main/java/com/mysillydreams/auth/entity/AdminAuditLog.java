package com.mysillydreams.auth.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Entity for tracking all admin operations and security events.
 * Provides comprehensive audit trail for compliance and security monitoring.
 */
@Entity
@Table(name = "admin_audit_log")
@Getter
@Setter
@NoArgsConstructor
public class AdminAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotNull
    @Column(name = "admin_id", nullable = false)
    private UUID adminId;

    @Column(name = "target_admin_id")
    private UUID targetAdminId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 50)
    private AdminAction action;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details", columnDefinition = "jsonb")
    private Map<String, Object> details;

    @Column(name = "ip_address")
    private InetAddress ipAddress;

    @Column(name = "user_agent")
    private String userAgent;

    @NotNull
    @Column(name = "success", nullable = false)
    private Boolean success = true;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "session_id")
    private UUID sessionId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public AdminAuditLog(UUID adminId, AdminAction action, Boolean success) {
        this.adminId = adminId;
        this.action = action;
        this.success = success;
    }

    public AdminAuditLog(UUID adminId, UUID targetAdminId, AdminAction action, Boolean success) {
        this.adminId = adminId;
        this.targetAdminId = targetAdminId;
        this.action = action;
        this.success = success;
    }

    /**
     * Enum for admin actions that can be audited.
     */
    public enum AdminAction {
        // Authentication actions
        LOGIN,
        LOGOUT,
        MFA_VERIFY,
        MFA_SETUP,
        
        // Admin management actions
        ADMIN_CREATE_START,
        ADMIN_CREATE_STEP1,
        ADMIN_CREATE_STEP2,
        ADMIN_CREATE_FINALIZE,
        ADMIN_UPDATE,
        ADMIN_DELETE,
        
        // System actions
        BOOTSTRAP_FIRST_ADMIN,
        SESSION_CLEANUP,
        
        // Security events
        UNAUTHORIZED_ACCESS,
        INVALID_MFA_CODE,
        SESSION_EXPIRED,
        
        // Profile actions
        PROFILE_VIEW,
        PROFILE_UPDATE
    }

    /**
     * Builder pattern for creating audit log entries.
     */
    public static class Builder {
        private final AdminAuditLog auditLog;

        public Builder(UUID adminId, AdminAction action) {
            this.auditLog = new AdminAuditLog();
            this.auditLog.adminId = adminId;
            this.auditLog.action = action;
            this.auditLog.success = true;
        }

        public Builder targetAdmin(UUID targetAdminId) {
            this.auditLog.targetAdminId = targetAdminId;
            return this;
        }

        public Builder details(Map<String, Object> details) {
            this.auditLog.details = details;
            return this;
        }

        public Builder ipAddress(InetAddress ipAddress) {
            this.auditLog.ipAddress = ipAddress;
            return this;
        }

        public Builder userAgent(String userAgent) {
            this.auditLog.userAgent = userAgent;
            return this;
        }

        public Builder success(Boolean success) {
            this.auditLog.success = success;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.auditLog.errorMessage = errorMessage;
            this.auditLog.success = false;
            return this;
        }

        public Builder sessionId(UUID sessionId) {
            this.auditLog.sessionId = sessionId;
            return this;
        }

        public AdminAuditLog build() {
            return this.auditLog;
        }
    }
}
