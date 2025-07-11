package com.mysillydreams.userservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.Base64;

/**
 * Mock encryption service for development when Vault is disabled.
 * In production, this should be replaced with proper Vault integration.
 * 
 * WARNING: This is NOT secure and should only be used for development!
 */
@Service
@Primary
@ConditionalOnProperty(name = "spring.cloud.vault.enabled", havingValue = "false", matchIfMissing = true)
public class MockEncryptionService implements EncryptionServiceInterface {

    private static final Logger logger = LoggerFactory.getLogger(MockEncryptionService.class);
    private static final String PREFIX = "MOCK_ENCRYPTED:";

    public MockEncryptionService() {
        logger.warn("Using MOCK EncryptionService - NOT SECURE! Only for development.");
    }

    /**
     * Mock encryption - just Base64 encode with a prefix.
     * WARNING: This is NOT secure!
     */
    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            String encoded = Base64.getEncoder().encodeToString(plaintext.getBytes());
            String result = PREFIX + encoded;
            logger.debug("Mock encrypted data. Plaintext length: {}, Result length: {}", 
                        plaintext.length(), result.length());
            return result;
        } catch (Exception e) {
            logger.error("Mock encryption failed: {}", e.getMessage(), e);
            throw new RuntimeException("Mock encryption failed", e);
        }
    }

    /**
     * Mock decryption - just Base64 decode after removing prefix.
     * WARNING: This is NOT secure!
     */
    public String decrypt(String ciphertext) {
        if (ciphertext == null) {
            return null;
        }
        try {
            if (!ciphertext.startsWith(PREFIX)) {
                // Assume it's already plaintext for backward compatibility
                logger.debug("Data doesn't have mock encryption prefix, returning as-is");
                return ciphertext;
            }
            
            String encoded = ciphertext.substring(PREFIX.length());
            String result = new String(Base64.getDecoder().decode(encoded));
            logger.debug("Mock decrypted data. Ciphertext length: {}, Result length: {}", 
                        ciphertext.length(), result.length());
            return result;
        } catch (Exception e) {
            logger.error("Mock decryption failed: {}", e.getMessage(), e);
            throw new RuntimeException("Mock decryption failed", e);
        }
    }
}
