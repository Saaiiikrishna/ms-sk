package com.mysillydreams.userservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.vault.core.VaultTransitOperations;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultTransitContext;
import org.springframework.vault.VaultException;


@Service
@ConditionalOnProperty(name = "spring.cloud.vault.enabled", havingValue = "true")
public class EncryptionService implements EncryptionServiceInterface {

    private static final Logger logger = LoggerFactory.getLogger(EncryptionService.class);

    private final VaultTransitOperations vaultTransitOperations;
    private final String transitKeyName;

    @Autowired
    public EncryptionService(VaultTransitOperations vaultTransitOperations,
                             @Value("${spring.cloud.vault.transit.default-key-name:user-service-key}") String transitKeyName) {
        this.vaultTransitOperations = vaultTransitOperations;
        this.transitKeyName = transitKeyName;
        logger.info("EncryptionService initialized with Transit key name: {}", this.transitKeyName);
    }

    /**
     * Encrypts the given plaintext string.
     *
     * @param plaintext The string to encrypt.
     * @return The base64 encoded ciphertext, or null if plaintext was null.
     * @throws VaultException if encryption fails.
     */
    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            // Spring Vault's encrypt method for transit already returns Base64 encoded ciphertext.
            // The VaultTransitContext can be used if you need to provide context for convergent encryption, etc.
            // For simple encryption:
            String ciphertext = vaultTransitOperations.encrypt(transitKeyName, plaintext);
            logger.debug("Successfully encrypted data using key '{}'. Plaintext length: {}, Ciphertext length: {}",
                         transitKeyName, plaintext.length(), ciphertext != null ? ciphertext.length() : 0);
            return ciphertext;
        } catch (VaultException e) {
            logger.error("Encryption failed for key '{}': {}", transitKeyName, e.getMessage(), e);
            throw e; // Re-throw to allow higher-level error handling
        }
    }

    /**
     * Decrypts the given base64 encoded ciphertext string.
     *
     * @param ciphertext The base64 encoded string to decrypt.
     * @return The decrypted plaintext, or null if ciphertext was null.
     * @throws VaultException if decryption fails.
     */
    public String decrypt(String ciphertext) {
        if (ciphertext == null) {
            return null;
        }
        try {
            // Spring Vault's decrypt method for transit expects Base64 encoded ciphertext.
            String plaintext = vaultTransitOperations.decrypt(transitKeyName, ciphertext);
            logger.debug("Successfully decrypted data using key '{}'. Ciphertext length: {}",
                         transitKeyName, ciphertext.length());
            return plaintext;
        } catch (VaultException e) {
            logger.error("Decryption failed for key '{}': {}", transitKeyName, e.getMessage(), e);
            throw e; // Re-throw
        }
    }

    // Optional: Methods for encrypting/decrypting byte arrays if needed
    /*
    public byte[] encrypt(byte[] plaintextBytes) {
        if (plaintextBytes == null) {
            return null;
        }
        // VaultTransitOperations.encrypt(keyName, plaintextBytes, VaultTransitContext.empty()) returns VaultResponse
        // You'd need to get the ciphertext from VaultResponse.getData().get("ciphertext")
        // and then Base64 decode it if it's a string, or handle bytes directly.
        // This is more complex than the string version. The string version is simpler for JPA converters.
        throw new UnsupportedOperationException("Byte array encryption not implemented yet.");
    }

    public byte[] decrypt(byte[] ciphertextBytes) {
        if (ciphertextBytes == null) {
            return null;
        }
        // Similar complexity for byte array decryption.
        throw new UnsupportedOperationException("Byte array decryption not implemented yet.");
    }
    */
}
