package com.mysillydreams.auth.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SimpleEncryptionServiceTest {

    // Use keys of valid AES lengths (16, 24, or 32 bytes)
    private final String key16Byte = "ThisIsA16ByteKey"; // 16 bytes
    private final String key24Byte = "ThisIsA24ByteKeyForAES192"; // 24 bytes
    private final String key32Byte = "ThisIsA32ByteKeyForAES256Yeah!"; // 32 bytes

    @Test
    void encryptDecrypt_with16ByteKey_shouldReturnOriginal() {
        SimpleEncryptionService service = new SimpleEncryptionService(key16Byte);
        String originalText = "Hello, AES-128 GCM!";
        String encrypted = service.encrypt(originalText);
        assertNotNull(encrypted);
        assertNotEquals(originalText, encrypted);
        String decrypted = service.decrypt(encrypted);
        assertEquals(originalText, decrypted);
    }

    @Test
    void encryptDecrypt_with24ByteKey_shouldReturnOriginal() {
        SimpleEncryptionService service = new SimpleEncryptionService(key24Byte);
        String originalText = "Hello, AES-192 GCM!";
        String encrypted = service.encrypt(originalText);
        assertNotNull(encrypted);
        assertNotEquals(originalText, encrypted);
        String decrypted = service.decrypt(encrypted);
        assertEquals(originalText, decrypted);
    }

    @Test
    void encryptDecrypt_with32ByteKey_shouldReturnOriginal() {
        SimpleEncryptionService service = new SimpleEncryptionService(key32Byte);
        String originalText = "Hello, AES-256 GCM!";
        String encrypted = service.encrypt(originalText);
        assertNotNull(encrypted);
        assertNotEquals(originalText, encrypted);
        String decrypted = service.decrypt(encrypted);
        assertEquals(originalText, decrypted);
    }

    @Test
    void encrypt_nullPlaintext_shouldReturnNull() {
        SimpleEncryptionService service = new SimpleEncryptionService(key16Byte);
        assertNull(service.encrypt(null));
    }

    @Test
    void decrypt_nullCiphertext_shouldReturnNull() {
        SimpleEncryptionService service = new SimpleEncryptionService(key16Byte);
        assertNull(service.decrypt(null));
    }

    @Test
    void decrypt_tamperedCiphertext_shouldThrowException() {
        SimpleEncryptionService service = new SimpleEncryptionService(key16Byte);
        String originalText = "Some data";
        String encrypted = service.encrypt(originalText);
        String tamperedEncrypted = encrypted.substring(0, encrypted.length() - 1) + "X"; // Tamper last char

        assertThrows(SimpleEncryptionService.EncryptionOperationException.class, () -> {
            service.decrypt(tamperedEncrypted);
        });
    }

    @Test
    void decrypt_invalidBase64_shouldThrowException() {
        SimpleEncryptionService service = new SimpleEncryptionService(key16Byte);
        String invalidBase64 = "!@#$%^&*()";
         assertThrows(SimpleEncryptionService.EncryptionOperationException.class, () -> {
            service.decrypt(invalidBase64);
        });
    }

    @Test
    void decrypt_tooShortCiphertext_shouldThrowException() {
        SimpleEncryptionService service = new SimpleEncryptionService(key16Byte);
        // IV length is 12. Data shorter than that is invalid.
        String shortCiphertext = "Short"; // Base64 of "U2hvcnQ=" is too short
         assertThrows(SimpleEncryptionService.EncryptionOperationException.class, () -> {
            service.decrypt(shortCiphertext);
        });
    }


    @Test
    void constructor_invalidKeyLength_shouldThrowIllegalArgumentException() {
        String shortKey = "TooShort"; // Less than 16 bytes
        assertThrows(IllegalArgumentException.class, () -> {
            new SimpleEncryptionService(shortKey);
        });
    }

    @Test
    void constructor_nullKey_shouldThrowIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> {
            new SimpleEncryptionService(null);
        });
    }

    @Test
    void encryptDecrypt_emptyString_shouldWork() {
        SimpleEncryptionService service = new SimpleEncryptionService(key16Byte);
        String originalText = "";
        String encrypted = service.encrypt(originalText);
        assertNotNull(encrypted);
        // Empty string encryption still produces IV + tag, so not empty
        assertNotEquals("", encrypted);

        String decrypted = service.decrypt(encrypted);
        assertEquals(originalText, decrypted);
    }
}
