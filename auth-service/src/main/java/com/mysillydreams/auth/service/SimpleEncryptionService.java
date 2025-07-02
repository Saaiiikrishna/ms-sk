package com.mysillydreams.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class SimpleEncryptionService {

    private static final Logger logger = LoggerFactory.getLogger(SimpleEncryptionService.class);
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12; // 96 bits for GCM IV is recommended
    private static final int GCM_TAG_LENGTH = 128; // bits

    private final SecretKey secretKey;

    public SimpleEncryptionService(@Value("${app.simple-encryption.secret-key}") String keyStr) {
        if (keyStr == null || keyStr.length() < 16) { // AES requires 16, 24, or 32 bytes
            logger.error("SimpleEncryptionService: Secret key is null or too short. Must be at least 16 characters for AES-128.");
            throw new IllegalArgumentException("Invalid secret key length for SimpleEncryptionService. Must be 16, 24, or 32 bytes.");
        }
        // Use first 16, 24, or 32 bytes of the key string for AES
        int keyLength = 16; // Default to AES-128
        if (keyStr.length() >= 32) {
            keyLength = 32; // AES-256
        } else if (keyStr.length() >= 24) {
            keyLength = 24; // AES-192
        }
        byte[] keyBytes = new byte[keyLength];
        System.arraycopy(keyStr.getBytes(StandardCharsets.UTF_8), 0, keyBytes, 0, keyLength);
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
        logger.info("SimpleEncryptionService initialized with AES-{} key.", keyLength * 8);
    }

    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            SecureRandom random = new SecureRandom();
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmParameterSpec);

            byte[] ciphertextBytes = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // Prepend IV to ciphertext for storage: IV + Ciphertext
            byte[] encryptedDataWithIv = new byte[iv.length + ciphertextBytes.length];
            System.arraycopy(iv, 0, encryptedDataWithIv, 0, iv.length);
            System.arraycopy(ciphertextBytes, 0, encryptedDataWithIv, iv.length, ciphertextBytes.length);

            return Base64.getEncoder().encodeToString(encryptedDataWithIv);
        } catch (GeneralSecurityException e) {
            logger.error("Encryption failed: {}", e.getMessage(), e);
            throw new EncryptionOperationException("Encryption failed", e);
        }
    }

    public String decrypt(String base64EncryptedDataWithIv) {
        if (base64EncryptedDataWithIv == null) {
            return null;
        }
        try {
            byte[] encryptedDataWithIv = Base64.getDecoder().decode(base64EncryptedDataWithIv);

            if (encryptedDataWithIv.length < GCM_IV_LENGTH) {
                throw new IllegalArgumentException("Invalid encrypted data format: too short to contain IV.");
            }

            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(encryptedDataWithIv, 0, iv, 0, iv.length);

            byte[] ciphertextBytes = new byte[encryptedDataWithIv.length - iv.length];
            System.arraycopy(encryptedDataWithIv, iv.length, ciphertextBytes, 0, ciphertextBytes.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmParameterSpec);

            byte[] plaintextBytes = cipher.doFinal(ciphertextBytes);
            return new String(plaintextBytes, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) { // Catch Base64 decoding errors too
            logger.error("Decryption failed: {}", e.getMessage(), e);
            throw new EncryptionOperationException("Decryption failed", e);
        }
    }

    // Custom exception for encryption/decryption operations
    public static class EncryptionOperationException extends RuntimeException {
        public EncryptionOperationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
