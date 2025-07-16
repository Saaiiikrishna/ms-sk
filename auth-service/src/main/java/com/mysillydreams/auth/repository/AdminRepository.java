package com.mysillydreams.auth.repository;

import com.mysillydreams.auth.entity.Admin;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for admin operations.
 * Handles CRUD operations for admin entities stored in Auth Service database.
 */
@Repository
public interface AdminRepository extends JpaRepository<Admin, UUID> {

    /**
     * Find admin by username.
     */
    Optional<Admin> findByUsername(String username);

    /**
     * Find admin by email.
     */
    Optional<Admin> findByEmail(String email);

    /**
     * Find admin by Keycloak user ID.
     */
    Optional<Admin> findByKeycloakUserId(String keycloakUserId);

    /**
     * Find admin by username or email.
     */
    @Query("SELECT a FROM Admin a WHERE a.username = :usernameOrEmail OR a.email = :usernameOrEmail")
    Optional<Admin> findByUsernameOrEmail(@Param("usernameOrEmail") String usernameOrEmail);

    /**
     * Check if username exists.
     */
    boolean existsByUsername(String username);

    /**
     * Check if email exists.
     */
    boolean existsByEmail(String email);

    /**
     * Check if Keycloak user ID exists.
     */
    boolean existsByKeycloakUserId(String keycloakUserId);

    /**
     * Find all enabled admins.
     */
    List<Admin> findByEnabledTrueOrderByUsernameAsc();

    /**
     * Find all admins with pagination.
     */
    Page<Admin> findAllByOrderByUsernameAsc(Pageable pageable);

    /**
     * Find admins with MFA enabled.
     */
    List<Admin> findByMfaEnabledTrueOrderByUsernameAsc();

    /**
     * Find admins with MFA disabled.
     */
    List<Admin> findByMfaEnabledFalseOrderByUsernameAsc();

    /**
     * Find admins created by specific admin.
     */
    List<Admin> findByCreatedByOrderByCreatedAtDesc(UUID createdBy);

    /**
     * Find admins with recent login.
     */
    @Query("SELECT a FROM Admin a WHERE a.lastLogin >= :since ORDER BY a.lastLogin DESC")
    List<Admin> findWithRecentLogin(@Param("since") LocalDateTime since);

    /**
     * Find admins without recent login.
     */
    @Query("SELECT a FROM Admin a WHERE a.lastLogin IS NULL OR a.lastLogin < :since ORDER BY a.lastLogin ASC NULLS FIRST")
    List<Admin> findWithoutRecentLogin(@Param("since") LocalDateTime since);

    /**
     * Find locked admins.
     */
    @Query("SELECT a FROM Admin a WHERE a.lockedUntil IS NOT NULL AND a.lockedUntil > :now ORDER BY a.lockedUntil DESC")
    List<Admin> findLockedAdmins(@Param("now") LocalDateTime now);

    /**
     * Find admins with failed login attempts.
     */
    @Query("SELECT a FROM Admin a WHERE a.failedLoginAttempts > :threshold ORDER BY a.failedLoginAttempts DESC")
    List<Admin> findAdminsWithFailedAttempts(@Param("threshold") Integer threshold);

    /**
     * Count total admins.
     */
    long count();

    /**
     * Count enabled admins.
     */
    long countByEnabledTrue();

    /**
     * Count admins with MFA enabled.
     */
    long countByMfaEnabledTrue();

    /**
     * Count locked admins.
     */
    @Query("SELECT COUNT(a) FROM Admin a WHERE a.lockedUntil IS NOT NULL AND a.lockedUntil > :now")
    long countLockedAdmins(@Param("now") LocalDateTime now);
}
