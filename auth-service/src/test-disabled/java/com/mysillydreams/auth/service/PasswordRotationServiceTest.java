package com.mysillydreams.auth.service;

import com.mysillydreams.auth.domain.PasswordRotationLog;
import com.mysillydreams.auth.event.AuthEvents;
import com.mysillydreams.auth.event.KafkaPublisher;
import com.mysillydreams.auth.repository.PasswordRotationLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PasswordRotationServiceTest {

    @Mock
    private PasswordRotationLogRepository passwordRotationLogRepository;

    @Mock
    private KafkaPublisher kafkaPublisher;

    @Mock
    private Keycloak keycloakAdminClient;

    // Mocks for Keycloak resource hierarchy
    @Mock
    private RealmResource realmResource;
    @Mock
    private UsersResource usersResource;
    @Mock
    private UserResource userResource;

    @InjectMocks
    private PasswordRotationService passwordRotationService;

    private final String testRealm = "TestRealm";
    private UUID testUserId;
    private UserRepresentation mockUserRepresentation;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(passwordRotationService, "keycloakRealm", testRealm);
        testUserId = UUID.randomUUID();

        // Setup Keycloak mock chain
        when(keycloakAdminClient.realm(testRealm)).thenReturn(realmResource);
        when(realmResource.users()).thenReturn(usersResource);
        when(usersResource.get(testUserId.toString())).thenReturn(userResource);

        mockUserRepresentation = new UserRepresentation();
        mockUserRepresentation.setId(testUserId.toString());
        mockUserRepresentation.setUsername("testuser");
        // Initialize requiredActions as an empty list to avoid NullPointerException
        mockUserRepresentation.setRequiredActions(new ArrayList<>());


        // Default behavior for userResource.toRepresentation()
        lenient().when(userResource.toRepresentation()).thenReturn(mockUserRepresentation);
    }

    @Test
    void rotatePassword_success_noExistingRequiredActions() {
        // Arrange
        mockUserRepresentation.setRequiredActions(new ArrayList<>()); // Ensure it's empty or null for this case
        when(userResource.toRepresentation()).thenReturn(mockUserRepresentation);


        // Act
        passwordRotationService.rotatePassword(testUserId);

        // Assert
        // 1. Keycloak user update
        ArgumentCaptor<UserRepresentation> userRepCaptor = ArgumentCaptor.forClass(UserRepresentation.class);
        verify(userResource).update(userRepCaptor.capture());
        assertTrue(userRepCaptor.getValue().getRequiredActions().contains("UPDATE_PASSWORD"));

        // 2. DB log save
        ArgumentCaptor<PasswordRotationLog> logCaptor = ArgumentCaptor.forClass(PasswordRotationLog.class);
        verify(passwordRotationLogRepository).save(logCaptor.capture());
        assertEquals(testUserId, logCaptor.getValue().getUserId());
        assertNotNull(logCaptor.getValue().getRotatedAt());

        // 3. Kafka event publish
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(kafkaPublisher).publishEvent(eq(AuthEvents.AUTH_EVENTS_TOPIC), eq(AuthEvents.PASSWORD_ROTATED), payloadCaptor.capture());
        assertEquals(testUserId.toString(), payloadCaptor.getValue().get("userId"));
        assertNotNull(payloadCaptor.getValue().get("rotatedAt"));
    }

    @Test
    void rotatePassword_success_withExistingRequiredActions() {
        // Arrange
        List<String> existingActions = new ArrayList<>(List.of("VERIFY_EMAIL"));
        mockUserRepresentation.setRequiredActions(existingActions);
        when(userResource.toRepresentation()).thenReturn(mockUserRepresentation);

        // Act
        passwordRotationService.rotatePassword(testUserId);

        // Assert
        ArgumentCaptor<UserRepresentation> userRepCaptor = ArgumentCaptor.forClass(UserRepresentation.class);
        verify(userResource).update(userRepCaptor.capture());
        List<String> updatedActions = userRepCaptor.getValue().getRequiredActions();
        assertTrue(updatedActions.contains("UPDATE_PASSWORD"));
        assertTrue(updatedActions.contains("VERIFY_EMAIL")); // Ensure existing actions are preserved
        assertEquals(2, updatedActions.size());

        verify(passwordRotationLogRepository).save(any(PasswordRotationLog.class));
        verify(kafkaPublisher).publishEvent(eq(AuthEvents.AUTH_EVENTS_TOPIC), eq(AuthEvents.PASSWORD_ROTATED), anyMap());
    }

    @Test
    void rotatePassword_success_updatePasswordActionAlreadyExists() {
        // Arrange
        List<String> existingActions = new ArrayList<>(List.of("UPDATE_PASSWORD", "VERIFY_EMAIL"));
        mockUserRepresentation.setRequiredActions(existingActions);
        when(userResource.toRepresentation()).thenReturn(mockUserRepresentation);

        // Act
        passwordRotationService.rotatePassword(testUserId);

        // Assert
        // UserRepresentation should still be updated, even if action is already there.
        // Keycloak handles idempotency of adding required actions.
        ArgumentCaptor<UserRepresentation> userRepCaptor = ArgumentCaptor.forClass(UserRepresentation.class);
        verify(userResource).update(userRepCaptor.capture());
        List<String> updatedActions = userRepCaptor.getValue().getRequiredActions();
        assertTrue(updatedActions.contains("UPDATE_PASSWORD"));
        assertTrue(updatedActions.contains("VERIFY_EMAIL"));
        assertEquals(2, updatedActions.size()); // No duplicates added

        verify(passwordRotationLogRepository).save(any(PasswordRotationLog.class));
        verify(kafkaPublisher).publishEvent(eq(AuthEvents.AUTH_EVENTS_TOPIC), eq(AuthEvents.PASSWORD_ROTATED), anyMap());
    }


    @Test
    void rotatePassword_keycloakUserNotFound() {
        // Arrange
        when(usersResource.get(testUserId.toString())).thenThrow(new NotFoundException("User not found by Keycloak"));

        // Act & Assert
        assertThrows(NotFoundException.class, () -> passwordRotationService.rotatePassword(testUserId));

        verify(passwordRotationLogRepository, never()).save(any());
        verify(kafkaPublisher, never()).publishEvent(anyString(), anyString(), any());
    }

    @Test
    void rotatePassword_keycloakAdminApiError() {
        // Arrange
        Response mockResponse = Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Keycloak internal error").build();
        WebApplicationException keycloakException = new WebApplicationException("Simulated Keycloak Error", mockResponse);
        doThrow(keycloakException).when(userResource).update(any(UserRepresentation.class));
         when(userResource.toRepresentation()).thenReturn(mockUserRepresentation);


        // Act & Assert
        RuntimeException thrown = assertThrows(RuntimeException.class, () -> passwordRotationService.rotatePassword(testUserId));
        assertTrue(thrown.getMessage().contains("Error during password rotation with Keycloak"));

        verify(passwordRotationLogRepository, never()).save(any());
        verify(kafkaPublisher, never()).publishEvent(anyString(), anyString(), any());
    }

    @Test
    void rotatePassword_repositorySaveError() {
        // Arrange
        when(userResource.toRepresentation()).thenReturn(mockUserRepresentation);
        doThrow(new RuntimeException("DB save failed")).when(passwordRotationLogRepository).save(any(PasswordRotationLog.class));

        // Act & Assert
        RuntimeException thrown = assertThrows(RuntimeException.class, () -> passwordRotationService.rotatePassword(testUserId));
        assertTrue(thrown.getMessage().contains("Unexpected error during password rotation")); // Generic wrapper
        assertTrue(thrown.getCause().getMessage().contains("DB save failed"));


        // Keycloak update should have been called
        verify(userResource).update(any(UserRepresentation.class));
        // Kafka publish should not be called if DB save fails (due to @Transactional behavior assumption,
        // or if explicitly coded to publish after successful save)
        verify(kafkaPublisher, never()).publishEvent(anyString(), anyString(), any());
    }

    @Test
    void rotatePassword_kafkaPublishError() {
         // Arrange
        when(userResource.toRepresentation()).thenReturn(mockUserRepresentation);
        doThrow(new RuntimeException("Kafka publish failed")).when(kafkaPublisher).publishEvent(anyString(), anyString(), anyMap());

        // Act & Assert
        // If Kafka fails after DB save, @Transactional might rollback DB save if Kafka ops are part of TX.
        // If not (typical for Kafka), then DB is saved but event fails. The service should reflect this.
        // Current code would throw RuntimeException.
        RuntimeException thrown = assertThrows(RuntimeException.class, () -> passwordRotationService.rotatePassword(testUserId));
        assertTrue(thrown.getMessage().contains("Unexpected error during password rotation"));
        assertTrue(thrown.getCause().getMessage().contains("Kafka publish failed"));


        // Keycloak update and DB save should have been called
        verify(userResource).update(any(UserRepresentation.class));
        verify(passwordRotationLogRepository).save(any(PasswordRotationLog.class));
    }
}
