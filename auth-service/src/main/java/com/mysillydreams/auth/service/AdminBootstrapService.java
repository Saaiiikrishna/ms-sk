package com.mysillydreams.auth.service;

import com.mysillydreams.auth.entity.Admin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Service for bootstrapping the first admin user using hybrid approach.
 * Stores admin data in Auth Service database and creates corresponding Keycloak user.
 */
@Service
public class AdminBootstrapService {

    private static final Logger logger = LoggerFactory.getLogger(AdminBootstrapService.class);

    private final HybridAdminService hybridAdminService;

    @Value("${app.bootstrap.enabled:true}")
    private boolean bootstrapEnabled;

    @Autowired
    public AdminBootstrapService(HybridAdminService hybridAdminService) {
        this.hybridAdminService = hybridAdminService;
    }

    /**
     * Bootstrap the first admin user with MFA setup.
     * This method should only be called once.
     */
    @Transactional
    public BootstrapResponse bootstrapFirstAdmin(BootstrapRequest request) {
        if (!bootstrapEnabled) {
            throw new IllegalStateException("Bootstrap is disabled. First admin already exists.");
        }

        // Check if any admin users already exist
        if (hasExistingAdmins()) {
            throw new IllegalStateException("Admin users already exist. Bootstrap not allowed.");
        }

        try {
            logger.info("Bootstrapping first admin user using hybrid approach: {}", request.username);

            // Create admin using hybrid approach (internal DB + Keycloak)
            Admin admin = hybridAdminService.createAdmin(
                request.username,
                request.email,
                request.firstName,
                request.lastName,
                request.password,
                null // No creator for first admin
            );

            logger.info("First admin user bootstrapped successfully: {} with ID: {}",
                       request.username, admin.getId());

            return new BootstrapResponse(
                admin.getId().toString(),
                request.username,
                null, // MFA will be set up through Keycloak later
                null, // QR code will be generated through Keycloak later
                "First admin user created successfully using hybrid approach. Admin data stored in Auth Service database, authentication handled by Keycloak."
            );

        } catch (Exception e) {
            logger.error("Failed to bootstrap first admin user: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to bootstrap first admin user: " + e.getMessage(), e);
        }
    }

    private boolean hasExistingAdmins() {
        try {
            // Check if any admins exist in internal database
            return hybridAdminService.hasExistingAdmins();
        } catch (Exception e) {
            logger.warn("Could not check for existing admins: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Get admin by ID for verification purposes.
     */
    public Admin getAdminById(UUID adminId) {
        return hybridAdminService.getAdminById(adminId)
            .orElseThrow(() -> new IllegalArgumentException("Admin not found with ID: " + adminId));
    }

    /**
     * Get admin by username for authentication purposes.
     */
    public Admin getAdminByUsername(String username) {
        return hybridAdminService.getAdminByUsername(username)
            .orElseThrow(() -> new IllegalArgumentException("Admin not found with username: " + username));
    }



    // DTOs
    public static class BootstrapRequest {
        public String username;
        public String email;
        public String firstName;
        public String lastName;
        public String password;
    }

    public static class BootstrapResponse {
        public String userId;
        public String username;
        public String totpSecret;
        public String qrCodeDataUri;
        public String message;

        public BootstrapResponse(String userId, String username, String totpSecret, String qrCodeDataUri, String message) {
            this.userId = userId;
            this.username = username;
            this.totpSecret = totpSecret;
            this.qrCodeDataUri = qrCodeDataUri;
            this.message = message;
        }
    }
}
