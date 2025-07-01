package com.mysillydreams.userservice.converter;

import com.mysillydreams.userservice.service.EncryptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.vault.VaultException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CryptoConverterTest {

    @Mock
    private EncryptionService mockEncryptionService;

    private CryptoConverter cryptoConverter;

    @BeforeEach
    void setUp() {
        cryptoConverter = new CryptoConverter();
        // Manually inject the mock service using the static setter
        cryptoConverter.setEncryptionService(mockEncryptionService);
    }

    @Test
    void convertToDatabaseColumn_withNonNullAttribute_shouldEncrypt() {
        String attribute = "sensitiveData";
        String encryptedData = "encrypted:sensitiveData";
        when(mockEncryptionService.encrypt(attribute)).thenReturn(encryptedData);

        String dbData = cryptoConverter.convertToDatabaseColumn(attribute);

        assertEquals(encryptedData, dbData);
        verify(mockEncryptionService).encrypt(attribute);
    }

    @Test
    void convertToDatabaseColumn_withNullAttribute_shouldReturnNull() {
        String dbData = cryptoConverter.convertToDatabaseColumn(null);
        assertNull(dbData);
        verifyNoInteractions(mockEncryptionService);
    }

    @Test
    void convertToDatabaseColumn_withEmptyAttribute_shouldReturnEmpty() {
        String dbData = cryptoConverter.convertToDatabaseColumn("");
        assertEquals("", dbData); // As per current CryptoConverter logic
        verifyNoInteractions(mockEncryptionService);
    }

    @Test
    void convertToDatabaseColumn_whenEncryptionFails_shouldThrowRuntimeException() {
        String attribute = "sensitiveData";
        when(mockEncryptionService.encrypt(attribute)).thenThrow(new VaultException("Encryption failed in test"));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            cryptoConverter.convertToDatabaseColumn(attribute);
        });
        assertTrue(exception.getMessage().contains("Failed to encrypt data"));
        assertTrue(exception.getCause() instanceof VaultException);
    }

    @Test
    void convertToEntityAttribute_withNonNullDbData_shouldDecrypt() {
        String dbData = "encrypted:sensitiveData";
        String decryptedData = "sensitiveData";
        when(mockEncryptionService.decrypt(dbData)).thenReturn(decryptedData);

        String attribute = cryptoConverter.convertToEntityAttribute(dbData);

        assertEquals(decryptedData, attribute);
        verify(mockEncryptionService).decrypt(dbData);
    }

    @Test
    void convertToEntityAttribute_withNullDbData_shouldReturnNull() {
        String attribute = cryptoConverter.convertToEntityAttribute(null);
        assertNull(attribute);
        verifyNoInteractions(mockEncryptionService);
    }

    @Test
    void convertToEntityAttribute_withEmptyDbData_shouldReturnEmpty() {
        String attribute = cryptoConverter.convertToEntityAttribute("");
        assertEquals("", attribute); // As per current CryptoConverter logic
        verifyNoInteractions(mockEncryptionService);
    }


    @Test
    void convertToEntityAttribute_whenDecryptionFails_shouldThrowRuntimeException() {
        String dbData = "encrypted:sensitiveData";
        when(mockEncryptionService.decrypt(dbData)).thenThrow(new VaultException("Decryption failed in test"));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            cryptoConverter.convertToEntityAttribute(dbData);
        });
        assertTrue(exception.getMessage().contains("Failed to decrypt data"));
        assertTrue(exception.getCause() instanceof VaultException);
    }

    @Test
    void convertToDatabaseColumn_encryptionServiceNotSet_shouldThrowIllegalStateException() {
        // Temporarily set encryptionService to null for this specific test case
        cryptoConverter.setEncryptionService(null); // This might be tricky due to static nature if other tests run in parallel
                                                  // or if @BeforeEach always sets it.
                                                  // A better way would be to have a dedicated test instance
                                                  // or a way to reliably nullify for this test.
                                                  // For this specific test, we'll assume @BeforeEach runs first, then we nullify.

        // Re-create converter for a clean static state, if possible, or rely on the nullification
        CryptoConverter localConverter = new CryptoConverter();
        // Don't call setEncryptionService on localConverter

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            localConverter.convertToDatabaseColumn("test");
        });
        assertTrue(exception.getMessage().contains("EncryptionService not available"));

        // Restore for other tests
        cryptoConverter.setEncryptionService(mockEncryptionService);
    }

    @Test
    void convertToEntityAttribute_encryptionServiceNotSet_shouldThrowIllegalStateException() {
        CryptoConverter localConverter = new CryptoConverter(); // Fresh instance without service set

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            localConverter.convertToEntityAttribute("test");
        });
        assertTrue(exception.getMessage().contains("EncryptionService not available"));
    }
}
