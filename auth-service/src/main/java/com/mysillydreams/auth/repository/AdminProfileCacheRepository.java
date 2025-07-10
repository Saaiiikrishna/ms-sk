package com.mysillydreams.auth.repository;

import com.mysillydreams.auth.entity.AdminProfileCache;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
 * Repository for admin profile cache operations.
 */
@Repository
public interface AdminProfileCacheRepository extends JpaRepository<AdminProfileCache, UUID> {

    /**
     * Find admin profile by username.
     */
    Optional<AdminProfileCache> findByUsername(String username);

    /**
     * Find admin profile by email.
     */
    Optional<AdminProfileCache> findByEmail(String email);

    /**
     * Find all enabled admin profiles.
     */
    List<AdminProfileCache> findByEnabledTrueOrderByUsernameAsc();

    /**
     * Find all admin profiles with pagination.
     */
    Page<AdminProfileCache> findAllByOrderByUsernameAsc(Pageable pageable);

    /**
     * Find admin profiles with MFA enabled.
     */
    List<AdminProfileCache> findByMfaEnabledTrueOrderByUsernameAsc();

    /**
     * Find admin profiles with MFA disabled.
     */
    List<AdminProfileCache> findByMfaEnabledFalseOrderByUsernameAsc();

    /**
     * Find admin profiles by enabled status.
     */
    Page<AdminProfileCache> findByEnabledOrderByUsernameAsc(Boolean enabled, Pageable pageable);

    /**
     * Search admin profiles by username or email.
     */
    @Query("SELECT a FROM AdminProfileCache a WHERE LOWER(a.username) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(a.email) LIKE LOWER(CONCAT('%', :search, '%')) ORDER BY a.username ASC")
    Page<AdminProfileCache> searchByUsernameOrEmail(@Param("search") String search, Pageable pageable);

    /**
     * Search admin profiles by name.
     */
    @Query("SELECT a FROM AdminProfileCache a WHERE LOWER(a.firstName) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(a.lastName) LIKE LOWER(CONCAT('%', :search, '%')) ORDER BY a.firstName ASC, a.lastName ASC")
    Page<AdminProfileCache> searchByName(@Param("search") String search, Pageable pageable);

    /**
     * Find admin profiles with recent login.
     */
    @Query("SELECT a FROM AdminProfileCache a WHERE a.lastLogin >= :since ORDER BY a.lastLogin DESC")
    List<AdminProfileCache> findWithRecentLogin(@Param("since") LocalDateTime since);

    /**
     * Find admin profiles without recent login.
     */
    @Query("SELECT a FROM AdminProfileCache a WHERE a.lastLogin IS NULL OR a.lastLogin < :since ORDER BY a.lastLogin ASC NULLS FIRST")
    List<AdminProfileCache> findWithoutRecentLogin(@Param("since") LocalDateTime since);

    /**
     * Count total admin profiles.
     */
    long count();

    /**
     * Count enabled admin profiles.
     */
    long countByEnabledTrue();

    /**
     * Count admin profiles with MFA enabled.
     */
    long countByMfaEnabledTrue();

    /**
     * Update MFA status for an admin.
     */
    @Modifying
    @Transactional
    @Query("UPDATE AdminProfileCache a SET a.mfaEnabled = :mfaEnabled WHERE a.userId = :userId")
    int updateMfaStatus(@Param("userId") UUID userId, @Param("mfaEnabled") Boolean mfaEnabled);

    /**
     * Update last login timestamp.
     */
    @Modifying
    @Transactional
    @Query("UPDATE AdminProfileCache a SET a.lastLogin = :lastLogin WHERE a.userId = :userId")
    int updateLastLogin(@Param("userId") UUID userId, @Param("lastLogin") LocalDateTime lastLogin);

    /**
     * Update enabled status.
     */
    @Modifying
    @Transactional
    @Query("UPDATE AdminProfileCache a SET a.enabled = :enabled WHERE a.userId = :userId")
    int updateEnabledStatus(@Param("userId") UUID userId, @Param("enabled") Boolean enabled);

    /**
     * Update profile information.
     */
    @Modifying
    @Transactional
    @Query("UPDATE AdminProfileCache a SET a.username = :username, a.email = :email, a.firstName = :firstName, a.lastName = :lastName, a.enabled = :enabled WHERE a.userId = :userId")
    int updateProfile(@Param("userId") UUID userId, 
                     @Param("username") String username, 
                     @Param("email") String email, 
                     @Param("firstName") String firstName, 
                     @Param("lastName") String lastName, 
                     @Param("enabled") Boolean enabled);

    /**
     * Check if username exists (excluding specific user ID).
     */
    @Query("SELECT COUNT(a) > 0 FROM AdminProfileCache a WHERE a.username = :username AND a.userId != :excludeUserId")
    boolean existsByUsernameAndUserIdNot(@Param("username") String username, @Param("excludeUserId") UUID excludeUserId);

    /**
     * Check if email exists (excluding specific user ID).
     */
    @Query("SELECT COUNT(a) > 0 FROM AdminProfileCache a WHERE a.email = :email AND a.userId != :excludeUserId")
    boolean existsByEmailAndUserIdNot(@Param("email") String email, @Param("excludeUserId") UUID excludeUserId);

    /**
     * Get admin statistics.
     */
    @Query("SELECT COUNT(a), COUNT(CASE WHEN a.enabled = true THEN 1 END), COUNT(CASE WHEN a.mfaEnabled = true THEN 1 END), COUNT(CASE WHEN a.lastLogin >= :recentLoginThreshold THEN 1 END) FROM AdminProfileCache a")
    Object[] getAdminStatistics(@Param("recentLoginThreshold") LocalDateTime recentLoginThreshold);
}
