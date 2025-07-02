package com.mysillydreams.auth.converter;

import com.mysillydreams.auth.service.SimpleEncryptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TotpSecretConverterTest {

    @Mock
    private SimpleEncryptionService mockEncryptionService;

    private TotpSecretConverter totpSecretConverter;

    @BeforeEach
    void setUp() {
        totpSecretConverter = new TotpSecretConverter();
        // Manually inject the mock service using the static setter
        totpSecretConverter.setEncryptionService(mockEncryptionService);
    }

    @Test
    void convertToDatabaseColumn_withNonNullAttribute_shouldEncrypt() {
        String attribute = "rawSecretKey";
        String encryptedData = "encrypted:rawSecretKey";
        when(mockEncryptionService.encrypt(attribute)).thenReturn(encryptedData);

        String dbData = totpSecretConverter.convertToDatabaseColumn(attribute);

        assertEquals(encryptedData, dbData);
        verify(mockEncryptionService).encrypt(attribute);
    }

    @Test
    void convertToDatabaseColumn_withNullAttribute_shouldReturnNull() {
        String dbData = totpSecretConverter.convertToDatabaseColumn(null);
        assertNull(dbData);
        verifyNoInteractions(mockEncryptionService);
    }

    @Test
    void convertToDatabaseColumn_withEmptyAttribute_shouldReturnEmpty() {
        String dbData = totpSecretConverter.convertToDatabaseColumn("");
        assertEquals("", dbData);
        verifyNoInteractions(mockEncryptionService);
    }

    @Test
    void convertToDatabaseColumn_whenEncryptionFails_shouldThrowRuntimeException() {
        String attribute = "rawSecretKey";
        when(mockEncryptionService.encrypt(attribute)).thenThrow(new SimpleEncryptionService.EncryptionOperationException("Test Encrypt Fail", null));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            totpSecretConverter.convertToDatabaseColumn(attribute);
        });
        assertTrue(exception.getMessage().contains("Failed to encrypt TOTP secret"));
        assertTrue(exception.getCause() instanceof SimpleEncryptionService.EncryptionOperationException);
    }

    @Test
    void convertToEntityAttribute_withNonNullDbData_shouldDecrypt() {
        String dbData = "encrypted:rawSecretKey";
        String decryptedData = "rawSecretKey";
        when(mockEncryptionService.decrypt(dbData)).thenReturn(decryptedData);

        String attribute = totpSecretConverter.convertToEntityAttribute(dbData);

        assertEquals(decryptedData, attribute);
        verify(mockEncryptionService).decrypt(dbData);
    }

    @Test
    void convertToEntityAttribute_withNullDbData_shouldReturnNull() {
        String attribute = totpSecretConverter.convertToEntityAttribute(null);
        assertNull(attribute);
        verifyNoInteractions(mockEncryptionService);
    }

    @Test
    void convertToEntityAttribute_withEmptyDbData_shouldReturnEmpty() {
        String attribute = totpSecretConverter.convertToEntityAttribute("");
        assertEquals("", attribute);
        verifyNoInteractions(mockEncryptionService);
    }

    @Test
    void convertToEntityAttribute_whenDecryptionFails_shouldThrowRuntimeException() {
        String dbData = "encrypted:rawSecretKey";
        when(mockEncryptionService.decrypt(dbData)).thenThrow(new SimpleEncryptionService.EncryptionOperationException("Test Decrypt Fail", null));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            totpSecretConverter.convertToEntityAttribute(dbData);
        });
        assertTrue(exception.getMessage().contains("Failed to decrypt TOTP secret"));
        assertTrue(exception.getCause() instanceof SimpleEncryptionService.EncryptionOperationException);
    }

    @Test
    void convertToDatabaseColumn_encryptionServiceNotSet_shouldThrowIllegalStateException() {
        TotpSecretConverter localConverter = new TotpSecretConverter(); // Fresh instance, service not set
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            localConverter.convertToDatabaseColumn("test");
        });
        assertTrue(exception.getMessage().contains("SimpleEncryptionService not available"));
    }
}
