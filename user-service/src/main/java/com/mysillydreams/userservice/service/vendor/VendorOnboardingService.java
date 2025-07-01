package com.mysillydreams.userservice.service.vendor;

import com.mysillydreams.userservice.domain.UserEntity;
import com.mysillydreams.userservice.domain.vendor.VendorProfile;
import com.mysillydreams.userservice.domain.vendor.VendorStatus;
import com.mysillydreams.userservice.dto.vendor.RegisterVendorRequest;
import com.mysillydreams.userservice.dto.vendor.VendorProfileDto;
import com.mysillydreams.userservice.repository.UserRepository; // To save UserEntity if roles change
import com.mysillydreams.userservice.repository.vendor.VendorProfileRepository;
// Re-using EntityNotFoundException from DocumentService for now, or define a common one.
// import com.mysillydreams.userservice.service.vendor.EntityNotFoundException;


import jakarta.persistence.EntityNotFoundException; // Using standard JPA one
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.Optional;
import java.util.UUID;

@Service
public class VendorOnboardingService {

    private static final Logger logger = LoggerFactory.getLogger(VendorOnboardingService.class);

    private final VendorProfileRepository vendorProfileRepository;
    private final UserRepository userRepository; // To save user if roles are updated
    private final KycOrchestratorClient kycOrchestratorClient;

    // Role constant
    private static final String ROLE_VENDOR_USER = "ROLE_VENDOR_USER"; // As specified in kickoff

    @Autowired
    public VendorOnboardingService(VendorProfileRepository vendorProfileRepository,
                                   UserRepository userRepository,
                                   KycOrchestratorClient kycOrchestratorClient) {
        this.vendorProfileRepository = vendorProfileRepository;
        this.userRepository = userRepository;
        this.kycOrchestratorClient = kycOrchestratorClient;
    }

    /**
     * Registers a new vendor by creating a VendorProfile, associating it with an existing UserEntity,
     * assigning the VENDOR_USER role to the user, and initiating a KYC workflow.
     *
     * @param request The vendor registration request data.
     * @param user    The UserEntity to be associated with this vendor profile.
     * @return The created and updated VendorProfile.
     * @throws IllegalStateException if the user already has a vendor profile.
     */
    @Transactional
    public VendorProfile registerVendor(RegisterVendorRequest request, UserEntity user) {
        Assert.notNull(request, "RegisterVendorRequest cannot be null.");
        Assert.notNull(user, "UserEntity cannot be null.");
        Assert.notNull(user.getId(), "UserEntity ID cannot be null for vendor registration.");

        logger.info("Attempting to register vendor for User ID: {}, Legal Name: {}", user.getId(), request.getLegalName());

        // Check if user already has a vendor profile
        Optional<VendorProfile> existingProfileOpt = vendorProfileRepository.findByUser(user);
        if (existingProfileOpt.isPresent()) {
            logger.warn("User ID: {} already has an existing vendor profile (ID: {}). Registration aborted.",
                    user.getId(), existingProfileOpt.get().getId());
            throw new IllegalStateException("User already has an active vendor profile.");
        }

        // 1) Ensure user has ROLE_VENDOR_USER
        boolean roleAdded = user.getRoles().add(ROLE_VENDOR_USER);
        if (roleAdded) {
            logger.info("Role {} added to User ID: {}. Saving user.", ROLE_VENDOR_USER, user.getId());
            userRepository.save(user); // Save user if roles were modified
        } else {
            logger.info("User ID: {} already has role {}.", user.getId(), ROLE_VENDOR_USER);
        }

        // 2) Create VendorProfile
        VendorProfile vendorProfile = new VendorProfile();
        vendorProfile.setUser(user);
        vendorProfile.setLegalName(request.getLegalName());
        vendorProfile.setStatus(VendorStatus.REGISTERED); // Initial status

        VendorProfile savedVp = vendorProfileRepository.save(vendorProfile);
        logger.info("VendorProfile created with ID: {} for User ID: {}", savedVp.getId(), user.getId());

        // 3) Kick off KYC workflow
        logger.info("Starting KYC workflow for VendorProfile ID: {}", savedVp.getId());
        String workflowId = kycOrchestratorClient.startKycWorkflow(savedVp.getId().toString());
        savedVp.setKycWorkflowId(workflowId);
        savedVp.setStatus(VendorStatus.KYC_IN_PROGRESS);

        VendorProfile finalVp = vendorProfileRepository.save(savedVp); // Save again to store workflowId and status update
        logger.info("Vendor registration completed for User ID: {}. VendorProfile ID: {}, KYC Workflow ID: {}, Status: {}",
                user.getId(), finalVp.getId(), finalVp.getKycWorkflowId(), finalVp.getStatus());

        return finalVp;
    }

    /**
     * Retrieves the vendor profile DTO for a given user ID.
     *
     * @param userId The UUID of the user.
     * @return The VendorProfileDto.
     * @throws EntityNotFoundException if no vendor profile is found for the user.
     */
    @Transactional(readOnly = true)
    public VendorProfileDto getProfileByUserId(UUID userId) {
        Assert.notNull(userId, "User ID cannot be null.");
        logger.debug("Fetching vendor profile for User ID: {}", userId);

        // Fetch VendorProfile by user ID. findByUserId was added to VendorProfileRepository.
        VendorProfile vp = vendorProfileRepository.findByUserId(userId)
                .orElseThrow(() -> {
                    logger.warn("No vendor profile found for User ID: {}", userId);
                    return new EntityNotFoundException("No vendor profile found for user ID: " + userId);
                });

        logger.debug("Vendor profile found for User ID: {}. Profile ID: {}", userId, vp.getId());
        return VendorProfileDto.from(vp);
    }

    /**
     * Retrieves the raw VendorProfile entity for a given user ID.
     * Used internally or when the full entity is needed.
     *
     * @param userId The UUID of the user.
     * @return The VendorProfile entity.
     * @throws EntityNotFoundException if no vendor profile is found for the user.
     */
    @Transactional(readOnly = true)
    public VendorProfile getVendorProfileEntityByUserId(UUID userId) {
        Assert.notNull(userId, "User ID cannot be null.");
        return vendorProfileRepository.findByUserId(userId)
            .orElseThrow(() -> new EntityNotFoundException("Vendor profile not found for user ID: " + userId));
    }
}
