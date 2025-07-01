package com.mysillydreams.auth.service;

import com.mysillydreams.auth.domain.PasswordRotationLog;
import com.mysillydreams.auth.event.AuthEvents;
import com.mysillydreams.auth.event.KafkaPublisher;
import com.mysillydreams.auth.repository.PasswordRotationLogRepository;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class PasswordRotationService {

    private static final Logger logger = LoggerFactory.getLogger(PasswordRotationService.class);

    private final PasswordRotationLogRepository passwordRotationLogRepository;
    private final KafkaPublisher kafkaPublisher;
    private final Keycloak keycloakAdminClient;

    @Value("${keycloak.realm}")
    private String keycloakRealm;

    @Autowired
    public PasswordRotationService(PasswordRotationLogRepository passwordRotationLogRepository,
                                   KafkaPublisher kafkaPublisher,
                                   Keycloak keycloakAdminClient) {
        this.passwordRotationLogRepository = passwordRotationLogRepository;
        this.kafkaPublisher = kafkaPublisher;
        this.keycloakAdminClient = keycloakAdminClient;
    }

    /**
     * Rotates the password for a given user ID by triggering a required action in Keycloak.
     * Logs the rotation event and publishes it to Kafka.
     *
     * @param userId The UUID of the user whose password needs to be rotated.
     * @throws NotFoundException if the user is not found in Keycloak.
     * @throws WebApplicationException for other Keycloak related errors.
     * @throws RuntimeException for other unexpected errors.
     */
    @Transactional // Ensures that DB save and Kafka publish are part of the same transaction if Kafka TX are configured,
                   // otherwise, it's primarily for the DB operation.
    public void rotatePassword(UUID userId) {
        logger.info("Attempting to initiate password rotation for user ID: {}", userId);

        try {
            RealmResource realmResource = keycloakAdminClient.realm(keycloakRealm);
            UserResource userResource = realmResource.users().get(userId.toString());

            if (userResource == null) {
                logger.warn("User with ID {} not found in Keycloak realm {}. Cannot rotate password.", userId, keycloakRealm);
                throw new NotFoundException("User not found: " + userId);
            }

            // Trigger Keycloak's "UPDATE_PASSWORD" required action for the user.
            // This will force the user to change their password upon next login.
            UserRepresentation userRepresentation = userResource.toRepresentation(); // Get current representation
            if (userRepresentation.getRequiredActions() == null) {
                userRepresentation.setRequiredActions(Collections.singletonList("UPDATE_PASSWORD"));
            } else if (!userRepresentation.getRequiredActions().contains("UPDATE_PASSWORD")) {
                userRepresentation.getRequiredActions().add("UPDATE_PASSWORD");
            }

            // For this to work, the 'auth-service-client' service account needs the 'manage-users'
            // role (typically a client role from the 'realm-management' client in Keycloak).
            userResource.update(userRepresentation);
            logger.info("Successfully triggered 'UPDATE_PASSWORD' required action for user ID: {}", userId);

            // 2. Save new entry in password_rotation_log
            Instant rotatedAt = Instant.now();
            PasswordRotationLog logEntry = new PasswordRotationLog(userId, rotatedAt);
            passwordRotationLogRepository.save(logEntry);
            logger.info("Password rotation log entry saved for user ID: {}", userId);

            // 3. Publish event
            Map<String, Object> eventPayload = new HashMap<>();
            eventPayload.put("userId", userId.toString());
            eventPayload.put("rotatedAt", rotatedAt.toString());
            // Using the main topic defined in AuthEvents and the specific event key
            kafkaPublisher.publishEvent(AuthEvents.AUTH_EVENTS_TOPIC, AuthEvents.PASSWORD_ROTATED, eventPayload);
            logger.info("Password rotated event published for user ID: {}", userId);

        } catch (NotFoundException nfe) {
            throw nfe; // Re-throw to be handled by controller advice or error handler
        } catch (WebApplicationException wae) {
            // Log Keycloak specific errors
            String errorDetails = "Unknown Keycloak error";
            if (wae.getResponse() != null) {
                errorDetails = wae.getResponse().readEntity(String.class);
            }
            logger.error("Keycloak Admin API error during password rotation for user {}: {} - {}", userId, wae.getMessage(), errorDetails, wae);
            throw new RuntimeException("Error during password rotation with Keycloak: " + wae.getMessage(), wae);
        } catch (Exception e) {
            logger.error("Unexpected error during password rotation for user ID: {}", userId, e);
            // Depending on policy, you might want to re-throw a custom business exception
            throw new RuntimeException("Unexpected error during password rotation: " + e.getMessage(), e);
        }
    }
}
