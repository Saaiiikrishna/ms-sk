package com.mysillydreams.userservice.service.support;

import com.mysillydreams.userservice.domain.UserEntity;
import com.mysillydreams.userservice.domain.support.SupportProfile;
import com.mysillydreams.userservice.repository.UserRepository;
import com.mysillydreams.userservice.repository.support.SupportProfileRepository;
import com.mysillydreams.userservice.service.UserService; // To get UserEntity by ID

import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.Optional;
import java.util.UUID;

@Service
public class SupportOnboardingService {

    private static final Logger logger = LoggerFactory.getLogger(SupportOnboardingService.class);

    private final UserService userService;
    private final UserRepository userRepository; // To save UserEntity after role modification
    private final SupportProfileRepository supportProfileRepository;

    private static final String ROLE_SUPPORT_USER = "ROLE_SUPPORT_USER";

    @Autowired
    public SupportOnboardingService(UserService userService,
                                    UserRepository userRepository,
                                    SupportProfileRepository supportProfileRepository) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.supportProfileRepository = supportProfileRepository;
    }

    /**
     * Creates a new SupportProfile for an existing user and assigns ROLE_SUPPORT_USER.
     *
     * @param userId         The UUID of the user to onboard as a support user.
     * @param specialization Optional specialization for the support user.
     * @return The created SupportProfile.
     * @throws EntityNotFoundException if the user does not exist.
     * @throws IllegalStateException   if the user already has a support profile.
     */
    @Transactional
    public SupportProfile createSupportProfile(UUID userId, String specialization) {
        Assert.notNull(userId, "User ID cannot be null for support profile creation.");
        logger.info("Attempting to create support profile for User ID: {}, Specialization: {}", userId, specialization);

        UserEntity user = userService.getById(userId); // Throws EntityNotFoundException if not found

        Optional<SupportProfile> existingProfileOpt = supportProfileRepository.findByUser(user);
        if (existingProfileOpt.isPresent()) {
            logger.warn("User ID: {} already has an existing support profile (ID: {}). Creation aborted.",
                    userId, existingProfileOpt.get().getId());
            throw new IllegalStateException("User already has an active support profile.");
        }

        boolean roleAdded = user.getRoles().add(ROLE_SUPPORT_USER);
        if (roleAdded) {
            logger.info("Role {} added to User ID: {}. Saving user.", ROLE_SUPPORT_USER, userId);
            userRepository.save(user);
        } else {
            logger.info("User ID: {} already has role {}.", userId, ROLE_SUPPORT_USER);
        }

        SupportProfile supportProfile = new SupportProfile();
        supportProfile.setUser(user);
        supportProfile.setSpecialization(specialization);
        supportProfile.setActive(true); // Active by default

        SupportProfile savedProfile = supportProfileRepository.save(supportProfile);
        logger.info("SupportProfile created with ID: {} for User ID: {}", savedProfile.getId(), userId);
        return savedProfile;
    }

    /**
     * Deactivates a support profile.
     *
     * @param profileId The UUID of the support profile to deactivate.
     * @return The updated, inactive SupportProfile.
     * @throws EntityNotFoundException if the profile does not exist.
     */
    @Transactional
    public SupportProfile deactivateSupportProfile(UUID profileId) {
        Assert.notNull(profileId, "Support Profile ID cannot be null for deactivation.");
        logger.info("Attempting to deactivate SupportProfile ID: {}", profileId);

        SupportProfile profile = supportProfileRepository.findById(profileId)
                .orElseThrow(() -> new EntityNotFoundException("SupportProfile not found with ID: " + profileId));

        if (!profile.isActive()) {
            logger.info("SupportProfile ID: {} is already inactive.", profileId);
            return profile;
        }

        profile.setActive(false);
        SupportProfile updatedProfile = supportProfileRepository.save(profile);
        logger.info("SupportProfile ID: {} deactivated successfully.", updatedProfile.getId());
        return updatedProfile;
    }

    /**
     * Activates an inactive support profile.
     *
     * @param profileId The UUID of the support profile to activate.
     * @return The updated, active SupportProfile.
     * @throws EntityNotFoundException if the profile does not exist.
     */
    @Transactional
    public SupportProfile activateSupportProfile(UUID profileId) {
        Assert.notNull(profileId, "Support Profile ID cannot be null for activation.");
        logger.info("Attempting to activate SupportProfile ID: {}", profileId);

        SupportProfile profile = supportProfileRepository.findById(profileId)
                .orElseThrow(() -> new EntityNotFoundException("SupportProfile not found with ID: " + profileId));

        if (profile.isActive()) {
            logger.info("SupportProfile ID: {} is already active.", profileId);
            return profile;
        }

        profile.setActive(true);
        SupportProfile updatedProfile = supportProfileRepository.save(profile);
        logger.info("SupportProfile ID: {} activated successfully.", updatedProfile.getId());
        return updatedProfile;
    }

    /**
     * Retrieves the SupportProfile entity for a given user ID.
     *
     * @param userId The UUID of the user.
     * @return The SupportProfile entity.
     * @throws EntityNotFoundException if no support profile is found for the user.
     */
    @Transactional(readOnly = true)
    public SupportProfile getSupportProfileEntityByUserId(UUID userId) {
        Assert.notNull(userId, "User ID cannot be null.");
        return supportProfileRepository.findByUserId(userId)
            .orElseThrow(() -> new EntityNotFoundException("Support profile not found for user ID: " + userId));
    }
}
