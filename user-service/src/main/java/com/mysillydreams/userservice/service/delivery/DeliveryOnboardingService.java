package com.mysillydreams.userservice.service.delivery;

import com.mysillydreams.userservice.domain.UserEntity;
import com.mysillydreams.userservice.domain.delivery.DeliveryProfile;
import com.mysillydreams.userservice.repository.UserRepository;
import com.mysillydreams.userservice.repository.delivery.DeliveryProfileRepository;
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
public class DeliveryOnboardingService {

    private static final Logger logger = LoggerFactory.getLogger(DeliveryOnboardingService.class);

    private final UserService userService;
    private final UserRepository userRepository; // To save UserEntity after role modification
    private final DeliveryProfileRepository deliveryProfileRepository;

    private static final String ROLE_DELIVERY_USER = "ROLE_DELIVERY_USER";

    @Autowired
    public DeliveryOnboardingService(UserService userService,
                                     UserRepository userRepository,
                                     DeliveryProfileRepository deliveryProfileRepository) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.deliveryProfileRepository = deliveryProfileRepository;
    }

    /**
     * Creates a new DeliveryProfile for an existing user and assigns ROLE_DELIVERY_USER.
     *
     * @param userId          The UUID of the user to onboard as a delivery user.
     * @param vehicleDetails  Optional vehicle details for the delivery user.
     * @return The created DeliveryProfile.
     * @throws EntityNotFoundException if the user does not exist.
     * @throws IllegalStateException   if the user already has a delivery profile.
     */
    @Transactional
    public DeliveryProfile createDeliveryProfile(UUID userId, String vehicleDetails) {
        Assert.notNull(userId, "User ID cannot be null for delivery profile creation.");
        logger.info("Attempting to create delivery profile for User ID: {}", userId);

        UserEntity user = userService.getById(userId); // Throws EntityNotFoundException if not found

        Optional<DeliveryProfile> existingProfileOpt = deliveryProfileRepository.findByUser(user);
        if (existingProfileOpt.isPresent()) {
            logger.warn("User ID: {} already has an existing delivery profile (ID: {}). Creation aborted.",
                    userId, existingProfileOpt.get().getId());
            throw new IllegalStateException("User already has an active delivery profile.");
        }

        boolean roleAdded = user.getRoles().add(ROLE_DELIVERY_USER);
        if (roleAdded) {
            logger.info("Role {} added to User ID: {}. Saving user.", ROLE_DELIVERY_USER, userId);
            userRepository.save(user);
        } else {
            logger.info("User ID: {} already has role {}.", userId, ROLE_DELIVERY_USER);
        }

        DeliveryProfile deliveryProfile = new DeliveryProfile();
        deliveryProfile.setUser(user);
        deliveryProfile.setVehicleDetails(vehicleDetails);
        deliveryProfile.setActive(true); // Active by default on creation

        DeliveryProfile savedProfile = deliveryProfileRepository.save(deliveryProfile);
        logger.info("DeliveryProfile created with ID: {} for User ID: {}", savedProfile.getId(), userId);
        return savedProfile;
    }

    /**
     * Deactivates a delivery profile. The profile remains but is marked inactive.
     *
     * @param profileId The UUID of the delivery profile to deactivate.
     * @return The updated, inactive DeliveryProfile.
     * @throws EntityNotFoundException if the profile does not exist.
     */
    @Transactional
    public DeliveryProfile deactivateDeliveryProfile(UUID profileId) {
        Assert.notNull(profileId, "Delivery Profile ID cannot be null for deactivation.");
        logger.info("Attempting to deactivate DeliveryProfile ID: {}", profileId);

        DeliveryProfile profile = deliveryProfileRepository.findById(profileId)
                .orElseThrow(() -> {
                    logger.warn("DeliveryProfile not found with ID: {} for deactivation.", profileId);
                    return new EntityNotFoundException("DeliveryProfile not found with ID: " + profileId);
                });

        if (!profile.isActive()) {
            logger.info("DeliveryProfile ID: {} is already inactive.", profileId);
            return profile; // Or throw exception if deactivating an already inactive one is an error
        }

        profile.setActive(false);
        DeliveryProfile updatedProfile = deliveryProfileRepository.save(profile);
        logger.info("DeliveryProfile ID: {} deactivated successfully.", updatedProfile.getId());
        // TODO: Consider if any Kafka event should be published for profile deactivation.
        return updatedProfile;
    }

    /**
     * Activates an inactive delivery profile.
     *
     * @param profileId The UUID of the delivery profile to activate.
     * @return The updated, active DeliveryProfile.
     * @throws EntityNotFoundException if the profile does not exist.
     */
    @Transactional
    public DeliveryProfile activateDeliveryProfile(UUID profileId) {
        Assert.notNull(profileId, "Delivery Profile ID cannot be null for activation.");
        logger.info("Attempting to activate DeliveryProfile ID: {}", profileId);

        DeliveryProfile profile = deliveryProfileRepository.findById(profileId)
                .orElseThrow(() -> {
                    logger.warn("DeliveryProfile not found with ID: {} for activation.", profileId);
                    return new EntityNotFoundException("DeliveryProfile not found with ID: " + profileId);
                });

        if (profile.isActive()) {
            logger.info("DeliveryProfile ID: {} is already active.", profileId);
            return profile;
        }

        profile.setActive(true);
        DeliveryProfile updatedProfile = deliveryProfileRepository.save(profile);
        logger.info("DeliveryProfile ID: {} activated successfully.", updatedProfile.getId());
        return updatedProfile;
    }


    /**
     * Retrieves the DeliveryProfile entity for a given user ID.
     *
     * @param userId The UUID of the user.
     * @return The DeliveryProfile entity.
     * @throws EntityNotFoundException if no delivery profile is found for the user.
     */
    @Transactional(readOnly = true)
    public DeliveryProfile getDeliveryProfileEntityByUserId(UUID userId) {
        Assert.notNull(userId, "User ID cannot be null.");
        return deliveryProfileRepository.findByUserId(userId)
            .orElseThrow(() -> new EntityNotFoundException("Delivery profile not found for user ID: " + userId));
    }
}
