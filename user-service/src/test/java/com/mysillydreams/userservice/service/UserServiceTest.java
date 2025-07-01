package com.mysillydreams.userservice.service;

import com.mysillydreams.userservice.domain.UserEntity;
import com.mysillydreams.userservice.dto.UserDto;
import com.mysillydreams.userservice.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private EncryptionService encryptionService; // Mocked, as its direct functionality is tested separately

    @InjectMocks
    private UserService userService;

    private UserDto userDto;
    private UserEntity userEntity;

    @BeforeEach
    void setUp() {
        userDto = new UserDto();
        userDto.setName("Test User");
        userDto.setEmail("test@example.com");
        userDto.setPhone("1234567890");
        userDto.setDob("1990-01-01");
        userDto.setGender("Male");
        userDto.setProfilePicUrl("http://example.com/pic.jpg");

        userEntity = new UserEntity();
        userEntity.setId(UUID.randomUUID());
        userEntity.setReferenceId(UUID.randomUUID().toString());
        userEntity.setName(userDto.getName());
        userEntity.setEmail(userDto.getEmail()); // In real entity, this would be encrypted if fetched
    }

    @Test
    void createUser_success() {
        // Arrange
        when(encryptionService.encrypt(userDto.getEmail())).thenReturn("encrypted_email_for_check");
        when(userRepository.findByEmail("encrypted_email_for_check")).thenReturn(Optional.empty());
        // When save is called, return the entity that would have been saved (or a mock of it)
        // For simplicity, we'll capture the argument and ensure it's what we expect, then return a mock/stub.
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> {
            UserEntity savedEntity = invocation.getArgument(0);
            savedEntity.setId(UUID.randomUUID()); // Simulate ID generation
            savedEntity.setCreatedAt(java.time.Instant.now());
            savedEntity.setUpdatedAt(java.time.Instant.now());
            return savedEntity;
        });

        // Act
        UserEntity createdUser = userService.createUser(userDto);

        // Assert
        assertNotNull(createdUser);
        assertNotNull(createdUser.getReferenceId());
        assertEquals(userDto.getName(), createdUser.getName());
        assertEquals(userDto.getEmail(), createdUser.getEmail()); // UserService sets plaintext, converter encrypts
        assertEquals(userDto.getPhone(), createdUser.getPhone());
        assertEquals(userDto.getDob(), createdUser.getDob());

        ArgumentCaptor<UserEntity> userEntityCaptor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(userEntityCaptor.capture());
        UserEntity capturedEntity = userEntityCaptor.getValue();
        assertEquals(userDto.getName(), capturedEntity.getName()); // Verify what's passed to save

        // verify(kafkaProducer).publishUserCreatedEvent(any(UserEntity.class)); // TODO: when Kafka is added
    }

    @Test
    void createUser_emailAlreadyExists_shouldThrowIllegalArgumentException() {
        when(encryptionService.encrypt(userDto.getEmail())).thenReturn("encrypted_email_for_check");
        when(userRepository.findByEmail("encrypted_email_for_check")).thenReturn(Optional.of(userEntity));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            userService.createUser(userDto);
        });

        assertTrue(exception.getMessage().contains("already exists"));
        verify(userRepository, never()).save(any(UserEntity.class));
    }

    @Test
    void createUser_invalidDobFormat_shouldThrowIllegalArgumentException() {
        userDto.setDob("01-01-1990"); // Invalid format

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            userService.createUser(userDto);
        });
        assertTrue(exception.getMessage().contains("Invalid Date of Birth format"));
    }


    @Test
    void getByReferenceId_userExists_shouldReturnUserEntity() {
        when(userRepository.findByReferenceId(userEntity.getReferenceId())).thenReturn(Optional.of(userEntity));

        UserEntity foundUser = userService.getByReferenceId(userEntity.getReferenceId());

        assertNotNull(foundUser);
        assertEquals(userEntity.getReferenceId(), foundUser.getReferenceId());
    }

    @Test
    void getByReferenceId_userNotExists_shouldThrowEntityNotFoundException() {
        String nonExistentRefId = "non-existent-ref-id";
        when(userRepository.findByReferenceId(nonExistentRefId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> {
            userService.getByReferenceId(nonExistentRefId);
        });
    }

    @Test
    void getById_userExists_shouldReturnUserEntity() {
        when(userRepository.findById(userEntity.getId())).thenReturn(Optional.of(userEntity));

        UserEntity foundUser = userService.getById(userEntity.getId());

        assertNotNull(foundUser);
        assertEquals(userEntity.getId(), foundUser.getId());
    }

    @Test
    void getById_userNotExists_shouldThrowEntityNotFoundException() {
        UUID nonExistentId = UUID.randomUUID();
        when(userRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> {
            userService.getById(nonExistentId);
        });
    }


    @Test
    void updateUser_success() {
        UserDto updateDto = new UserDto();
        updateDto.setName("Updated Test User");
        updateDto.setPhone("0987654321");
        updateDto.setDob("1995-05-05");

        when(userRepository.findByReferenceId(userEntity.getReferenceId())).thenReturn(Optional.of(userEntity));
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserEntity updatedUser = userService.updateUser(userEntity.getReferenceId(), updateDto);

        assertNotNull(updatedUser);
        assertEquals("Updated Test User", updatedUser.getName());
        assertEquals("0987654321", updatedUser.getPhone());
        assertEquals("1995-05-05", updatedUser.getDob());
        // Email should not have changed
        assertEquals(userDto.getEmail(), updatedUser.getEmail());

        verify(userRepository).save(userEntity);
    }

    @Test
    void updateUser_userNotFound_shouldThrowEntityNotFoundException() {
        UserDto updateDto = new UserDto();
        updateDto.setName("Updated Name");
        String nonExistentRefId = "non-existent-ref";
        when(userRepository.findByReferenceId(nonExistentRefId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> {
            userService.updateUser(nonExistentRefId, updateDto);
        });
        verify(userRepository, never()).save(any(UserEntity.class));
    }

    @Test
    void updateUser_blankNameInDto_shouldThrowIllegalArgumentException() {
        UserDto updateDto = new UserDto();
        updateDto.setName(""); // Attempt to set blank name

        when(userRepository.findByReferenceId(userEntity.getReferenceId())).thenReturn(Optional.of(userEntity));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            userService.updateUser(userEntity.getReferenceId(), updateDto);
        });
        assertTrue(exception.getMessage().contains("Name cannot be updated to blank"));
    }

    @Test
    void updateUser_invalidDobFormatInDto_shouldThrowIllegalArgumentException() {
        UserDto updateDto = new UserDto();
        updateDto.setDob("invalid-date");

        when(userRepository.findByReferenceId(userEntity.getReferenceId())).thenReturn(Optional.of(userEntity));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            userService.updateUser(userEntity.getReferenceId(), updateDto);
        });
        assertTrue(exception.getMessage().contains("Invalid Date of Birth format for update"));
    }
}
