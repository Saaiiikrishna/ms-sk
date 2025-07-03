package com.mysillydreams.userservice.service.support;

import com.mysillydreams.userservice.domain.UserEntity;
import com.mysillydreams.userservice.domain.support.SupportProfile;
import com.mysillydreams.userservice.repository.UserRepository;
import com.mysillydreams.userservice.repository.support.SupportProfileRepository;
import com.mysillydreams.userservice.service.UserService;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SupportOnboardingServiceTest {

    @Mock
    private UserService mockUserService;
    @Mock
    private UserRepository mockUserRepository;
    @Mock
    private SupportProfileRepository mockSupportProfileRepository;

    @InjectMocks
    private SupportOnboardingService supportOnboardingService;

    private UUID testUserId;
    private UserEntity testUserEntity;
    private String testSpecialization = "Billing";

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        testUserEntity = new UserEntity();
        testUserEntity.setId(testUserId);
        testUserEntity.setRoles(new HashSet<>());
    }

    @Test
    void createSupportProfile_success_newUserToSupport() {
        when(mockUserService.getById(testUserId)).thenReturn(testUserEntity);
        when(mockSupportProfileRepository.findByUser(testUserEntity)).thenReturn(Optional.empty());
        when(mockUserRepository.save(any(UserEntity.class))).thenReturn(testUserEntity);
        when(mockSupportProfileRepository.save(any(SupportProfile.class)))
                .thenAnswer(invocation -> {
                    SupportProfile profile = invocation.getArgument(0);
                    profile.setId(UUID.randomUUID()); // Simulate ID generation
                    return profile;
                });

        SupportProfile result = supportOnboardingService.createSupportProfile(testUserId, testSpecialization);

        assertNotNull(result);
        assertNotNull(result.getId());
        assertEquals(testUserEntity, result.getUser());
        assertEquals(testSpecialization, result.getSpecialization());
        assertTrue(result.isActive());
        assertTrue(testUserEntity.getRoles().contains("ROLE_SUPPORT_USER"));
        verify(mockUserRepository).save(testUserEntity);
        verify(mockSupportProfileRepository).save(any(SupportProfile.class));
    }

    @Test
    void createSupportProfile_userAlreadyHasRole_stillCreatesProfile() {
        testUserEntity.getRoles().add("ROLE_SUPPORT_USER");
        when(mockUserService.getById(testUserId)).thenReturn(testUserEntity);
        when(mockSupportProfileRepository.findByUser(testUserEntity)).thenReturn(Optional.empty());
        when(mockSupportProfileRepository.save(any(SupportProfile.class)))
                .thenAnswer(invocation -> {
                    SupportProfile profile = invocation.getArgument(0);
                    profile.setId(UUID.randomUUID());
                    return profile;
                });

        SupportProfile result = supportOnboardingService.createSupportProfile(testUserId, testSpecialization);

        assertNotNull(result);
        verify(mockUserRepository, never()).save(testUserEntity); // Role already exists
        verify(mockSupportProfileRepository).save(any(SupportProfile.class));
    }

    @Test
    void createSupportProfile_nullSpecialization_isAllowed() {
        when(mockUserService.getById(testUserId)).thenReturn(testUserEntity);
        when(mockSupportProfileRepository.findByUser(testUserEntity)).thenReturn(Optional.empty());
        when(mockSupportProfileRepository.save(any(SupportProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0)); // Return same instance for simplicity

        SupportProfile result = supportOnboardingService.createSupportProfile(testUserId, null);
        assertNull(result.getSpecialization());
    }


    @Test
    void createSupportProfile_userNotFound_throwsEntityNotFoundException() {
        when(mockUserService.getById(testUserId)).thenThrow(new EntityNotFoundException("User not found"));

        assertThrows(EntityNotFoundException.class, () -> {
            supportOnboardingService.createSupportProfile(testUserId, testSpecialization);
        });
    }

    @Test
    void createSupportProfile_profileAlreadyExists_throwsIllegalStateException() {
        when(mockUserService.getById(testUserId)).thenReturn(testUserEntity);
        when(mockSupportProfileRepository.findByUser(testUserEntity)).thenReturn(Optional.of(new SupportProfile()));

        assertThrows(IllegalStateException.class, () -> {
            supportOnboardingService.createSupportProfile(testUserId, testSpecialization);
        });
    }

    @Test
    void deactivateSupportProfile_profileExistsAndActive_deactivates() {
        SupportProfile profile = new SupportProfile();
        profile.setId(UUID.randomUUID());
        profile.setActive(true);
        when(mockSupportProfileRepository.findById(profile.getId())).thenReturn(Optional.of(profile));
        when(mockSupportProfileRepository.save(any(SupportProfile.class))).thenReturn(profile);

        SupportProfile result = supportOnboardingService.deactivateSupportProfile(profile.getId());

        assertFalse(result.isActive());
        verify(mockSupportProfileRepository).save(profile);
    }

    @Test
    void activateSupportProfile_profileExistsAndInactive_activates() {
        SupportProfile profile = new SupportProfile();
        profile.setId(UUID.randomUUID());
        profile.setActive(false);
        when(mockSupportProfileRepository.findById(profile.getId())).thenReturn(Optional.of(profile));
        when(mockSupportProfileRepository.save(any(SupportProfile.class))).thenReturn(profile);

        SupportProfile result = supportOnboardingService.activateSupportProfile(profile.getId());

        assertTrue(result.isActive());
        verify(mockSupportProfileRepository).save(profile);
    }


    @Test
    void getSupportProfileEntityByUserId_exists_returnsProfile() {
        SupportProfile profile = new SupportProfile();
        when(mockSupportProfileRepository.findByUserId(testUserId)).thenReturn(Optional.of(profile));
        SupportProfile result = supportOnboardingService.getSupportProfileEntityByUserId(testUserId);
        assertEquals(profile, result);
    }

    @Test
    void getSupportProfileEntityByUserId_notExists_throwsEntityNotFound() {
        when(mockSupportProfileRepository.findByUserId(testUserId)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> {
            supportOnboardingService.getSupportProfileEntityByUserId(testUserId);
        });
    }
}
