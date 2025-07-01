package com.mysillydreams.userservice.service;

import com.mysillydreams.userservice.domain.UserEntity;
import com.mysillydreams.userservice.dto.UserDto;
import com.mysillydreams.userservice.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException; // Standard JPA
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;


import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import java.util.Optional;

@Service
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final EncryptionService encryptionService; // Needed if we were to check uniqueness on encrypted email

    @Autowired
    public UserService(UserRepository userRepository, EncryptionService encryptionService) {
        this.userRepository = userRepository;
        this.encryptionService = encryptionService;
    }

    /**
     * Creates a new user.
     *
     * @param dto The UserDto containing user data.
     * @return The created UserEntity.
     * @throws IllegalArgumentException if email already exists (example of a business rule).
     */
    @Transactional
    public UserEntity createUser(UserDto dto) {
        Assert.notNull(dto, "UserDto cannot be null");
        Assert.hasText(dto.getEmail(), "Email cannot be blank");
        Assert.hasText(dto.getName(), "Name cannot be blank");

        // Example: Check for email uniqueness before saving.
        // Note: This check happens on the *plaintext* email from the DTO.
        // The actual encrypted email in the DB is what needs to be unique.
        // If CryptoConverter makes encryption deterministic, we could encrypt here and query.
        // This is a simplified check. A robust check would involve trying to encrypt dto.getEmail()
        // and querying by the encrypted value, or relying on DB unique constraint on the encrypted email column.
        String encryptedEmailForCheck = encryptionService.encrypt(dto.getEmail());
        if (userRepository.findByEmail(encryptedEmailForCheck).isPresent()) {
             logger.warn("Attempt to create user with existing email: {}", dto.getEmail());
             throw new IllegalArgumentException("User with email " + dto.getEmail() + " already exists.");
        }
        // Validate DOB format if provided
        if (dto.getDob() != null && !dto.getDob().trim().isEmpty()) {
            try {
                LocalDate.parse(dto.getDob(), DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("Invalid Date of Birth format. Expected YYYY-MM-DD.", e);
            }
        }


        UserEntity userEntity = new UserEntity();
        userEntity.setReferenceId(UUID.randomUUID().toString()); // Generate a business reference ID
        userEntity.setName(dto.getName());
        userEntity.setEmail(dto.getEmail()); // Will be encrypted by CryptoConverter
        userEntity.setPhone(dto.getPhone()); // Will be encrypted
        userEntity.setDob(dto.getDob());     // Will be encrypted (as String "YYYY-MM-DD")
        userEntity.setGender(dto.getGender());
        userEntity.setProfilePicUrl(dto.getProfilePicUrl());

        // TODO: Handle addresses, paymentInfos if they are part of UserDto for creation

        UserEntity savedUser = userRepository.save(userEntity);
        logger.info("User created with referenceId: {}", savedUser.getReferenceId());
        // TODO: Publish user.created event to Kafka
        return savedUser;
    }

    /**
     * Retrieves a user by their business reference ID.
     *
     * @param referenceId The reference ID of the user.
     * @return The UserEntity.
     * @throws EntityNotFoundException if no user is found with the given reference ID.
     */
    @Transactional(readOnly = true)
    public UserEntity getByReferenceId(String referenceId) {
        Assert.hasText(referenceId, "Reference ID cannot be blank");
        return userRepository.findByReferenceId(referenceId)
                .orElseThrow(() -> {
                    logger.warn("User not found with referenceId: {}", referenceId);
                    return new EntityNotFoundException("User not found with reference ID: " + referenceId);
                });
    }

    /**
     * Retrieves a user by their primary UUID ID.
     *
     * @param id The UUID of the user.
     * @return The UserEntity.
     * @throws EntityNotFoundException if no user is found with the given ID.
     */
    @Transactional(readOnly = true)
    public UserEntity getById(UUID id) {
        Assert.notNull(id, "User ID cannot be null");
        return userRepository.findById(id)
                .orElseThrow(() -> {
                    logger.warn("User not found with ID: {}", id);
                    return new EntityNotFoundException("User not found with ID: " + id);
                });
    }


    /**
     * Updates an existing user.
     * (Stub for now - needs implementation for which fields can be updated)
     *
     * @param referenceId The reference ID of the user to update.
     * @param dto         The UserDto containing updated data.
     * @return The updated UserEntity.
     */
    @Transactional
    public UserEntity updateUser(String referenceId, UserDto dto) {
        Assert.hasText(referenceId, "Reference ID cannot be blank");
        Assert.notNull(dto, "UserDto cannot be null");

        UserEntity existingUser = getByReferenceId(referenceId); // Throws EntityNotFoundException if not found

        // Update allowed fields
        // Example: Name (ensure not blank if provided, otherwise keep old)
        if (dto.getName() != null) {
            if (dto.getName().trim().isEmpty()) throw new IllegalArgumentException("Name cannot be updated to blank.");
            existingUser.setName(dto.getName());
        }
        // Example: Phone
        if (dto.getPhone() != null) { // Allow setting phone to null/empty to remove it
            existingUser.setPhone(dto.getPhone().trim().isEmpty() ? null : dto.getPhone());
        }
        // Example: DOB
        if (dto.getDob() != null) {
            if (dto.getDob().trim().isEmpty()) {
                existingUser.setDob(null);
            } else {
                try {
                    LocalDate.parse(dto.getDob(), DateTimeFormatter.ISO_LOCAL_DATE); // Validate format
                    existingUser.setDob(dto.getDob());
                } catch (DateTimeParseException e) {
                    throw new IllegalArgumentException("Invalid Date of Birth format for update. Expected YYYY-MM-DD.", e);
                }
            }
        }
        // Email typically should not be updated directly or requires re-verification.
        // For this example, we won't allow email update via this method.
        if (dto.getEmail() != null && !dto.getEmail().equals(existingUser.getEmail())) {
             logger.warn("Attempt to update email for user {} (refId: {}) denied through this method.", existingUser.getId(), referenceId);
             // throw new UnsupportedOperationException("Email update requires a separate verification process.");
        }

        // TODO: Handle updates to collections like addresses, paymentInfos (e.g., add/remove/update items)

        UserEntity updatedUser = userRepository.save(existingUser);
        logger.info("User updated with referenceId: {}", updatedUser.getReferenceId());
        // TODO: Publish user.updated event to Kafka
        return updatedUser;
    }

    /**
     * Lists user sessions.
     * (Stub for now)
     *
     * @param referenceId The reference ID of the user.
     * @return A list of SessionEntity (or SessionDto).
     */
    @Transactional(readOnly = true)
    public List<?> listSessions(String referenceId) {
        // UserEntity user = getByReferenceId(referenceId);
        // return sessionRepository.findByUserOrderByLoginTimeDesc(user);
        // Or map to SessionDto
        logger.warn("listSessions method is a stub and not fully implemented.");
        return List.of(); // Placeholder
    }

    // TODO: Methods for managing addresses (add, update, delete)
    // TODO: Methods for managing payment info (add, update, delete - primarily token management)
    // TODO: Method for user deletion (soft delete / PII scrubbing as per GDPR Right-to-Be-Forgotten)
}
