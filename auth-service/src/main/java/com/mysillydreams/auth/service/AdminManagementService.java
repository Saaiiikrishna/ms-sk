package com.mysillydreams.auth.service;

import com.mysillydreams.auth.entity.AdminMfaConfig;
import com.mysillydreams.auth.repository.AdminMfaConfigRepository;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.http.ResponseEntity;
import jakarta.ws.rs.core.Response;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing admin users with MFA verification.
 */
@Service
public class AdminManagementService {

    private static final Logger logger = LoggerFactory.getLogger(AdminManagementService.class);

    private final Keycloak keycloak;
    private final AdminMfaService adminMfaService;
    private final AdminMfaConfigRepository adminMfaConfigRepository;

    @Value("${keycloak.realm}")
    private String realm;

    // Temporary session storage for multi-step admin creation
    private final Map<String, AdminCreationSession> creationSessions = new ConcurrentHashMap<>();

    @Autowired
    public AdminManagementService(Keycloak keycloak, 
                                 AdminMfaService adminMfaService,
                                 AdminMfaConfigRepository adminMfaConfigRepository) {
        this.keycloak = keycloak;
        this.adminMfaService = adminMfaService;
        this.adminMfaConfigRepository = adminMfaConfigRepository;
    }

    /**
     * Step 1: Initialize admin creation with general details
     */
    @Transactional
    public AdminCreationStepOneResponse initializeAdminCreation(AdminCreationStepOneRequest request, UUID currentAdminId) {
        // Verify current admin's MFA
        if (!adminMfaService.verifyOtp(currentAdminId, request.currentAdminMfaCode)) {
            throw new SecurityException("Invalid MFA code for current admin");
        }

        // Create session for multi-step process
        String sessionId = UUID.randomUUID().toString();
        AdminCreationSession session = new AdminCreationSession();
        session.sessionId = sessionId;
        session.currentAdminId = currentAdminId;
        session.newAdminDetails = request;
        session.createdAt = LocalDateTime.now();
        session.step = 1;

        creationSessions.put(sessionId, session);

        logger.info("Admin creation session initialized: {} by admin: {}", sessionId, currentAdminId);

        return new AdminCreationStepOneResponse(sessionId, "Admin details validated. Proceed to MFA setup.");
    }

    /**
     * Step 2: Setup MFA and complete admin creation
     */
    @Transactional
    public AdminCreationStepTwoResponse completeAdminCreation(AdminCreationStepTwoRequest request) {
        // Validate session
        AdminCreationSession session = creationSessions.get(request.sessionId);
        if (session == null || session.step != 1) {
            throw new IllegalStateException("Invalid or expired session");
        }

        try {
            // Create user in Keycloak
            UUID newAdminId = createUserInKeycloak(session.newAdminDetails);
            
            // Generate MFA setup for new admin
            AdminMfaService.MfaSetupResponse mfaSetup = adminMfaService.generateMfaSetup(newAdminId, session.newAdminDetails.username);
            
            // Update session
            session.newAdminId = newAdminId;
            session.mfaSecret = mfaSetup.getRawSecret();
            session.step = 2;

            logger.info("MFA setup generated for new admin: {} in session: {}", newAdminId, request.sessionId);

            return new AdminCreationStepTwoResponse(
                request.sessionId,
                mfaSetup.getQrCodeDataUri(),
                "Scan the QR code with your authenticator app and enter the 6-digit code to complete admin creation."
            );

        } catch (Exception e) {
            creationSessions.remove(request.sessionId);
            logger.error("Failed to setup MFA for admin creation session {}: {}", request.sessionId, e.getMessage(), e);
            throw new RuntimeException("Failed to setup MFA: " + e.getMessage(), e);
        }
    }

    /**
     * Step 3: Verify new admin's MFA and finalize creation
     */
    @Transactional
    public AdminCreationCompleteResponse finalizeAdminCreation(AdminCreationFinalizeRequest request) {
        // Validate session
        AdminCreationSession session = creationSessions.get(request.sessionId);
        if (session == null || session.step != 2) {
            throw new IllegalStateException("Invalid or expired session");
        }

        try {
            // Verify new admin's MFA code
            boolean mfaVerified = adminMfaService.verifyAndEnableMfa(session.newAdminId, request.newAdminMfaCode);
            if (!mfaVerified) {
                throw new SecurityException("Invalid MFA code for new admin");
            }

            // Assign admin role
            assignAdminRole(session.newAdminId);

            // TODO: Call user service to create admin profile
            // createAdminProfile(session.newAdminId, session.newAdminDetails);

            // Clean up session
            creationSessions.remove(request.sessionId);

            logger.info("Admin creation completed successfully: {} by admin: {}", session.newAdminId, session.currentAdminId);

            return new AdminCreationCompleteResponse(
                session.newAdminId.toString(),
                session.newAdminDetails.username,
                "Admin user created successfully with MFA enabled."
            );

        } catch (Exception e) {
            logger.error("Failed to finalize admin creation for session {}: {}", request.sessionId, e.getMessage(), e);
            throw new RuntimeException("Failed to finalize admin creation: " + e.getMessage(), e);
        }
    }

