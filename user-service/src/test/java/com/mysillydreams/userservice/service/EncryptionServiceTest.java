package com.mysillydreams.userservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.vault.VaultException;
import org.springframework.vault.core.VaultTransitOperations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EncryptionServiceTest {

    @Mock
    private VaultTransitOperations vaultTransitOperations;

    // @Value("${spring.cloud.vault.transit.default-key-name:user-service-key}")
    // For unit tests, we don't rely on @Value injection from application properties.
    // We can either pass it in constructor or set it via reflection if needed,
    // but EncryptionService constructor takes it as an argument.
    private String testKeyName = "user-service-key-test";

    @InjectMocks
    private EncryptionService encryptionService;

    @BeforeEach
    void setUp() {
        // Re-initialize encryptionService with the mocked vaultTransitOperations and testKeyName
        // This is because @InjectMocks creates the instance before @Mock fields might be fully configured
        // for constructor injection if the constructor params are not just mocks.
        // Or, ensure the constructor of EncryptionService can take the key name directly.
        // The current EncryptionService constructor is:
        // public EncryptionService(VaultTransitOperations vaultTransitOperations, @Value(...) String transitKeyName)
        // @InjectMocks handles the VaultTransitOperations. For the @Value, it would be null unless we use Spring context.
        // So, we need to construct it manually or use ReflectionTestUtils for the key name.
        encryptionService = new EncryptionService(vaultTransitOperations, testKeyName);
    }

    @Test
    void encrypt_shouldCallVaultTransitOperationsAndReturnCiphertext() {
        String plaintext = "Hello, World!";
        String expectedCiphertext = "cipher:SGVsbG8sIFdvcmxkIQ=="; // Example
        when(vaultTransitOperations.encrypt(testKeyName, plaintext)).thenReturn(expectedCiphertext);

        String actualCiphertext = encryptionService.encrypt(plaintext);

        assertEquals(expectedCiphertext, actualCiphertext);
        verify(vaultTransitOperations).encrypt(testKeyName, plaintext);
    }

    @Test
    void encrypt_withNullPlaintext_shouldReturnNull() {
        String ciphertext = encryptionService.encrypt(null);
        assertNull(ciphertext);
        verifyNoInteractions(vaultTransitOperations);
    }

    @Test
    void encrypt_whenVaultThrowsException_shouldPropagateException() {
        String plaintext = "data";
        when(vaultTransitOperations.encrypt(testKeyName, plaintext)).thenThrow(new VaultException("Encryption error"));

        VaultException exception = assertThrows(VaultException.class, () -> {
            encryptionService.encrypt(plaintext);
        });

        assertEquals("Encryption error", exception.getMessage());
    }

    @Test
    void decrypt_shouldCallVaultTransitOperationsAndReturnPlaintext() {
        String ciphertext = "cipher:SGVsbG8sIFdvcmxkIQ==";
        String expectedPlaintext = "Hello, World!";
        when(vaultTransitOperations.decrypt(testKeyName, ciphertext)).thenReturn(expectedPlaintext);

        String actualPlaintext = encryptionService.decrypt(ciphertext);

        assertEquals(expectedPlaintext, actualPlaintext);
        verify(vaultTransitOperations).decrypt(testKeyName, ciphertext);
    }

    @Test
    void decrypt_withNullCiphertext_shouldReturnNull() {
        String plaintext = encryptionService.decrypt(null);
        assertNull(plaintext);
        verifyNoInteractions(vaultTransitOperations);
    }

    @Test
    void decrypt_whenVaultThrowsException_shouldPropagateException() {
        String ciphertext = "data";
        when(vaultTransitOperations.decrypt(testKeyName, ciphertext)).thenThrow(new VaultException("Decryption error"));

        VaultException exception = assertThrows(VaultException.class, () -> {
            encryptionService.decrypt(ciphertext);
        });

        assertEquals("Decryption error", exception.getMessage());
    }
}
