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
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Service for bootstrapping the first admin user.
 * This should be used only once and then disabled.
 */
@Service
public class AdminBootstrapService {

    private static final Logger logger = LoggerFactory.getLogger(AdminBootstrapService.class);

    private final Keycloak keycloak;
    private final AdminMfaService adminMfaService;
    private final AdminMfaConfigRepository adminMfaConfigRepository;

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${app.bootstrap.enabled:true}")
    private boolean bootstrapEnabled;

    @Autowired
    public AdminBootstrapService(Keycloak keycloak, 
                                AdminMfaService adminMfaService,
                                AdminMfaConfigRepository adminMfaConfigRepository) {
        this.keycloak = keycloak;
        this.adminMfaService = adminMfaService;
        this.adminMfaConfigRepository = adminMfaConfigRepository;
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
            logger.info("Bootstrapping first admin user: {}", request.username);

            // Create user in Keycloak
            UUID userId = createUserInKeycloak(request);
            
            // Assign admin role
            assignAdminRole(userId);
            
            // Generate MFA setup
            AdminMfaService.MfaSetupResponse mfaSetup = adminMfaService.generateMfaSetup(userId, request.username);
            
            logger.info("First admin user bootstrapped successfully: {}", request.username);
            
            return new BootstrapResponse(
                userId.toString(),
                request.username,
                mfaSetup.getRawSecret(),
                mfaSetup.getQrCodeDataUri(),
                "First admin user created successfully. Please scan the QR code and verify MFA to complete setup."
            );
            
        } catch (Exception e) {
            logger.error("Failed to bootstrap first admin user: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to bootstrap first admin user: " + e.getMessage(), e);
        }
    }

    private boolean hasExistingAdmins() {
        try {
            RealmResource realmResource = keycloak.realm(realm);
            RoleRepresentation adminRole = realmResource.roles().get("admin").toRepresentation();
            return !realmResource.roles().get("admin").getUserMembers().isEmpty();
        } catch (Exception e) {
            logger.warn("Could not check for existing admins: {}", e.getMessage());
            return false;
        }
    }

    private UUID createUserInKeycloak(BootstrapRequest request) {
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

    /**
     * Verify MFA for the bootstrapped admin and complete the setup.
     */
    @Transactional
    public boolean verifyBootstrapMfa(String userId, String totpCode) {
        try {
            UUID adminUserId = UUID.fromString(userId);
            boolean verified = adminMfaService.verifyAndEnableMfa(adminUserId, totpCode);
            
            if (verified) {
                logger.info("Bootstrap MFA verified successfully for user: {}", userId);
                // TODO: Call user service to create admin profile
                return true;
            } else {
                logger.warn("Bootstrap MFA verification failed for user: {}", userId);
                return false;
            }
        } catch (Exception e) {
            logger.error("Error verifying bootstrap MFA for user {}: {}", userId, e.getMessage(), e);
            return false;
        }
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