    /**
     * Delete admin user with MFA verification
     */
    @Transactional
    public boolean deleteAdmin(UUID adminToDeleteId, UUID currentAdminId, String currentAdminMfaCode) {
        // Verify current admin's MFA
        if (!adminMfaService.verifyOtp(currentAdminId, currentAdminMfaCode)) {
            throw new SecurityException("Invalid MFA code for current admin");
        }

        // Prevent self-deletion
        if (adminToDeleteId.equals(currentAdminId)) {
            throw new IllegalArgumentException("Cannot delete your own admin account");
        }

        try {
            // Remove admin role
            removeAdminRole(adminToDeleteId);
            
            // Delete user from Keycloak
            keycloak.realm(realm).users().delete(adminToDeleteId.toString());
            
            // Delete MFA config
            adminMfaConfigRepository.deleteByUserId(adminToDeleteId);

            // TODO: Call user service to delete admin profile
            
            logger.info("Admin user deleted: {} by admin: {}", adminToDeleteId, currentAdminId);
            return true;

        } catch (Exception e) {
            logger.error("Failed to delete admin {}: {}", adminToDeleteId, e.getMessage(), e);
            throw new RuntimeException("Failed to delete admin user: " + e.getMessage(), e);
        }
    }

    private UUID createUserInKeycloak(AdminCreationStepOneRequest request) {
        RealmResource realmResource = keycloak.realm(realm);
        UsersResource usersResource = realmResource.users();

        // Create user representation
        UserRepresentation user = new UserRepresentation();
        user.setUsername(request.username);
        user.setEmail(request.email);
        user.setFirstName(request.firstName);
        user.setLastName(request.lastName);
        user.setEnabled(true);
        user.setEmailVerified(true);

        // Set password
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(request.password);
        credential.setTemporary(false);
        user.setCredentials(Collections.singletonList(credential));

        // Create user
        Response response = usersResource.create(user);
        if (response.getStatus() != 201) {
            throw new RuntimeException("Failed to create user in Keycloak. Status: " + response.getStatus());
        }

        // Extract user ID from location header
        String location = response.getLocation().getPath();
        String userId = location.substring(location.lastIndexOf('/') + 1);
        
        response.close();
        return UUID.fromString(userId);
    }

    private void assignAdminRole(UUID userId) {
        try {
            RealmResource realmResource = keycloak.realm(realm);
            RoleRepresentation adminRole = realmResource.roles().get("admin").toRepresentation();
            realmResource.users().get(userId.toString()).roles().realmLevel().add(Collections.singletonList(adminRole));
            logger.info("Admin role assigned to user: {}", userId);
        } catch (Exception e) {
            logger.error("Failed to assign admin role to user {}: {}", userId, e.getMessage(), e);
            throw new RuntimeException("Failed to assign admin role", e);
        }
    }

    private void removeAdminRole(UUID userId) {
        try {
            RealmResource realmResource = keycloak.realm(realm);
            RoleRepresentation adminRole = realmResource.roles().get("admin").toRepresentation();
            realmResource.users().get(userId.toString()).roles().realmLevel().remove(Collections.singletonList(adminRole));
            logger.info("Admin role removed from user: {}", userId);
        } catch (Exception e) {
            logger.error("Failed to remove admin role from user {}: {}", userId, e.getMessage(), e);
            throw new RuntimeException("Failed to remove admin role", e);
        }
    }

    // Session and DTO classes
    private static class AdminCreationSession {
        String sessionId;
        UUID currentAdminId;
        UUID newAdminId;
        AdminCreationStepOneRequest newAdminDetails;
        String mfaSecret;
        LocalDateTime createdAt;
        int step;
    }

    public static class AdminCreationStepOneRequest {
        public String username;
        public String email;
        public String firstName;
        public String lastName;
        public String password;
        public String currentAdminMfaCode; // Current admin's MFA code for authorization
    }

    public static class AdminCreationStepOneResponse {
        public String sessionId;
        public String message;

        public AdminCreationStepOneResponse(String sessionId, String message) {
            this.sessionId = sessionId;
            this.message = message;
        }
    }

    public static class AdminCreationStepTwoRequest {
        public String sessionId;
    }

    public static class AdminCreationStepTwoResponse {
        public String sessionId;
        public String qrCodeDataUri;
        public String message;

        public AdminCreationStepTwoResponse(String sessionId, String qrCodeDataUri, String message) {
            this.sessionId = sessionId;
            this.qrCodeDataUri = qrCodeDataUri;
            this.message = message;
        }
    }

    public static class AdminCreationFinalizeRequest {
        public String sessionId;
        public String newAdminMfaCode; // New admin's MFA code from authenticator app
    }

    public static class AdminCreationCompleteResponse {
        public String adminId;
        public String username;
        public String message;

        public AdminCreationCompleteResponse(String adminId, String username, String message) {
            this.adminId = adminId;
            this.username = username;
            this.message = message;
        }
    }
}
