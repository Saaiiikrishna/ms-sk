package com.mysillydreams.userservice.service.inventory;

import com.mysillydreams.userservice.domain.UserEntity;
import com.mysillydreams.userservice.domain.inventory.InventoryProfile;
import com.mysillydreams.userservice.dto.inventory.InventoryProfileDto;
import com.mysillydreams.userservice.repository.UserRepository;
import com.mysillydreams.userservice.repository.inventory.InventoryProfileRepository;
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
class InventoryOnboardingServiceTest {

    @Mock
    private UserService mockUserService;
    @Mock
    private UserRepository mockUserRepository;
    @Mock
    private InventoryProfileRepository mockInventoryProfileRepository;

    @InjectMocks
    private InventoryOnboardingService inventoryOnboardingService;

    private UUID testUserId;
    private UserEntity testUserEntity;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        testUserEntity = new UserEntity();
        testUserEntity.setId(testUserId);
        testUserEntity.setRoles(new HashSet<>()); // Start with empty roles
    }

    @Test
    void registerInventoryUser_success_newUserToInventory() {
        when(mockUserService.getById(testUserId)).thenReturn(testUserEntity);
        when(mockInventoryProfileRepository.findByUser(testUserEntity)).thenReturn(Optional.empty());
        when(mockUserRepository.save(any(UserEntity.class))).thenReturn(testUserEntity);
        when(mockInventoryProfileRepository.save(any(InventoryProfile.class)))
                .thenAnswer(invocation -> {
                    InventoryProfile profile = invocation.getArgument(0);
                    profile.setId(UUID.randomUUID()); // Simulate ID generation
                    return profile;
                });

        InventoryProfile result = inventoryOnboardingService.registerInventoryUser(testUserId);

        assertNotNull(result);
        assertNotNull(result.getId());
        assertEquals(testUserEntity, result.getUser());
        assertTrue(testUserEntity.getRoles().contains("ROLE_INVENTORY_USER"));
        verify(mockUserRepository).save(testUserEntity); // User saved due to role addition
        verify(mockInventoryProfileRepository).save(any(InventoryProfile.class));
    }

    @Test
    void registerInventoryUser_userAlreadyHasInventoryRole_stillCreatesProfile() {
        testUserEntity.getRoles().add("ROLE_INVENTORY_USER"); // User already has role

        when(mockUserService.getById(testUserId)).thenReturn(testUserEntity);
        when(mockInventoryProfileRepository.findByUser(testUserEntity)).thenReturn(Optional.empty());
        // userRepository.save should NOT be called if role already exists
        when(mockInventoryProfileRepository.save(any(InventoryProfile.class)))
                .thenAnswer(invocation -> {
                    InventoryProfile profile = invocation.getArgument(0);
                    profile.setId(UUID.randomUUID());
                    return profile;
                });

        InventoryProfile result = inventoryOnboardingService.registerInventoryUser(testUserId);

        assertNotNull(result);
        verify(mockUserRepository, never()).save(testUserEntity); // Not called
        verify(mockInventoryProfileRepository).save(any(InventoryProfile.class));
    }

    @Test
    void registerInventoryUser_userNotFound_throwsEntityNotFoundException() {
        when(mockUserService.getById(testUserId)).thenThrow(new EntityNotFoundException("User not found"));

        assertThrows(EntityNotFoundException.class, () -> {
            inventoryOnboardingService.registerInventoryUser(testUserId);
        });
        verifyNoInteractions(mockInventoryProfileRepository, mockUserRepository);
    }

    @Test
    void registerInventoryUser_profileAlreadyExists_throwsIllegalStateException() {
        when(mockUserService.getById(testUserId)).thenReturn(testUserEntity);
        when(mockInventoryProfileRepository.findByUser(testUserEntity)).thenReturn(Optional.of(new InventoryProfile()));

        assertThrows(IllegalStateException.class, () -> {
            inventoryOnboardingService.registerInventoryUser(testUserId);
        });
        verify(mockUserRepository, never()).save(any()); // Role check happens after profile check
    }

    @Test
    void getInventoryProfileByUserId_profileExists_returnsDto() {
        InventoryProfile mockProfile = new InventoryProfile();
        mockProfile.setId(UUID.randomUUID());
        mockProfile.setUser(testUserEntity); // UserEntity is required for InventoryProfileDto.from()

        when(mockInventoryProfileRepository.findByUserId(testUserId)).thenReturn(Optional.of(mockProfile));

        InventoryProfileDto resultDto = inventoryOnboardingService.getInventoryProfileByUserId(testUserId);

        assertNotNull(resultDto);
        assertEquals(mockProfile.getId(), resultDto.getId());
        assertEquals(testUserId, resultDto.getUserId());
    }

    @Test
    void getInventoryProfileByUserId_profileNotExists_throwsEntityNotFoundException() {
        when(mockInventoryProfileRepository.findByUserId(testUserId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> {
            inventoryOnboardingService.getInventoryProfileByUserId(testUserId);
        });
    }

    @Test
    void getInventoryProfileEntityByUserId_profileExists_returnsEntity() {
        InventoryProfile mockProfile = new InventoryProfile();
        mockProfile.setId(UUID.randomUUID());
        when(mockInventoryProfileRepository.findByUserId(testUserId)).thenReturn(Optional.of(mockProfile));

        InventoryProfile resultEntity = inventoryOnboardingService.getInventoryProfileEntityByUserId(testUserId);

        assertNotNull(resultEntity);
        assertEquals(mockProfile.getId(), resultEntity.getId());
    }

    @Test
    void getInventoryProfileEntityByUserId_profileNotExists_throwsEntityNotFoundException() {
        when(mockInventoryProfileRepository.findByUserId(testUserId)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> {
            inventoryOnboardingService.getInventoryProfileEntityByUserId(testUserId);
        });
    }
}
