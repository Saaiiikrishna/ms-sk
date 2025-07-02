package com.mysillydreams.userservice.service.inventory;

import com.mysillydreams.userservice.domain.UserEntity;
import com.mysillydreams.userservice.domain.inventory.InventoryProfile;
import com.mysillydreams.userservice.dto.inventory.InventoryProfileDto;
import com.mysillydreams.userservice.repository.UserRepository; // To save UserEntity with new role
import com.mysillydreams.userservice.repository.inventory.InventoryProfileRepository;
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
public class InventoryOnboardingService {

    private static final Logger logger = LoggerFactory.getLogger(InventoryOnboardingService.class);

    private final UserService userService; // To fetch the UserEntity
    private final UserRepository userRepository; // To save UserEntity after role modification
    private final InventoryProfileRepository inventoryProfileRepository;

    private static final String ROLE_INVENTORY_USER = "ROLE_INVENTORY_USER";

    @Autowired
    public InventoryOnboardingService(UserService userService,
                                      UserRepository userRepository,
                                      InventoryProfileRepository inventoryProfileRepository) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.inventoryProfileRepository = inventoryProfileRepository;
    }

    /**
     * Registers an existing user as an inventory user by creating an InventoryProfile
     * and assigning the ROLE_INVENTORY_USER.
     *
     * @param userId The UUID of the user to be registered.
     * @return The created InventoryProfile.
     * @throws EntityNotFoundException if the user with the given ID does not exist.
     * @throws IllegalStateException if the user already has an inventory profile.
     */
    @Transactional
    public InventoryProfile registerInventoryUser(UUID userId) {
        Assert.notNull(userId, "User ID cannot be null for inventory registration.");
        logger.info("Attempting to register inventory profile for User ID: {}", userId);

        UserEntity user = userService.getById(userId); // Throws EntityNotFoundException if user doesn't exist

        // Check if user already has an inventory profile
        Optional<InventoryProfile> existingProfileOpt = inventoryProfileRepository.findByUser(user);
        if (existingProfileOpt.isPresent()) {
            logger.warn("User ID: {} already has an existing inventory profile (ID: {}). Registration aborted.",
                    userId, existingProfileOpt.get().getId());
            throw new IllegalStateException("User already has an inventory profile.");
        }

        // Add ROLE_INVENTORY_USER to the user
        boolean roleAdded = user.getRoles().add(ROLE_INVENTORY_USER);
        if (roleAdded) {
            logger.info("Role {} added to User ID: {}. Saving user.", ROLE_INVENTORY_USER, userId);
            userRepository.save(user); // Persist role change for the user
        } else {
            logger.info("User ID: {} already has role {}.", userId, ROLE_INVENTORY_USER);
        }

        // Create and save the new InventoryProfile
        InventoryProfile inventoryProfile = new InventoryProfile();
        inventoryProfile.setUser(user);
        InventoryProfile savedProfile = inventoryProfileRepository.save(inventoryProfile);

        logger.info("InventoryProfile created with ID: {} for User ID: {}", savedProfile.getId(), userId);
        return savedProfile;
    }

    /**
     * Retrieves the inventory profile DTO for a given user ID.
     *
     * @param userId The UUID of the user.
     * @return The InventoryProfileDto.
     * @throws EntityNotFoundException if no user or inventory profile is found.
     */
    @Transactional(readOnly = true)
    public InventoryProfileDto getInventoryProfileByUserId(UUID userId) {
        Assert.notNull(userId, "User ID cannot be null.");
        logger.debug("Fetching inventory profile for User ID: {}", userId);

        // UserEntity user = userService.getById(userId); // Ensures user exists first
        // InventoryProfile profile = inventoryProfileRepository.findByUser(user)
        InventoryProfile profile = inventoryProfileRepository.findByUserId(userId) // More direct
                .orElseThrow(() -> {
                    logger.warn("No inventory profile found for User ID: {}", userId);
                    return new EntityNotFoundException("Not an inventory user or profile not found for user ID: " + userId);
                });

        logger.debug("Inventory profile found for User ID: {}. Profile ID: {}", userId, profile.getId());
        return InventoryProfileDto.from(profile);
    }

    /**
     * Retrieves the InventoryProfile entity for a given user ID.
     * Used internally by controllers or other services needing the entity.
     *
     * @param userId The UUID of the user.
     * @return The InventoryProfile entity.
     * @throws EntityNotFoundException if no user or inventory profile is found.
     */
    @Transactional(readOnly = true)
    public InventoryProfile getInventoryProfileEntityByUserId(UUID userId) {
        Assert.notNull(userId, "User ID cannot be null.");
        return inventoryProfileRepository.findByUserId(userId)
            .orElseThrow(() -> new EntityNotFoundException("Inventory profile not found for user ID: " + userId));
    }
}
