package com.mysillydreams.auth.service;

import com.mysillydreams.auth.entity.Admin;
import com.mysillydreams.auth.repository.AdminRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Pure Internal Admin Service that manages admin users entirely within Auth Service.
 *
 * Architecture:
 * - Admin data stored ONLY in Auth Service database
 * - Admin creation only through Admin Portal
 * - NO admin data stored in Keycloak
 * - NO admin users can access Keycloak UI
 * - ADMIN_ROLE managed entirely internally
 * - Complete separation from regular user management
 * - Pure internal authentication for maximum security
 */
@Service
public class HybridAdminService {

    private static final Logger logger = LoggerFactory.getLogger(HybridAdminService.class);

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public HybridAdminService(AdminRepository adminRepository,
                             PasswordEncoder passwordEncoder) {
        this.adminRepository = adminRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Creates an admin user using pure internal approach:
     * 1. Store admin data ONLY in Auth Service database
     * 2. NO user creation in Keycloak
     * 3. Complete internal management for maximum security
     */
    @Transactional
    public Admin createAdmin(String username, String email, String firstName, String lastName,
                           String password, UUID createdBy) {
        logger.info("Creating admin user: {} using pure internal approach (NO Keycloak)", username);

        // Validate that admin doesn't already exist
        if (adminRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Admin with username already exists: " + username);
        }

        if (adminRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Admin with email already exists: " + email);
        }

        try {
            // Store admin data ONLY in Auth Service database - NO Keycloak integration
            String passwordHash = passwordEncoder.encode(password);
            Admin admin = new Admin(username, email, firstName, lastName, passwordHash);
            // Note: keycloak_user_id remains null - admin exists ONLY in Auth Service
            admin.setCreatedBy(createdBy);

            admin = adminRepository.save(admin);

            logger.info("Admin created successfully: {} with internal ID: {} (NO Keycloak user created)",
                       username, admin.getId());

            return admin;

        } catch (Exception e) {
            logger.error("Failed to create admin user: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create admin user: " + e.getMessage(), e);
        }
    }

    /**
     * Authenticates admin using pure internal approach:
     * 1. Find admin in internal database
     * 2. Validate password using internal password encoder
     * 3. Return internal admin data with ADMIN_ROLE
     * NO Keycloak integration - complete internal authentication
     */
    public Admin authenticateAdmin(String username, String password) {
        logger.info("Authenticating admin: {} using pure internal approach (NO Keycloak)", username);

        // Step 1: Find admin in internal database
        Optional<Admin> adminOpt = adminRepository.findByUsername(username);
        if (adminOpt.isEmpty()) {
            throw new IllegalArgumentException("Admin not found: " + username);
        }

        Admin admin = adminOpt.get();

        // Check if admin is enabled and not locked
        if (!admin.getEnabled()) {
            throw new IllegalArgumentException("Admin account is disabled: " + username);
        }

        if (admin.isLocked()) {
            throw new IllegalArgumentException("Admin account is locked: " + username);
        }

        // Step 2: Validate password using internal password encoder (NO Keycloak)
        try {
            if (!passwordEncoder.matches(password, admin.getPasswordHash())) {
                // Handle failed authentication
                admin.incrementFailedLoginAttempts();
                adminRepository.save(admin);
                throw new IllegalArgumentException("Invalid password for admin: " + username);
            }

            // Step 3: Reset failed attempts and update login information
            admin.resetFailedLoginAttempts();
            admin.updateLastLogin();
            adminRepository.save(admin);

            logger.info("Admin authenticated successfully using internal authentication: {}", username);
            return admin;

        } catch (Exception e) {
            // Handle failed authentication
            admin.incrementFailedLoginAttempts();
            if (admin.getFailedLoginAttempts() >= 5) {
                admin.lockAccount(30); // Lock for 30 minutes
                logger.warn("Admin account locked due to failed attempts: {}", username);
            }
            adminRepository.save(admin);

            logger.error("Internal authentication failed for admin: {}", username);
            throw new IllegalArgumentException("Authentication failed for admin: " + username);
        }
    }

    /**
     * Gets admin by username from internal database.
     */
    public Optional<Admin> getAdminByUsername(String username) {
        return adminRepository.findByUsername(username);
    }

    /**
     * Gets admin by ID from internal database.
     */
    public Optional<Admin> getAdminById(UUID adminId) {
        return adminRepository.findById(adminId);
    }

    /**
     * Checks if any admins exist in the system.
     */
    public boolean hasExistingAdmins() {
        return adminRepository.count() > 0;
    }

}
