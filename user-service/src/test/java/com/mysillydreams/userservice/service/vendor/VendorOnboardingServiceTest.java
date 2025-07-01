package com.mysillydreams.userservice.service.vendor;

import com.mysillydreams.userservice.domain.UserEntity;
import com.mysillydreams.userservice.domain.vendor.VendorProfile;
import com.mysillydreams.userservice.domain.vendor.VendorStatus;
import com.mysillydreams.userservice.dto.vendor.RegisterVendorRequest;
import com.mysillydreams.userservice.dto.vendor.VendorProfileDto;
import com.mysillydreams.userservice.repository.UserRepository;
import com.mysillydreams.userservice.repository.vendor.VendorProfileRepository;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VendorOnboardingServiceTest {

    @Mock
    private VendorProfileRepository mockVendorProfileRepository;
    @Mock
    private UserRepository mockUserRepository;
    @Mock
    private KycOrchestratorClient mockKycOrchestratorClient;

    @InjectMocks
    private VendorOnboardingService vendorOnboardingService;

    private UserEntity testUser;
    private RegisterVendorRequest registerRequest;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        testUser = new UserEntity();
        testUser.setId(userId);
        testUser.setReferenceId(UUID.randomUUID().toString());
        testUser.setEmail("vendoruser@example.com");
        testUser.setRoles(new HashSet<>()); // Start with no roles

        registerRequest = new RegisterVendorRequest();
        registerRequest.setLegalName("Test Vendor Legal Name");
    }

    @Test
    void registerVendor_success_newUserToVendor() {
        // Arrange
        String mockWorkflowId = "wf-123";
        when(mockVendorProfileRepository.findByUser(testUser)).thenReturn(Optional.empty());
        when(mockUserRepository.save(any(UserEntity.class))).thenReturn(testUser); // Mock user save
        when(mockVendorProfileRepository.save(any(VendorProfile.class)))
                .thenAnswer(invocation -> { // First save (before KYC)
                    VendorProfile vp = invocation.getArgument(0);
                    vp.setId(UUID.randomUUID()); // Simulate ID generation
                    return vp;
                })
                .thenAnswer(invocation -> invocation.getArgument(0)); // Second save (after KYC)
        when(mockKycOrchestratorClient.startKycWorkflow(anyString())).thenReturn(mockWorkflowId);

        // Act
        VendorProfile resultProfile = vendorOnboardingService.registerVendor(registerRequest, testUser);

        // Assert
        assertNotNull(resultProfile);
        assertEquals(testUser, resultProfile.getUser());
        assertEquals(registerRequest.getLegalName(), resultProfile.getLegalName());
        assertTrue(testUser.getRoles().contains("ROLE_VENDOR_USER"));
        assertEquals(VendorStatus.KYC_IN_PROGRESS, resultProfile.getStatus());
        assertEquals(mockWorkflowId, resultProfile.getKycWorkflowId());

        verify(mockUserRepository).save(testUser); // User saved due to role addition
        verify(mockVendorProfileRepository, times(2)).save(any(VendorProfile.class));
        verify(mockKycOrchestratorClient).startKycWorkflow(resultProfile.getId().toString());
    }

    @Test
    void registerVendor_userAlreadyHasVendorRole() {
        testUser.getRoles().add("ROLE_VENDOR_USER"); // User already has the role
        String mockWorkflowId = "wf-456";

        when(mockVendorProfileRepository.findByUser(testUser)).thenReturn(Optional.empty());
        // No call to userRepository.save() expected if role already exists
        when(mockVendorProfileRepository.save(any(VendorProfile.class)))
                .thenAnswer(invocation -> {
                    VendorProfile vp = invocation.getArgument(0);
                    vp.setId(UUID.randomUUID());
                    return vp;
                })
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(mockKycOrchestratorClient.startKycWorkflow(anyString())).thenReturn(mockWorkflowId);

        VendorProfile resultProfile = vendorOnboardingService.registerVendor(registerRequest, testUser);

        assertNotNull(resultProfile);
        verify(mockUserRepository, never()).save(testUser); // Should not be called
        verify(mockVendorProfileRepository, times(2)).save(any(VendorProfile.class));
        assertEquals(VendorStatus.KYC_IN_PROGRESS, resultProfile.getStatus());
    }

    @Test
    void registerVendor_userAlreadyHasProfile_throwsIllegalStateException() {
        when(mockVendorProfileRepository.findByUser(testUser)).thenReturn(Optional.of(new VendorProfile()));

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            vendorOnboardingService.registerVendor(registerRequest, testUser);
        });

        assertEquals("User already has an active vendor profile.", exception.getMessage());
        verify(mockUserRepository, never()).save(any());
        verify(mockVendorProfileRepository, never()).save(any());
        verifyNoInteractions(mockKycOrchestratorClient);
    }

    @Test
    void getProfileByUserId_profileExists_returnsDto() {
        VendorProfile mockProfile = new VendorProfile();
        mockProfile.setId(UUID.randomUUID());
        mockProfile.setUser(testUser);
        mockProfile.setLegalName("Found Vendor");
        mockProfile.setStatus(VendorStatus.ACTIVE);

        when(mockVendorProfileRepository.findByUserId(userId)).thenReturn(Optional.of(mockProfile));

        VendorProfileDto resultDto = vendorOnboardingService.getProfileByUserId(userId);

        assertNotNull(resultDto);
        assertEquals(mockProfile.getId(), resultDto.getId());
        assertEquals("Found Vendor", resultDto.getLegalName());
        assertEquals(VendorStatus.ACTIVE, resultDto.getStatus());
    }

    @Test
    void getProfileByUserId_profileNotExists_throwsEntityNotFoundException() {
        when(mockVendorProfileRepository.findByUserId(userId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> {
            vendorOnboardingService.getProfileByUserId(userId);
        });
    }

    @Test
    void getVendorProfileEntityByUserId_profileExists_returnsEntity() {
        VendorProfile mockProfile = new VendorProfile();
        mockProfile.setId(UUID.randomUUID());
        mockProfile.setUser(testUser);
        when(mockVendorProfileRepository.findByUserId(userId)).thenReturn(Optional.of(mockProfile));

        VendorProfile resultEntity = vendorOnboardingService.getVendorProfileEntityByUserId(userId);

        assertNotNull(resultEntity);
        assertEquals(mockProfile.getId(), resultEntity.getId());
    }

    @Test
    void getVendorProfileEntityByUserId_profileNotExists_throwsEntityNotFoundException() {
        when(mockVendorProfileRepository.findByUserId(userId)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> {
            vendorOnboardingService.getVendorProfileEntityByUserId(userId);
        });
    }
}
