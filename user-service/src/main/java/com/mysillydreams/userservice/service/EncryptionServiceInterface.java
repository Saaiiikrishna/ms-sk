package com.mysillydreams.userservice.service;

/**
 * Interface for encryption services.
 * This allows us to have different implementations (Vault-based, mock, etc.)
 */
public interface EncryptionServiceInterface {
    
    /**
     * Encrypts the given plaintext string.
     *
     * @param plaintext The string to encrypt.
     * @return The encrypted ciphertext, or null if plaintext was null.
     */
    String encrypt(String plaintext);

    /**
     * Decrypts the given ciphertext string.
     *
     * @param ciphertext The string to decrypt.
     * @return The decrypted plaintext, or null if ciphertext was null.
     */
    String decrypt(String ciphertext);
}
