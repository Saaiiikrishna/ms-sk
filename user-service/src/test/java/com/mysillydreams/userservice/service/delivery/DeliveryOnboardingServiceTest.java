package com.mysillydreams.userservice.service.delivery;

import com.mysillydreams.userservice.domain.UserEntity;
import com.mysillydreams.userservice.domain.delivery.DeliveryProfile;
import com.mysillydreams.userservice.repository.UserRepository;
import com.mysillydreams.userservice.repository.delivery.DeliveryProfileRepository;
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
class DeliveryOnboardingServiceTest {

    @Mock
    private UserService mockUserService;
    @Mock
    private UserRepository mockUserRepository;
    @Mock
    private DeliveryProfileRepository mockDeliveryProfileRepository;

    @InjectMocks
    private DeliveryOnboardingService deliveryOnboardingService;

    private UUID testUserId;
    private UserEntity testUserEntity;
    private String testVehicleDetails = "Bike - Test1234";

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        testUserEntity = new UserEntity();
        testUserEntity.setId(testUserId);
        testUserEntity.setRoles(new HashSet<>());
    }

    @Test
    void createDeliveryProfile_success_newUserToDelivery() {
        when(mockUserService.getById(testUserId)).thenReturn(testUserEntity);
        when(mockDeliveryProfileRepository.findByUser(testUserEntity)).thenReturn(Optional.empty());
        when(mockUserRepository.save(any(UserEntity.class))).thenReturn(testUserEntity);
        when(mockDeliveryProfileRepository.save(any(DeliveryProfile.class)))
                .thenAnswer(invocation -> {
                    DeliveryProfile profile = invocation.getArgument(0);
                    profile.setId(UUID.randomUUID());
                    return profile;
                });

        DeliveryProfile result = deliveryOnboardingService.createDeliveryProfile(testUserId, testVehicleDetails);

        assertNotNull(result);
        assertNotNull(result.getId());
        assertEquals(testUserEntity, result.getUser());
        assertEquals(testVehicleDetails, result.getVehicleDetails());
        assertTrue(result.isActive());
        assertTrue(testUserEntity.getRoles().contains("ROLE_DELIVERY_USER"));
        verify(mockUserRepository).save(testUserEntity);
        verify(mockDeliveryProfileRepository).save(any(DeliveryProfile.class));
    }

    @Test
    void createDeliveryProfile_userAlreadyHasRole_stillCreatesProfile() {
        testUserEntity.getRoles().add("ROLE_DELIVERY_USER");
        when(mockUserService.getById(testUserId)).thenReturn(testUserEntity);
        when(mockDeliveryProfileRepository.findByUser(testUserEntity)).thenReturn(Optional.empty());
        // userRepository.save should NOT be called if role already exists
        when(mockDeliveryProfileRepository.save(any(DeliveryProfile.class)))
                .thenAnswer(invocation -> {
                    DeliveryProfile profile = invocation.getArgument(0);
                    profile.setId(UUID.randomUUID());
                    return profile;
                });

        DeliveryProfile result = deliveryOnboardingService.createDeliveryProfile(testUserId, testVehicleDetails);

        assertNotNull(result);
        verify(mockUserRepository, never()).save(testUserEntity);
        verify(mockDeliveryProfileRepository).save(any(DeliveryProfile.class));
    }

    @Test
    void createDeliveryProfile_userNotFound_throwsEntityNotFoundException() {
        when(mockUserService.getById(testUserId)).thenThrow(new EntityNotFoundException("User not found"));

        assertThrows(EntityNotFoundException.class, () -> {
            deliveryOnboardingService.createDeliveryProfile(testUserId, testVehicleDetails);
        });
        verifyNoInteractions(mockDeliveryProfileRepository, mockUserRepository);
    }

    @Test
    void createDeliveryProfile_profileAlreadyExists_throwsIllegalStateException() {
        when(mockUserService.getById(testUserId)).thenReturn(testUserEntity);
        when(mockDeliveryProfileRepository.findByUser(testUserEntity)).thenReturn(Optional.of(new DeliveryProfile()));

        assertThrows(IllegalStateException.class, () -> {
            deliveryOnboardingService.createDeliveryProfile(testUserId, testVehicleDetails);
        });
        verify(mockUserRepository, never()).save(any());
    }

    @Test
    void deactivateDeliveryProfile_profileExistsAndActive_deactivates() {
        DeliveryProfile profile = new DeliveryProfile();
        profile.setId(UUID.randomUUID());
        profile.setActive(true);
        when(mockDeliveryProfileRepository.findById(profile.getId())).thenReturn(Optional.of(profile));
        when(mockDeliveryProfileRepository.save(any(DeliveryProfile.class))).thenReturn(profile);

        DeliveryProfile result = deliveryOnboardingService.deactivateDeliveryProfile(profile.getId());

        assertFalse(result.isActive());
        verify(mockDeliveryProfileRepository).save(profile);
    }

    @Test
    void deactivateDeliveryProfile_profileAlreadyInactive_returnsProfile() {
        DeliveryProfile profile = new DeliveryProfile();
        profile.setId(UUID.randomUUID());
        profile.setActive(false);
        when(mockDeliveryProfileRepository.findById(profile.getId())).thenReturn(Optional.of(profile));

        DeliveryProfile result = deliveryOnboardingService.deactivateDeliveryProfile(profile.getId());

        assertFalse(result.isActive()); // Still false
        verify(mockDeliveryProfileRepository, never()).save(any()); // No save call
    }

    @Test
    void deactivateDeliveryProfile_profileNotFound_throwsEntityNotFoundException() {
        UUID nonExistentId = UUID.randomUUID();
        when(mockDeliveryProfileRepository.findById(nonExistentId)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> {
            deliveryOnboardingService.deactivateDeliveryProfile(nonExistentId);
        });
    }

    @Test
    void activateDeliveryProfile_profileExistsAndInactive_activates() {
        DeliveryProfile profile = new DeliveryProfile();
        profile.setId(UUID.randomUUID());
        profile.setActive(false);
        when(mockDeliveryProfileRepository.findById(profile.getId())).thenReturn(Optional.of(profile));
        when(mockDeliveryProfileRepository.save(any(DeliveryProfile.class))).thenReturn(profile);

        DeliveryProfile result = deliveryOnboardingService.activateDeliveryProfile(profile.getId());

        assertTrue(result.isActive());
        verify(mockDeliveryProfileRepository).save(profile);
    }

    @Test
    void getDeliveryProfileEntityByUserId_exists_returnsProfile() {
        DeliveryProfile profile = new DeliveryProfile();
        when(mockDeliveryProfileRepository.findByUserId(testUserId)).thenReturn(Optional.of(profile));
        DeliveryProfile result = deliveryOnboardingService.getDeliveryProfileEntityByUserId(testUserId);
        assertEquals(profile, result);
    }

    @Test
    void getDeliveryProfileEntityByUserId_notExists_throwsEntityNotFound() {
        when(mockDeliveryProfileRepository.findByUserId(testUserId)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> {
            deliveryOnboardingService.getDeliveryProfileEntityByUserId(testUserId);
        });
    }
}
