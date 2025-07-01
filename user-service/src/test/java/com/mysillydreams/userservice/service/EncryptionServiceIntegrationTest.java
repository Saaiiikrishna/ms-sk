package com.mysillydreams.userservice.service;

import com.mysillydreams.userservice.config.UserIntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.vault.VaultException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest // Loads full application context, including Vault connection from Initializer
public class EncryptionServiceIntegrationTest extends UserIntegrationTestBase {

    @Autowired
    private EncryptionService encryptionService; // Real service connected to Testcontainer Vault

    @Test
    void encryptAndDecrypt_shouldReturnOriginalPlaintext() {
        String originalPlaintext = "This is a highly sensitive piece of information!";

        // Encrypt
        String ciphertext = encryptionService.encrypt(originalPlaintext);
        assertThat(ciphertext).isNotNull();
        assertThat(ciphertext).isNotEqualTo(originalPlaintext);
        // Vault transit typically prefixes ciphertext with "vault:vX:" where X is key version
        assertThat(ciphertext).startsWith("vault:v");

        // Decrypt
        String decryptedPlaintext = encryptionService.decrypt(ciphertext);
        assertThat(decryptedPlaintext).isEqualTo(originalPlaintext);
    }

    @Test
    void encrypt_withNull_shouldReturnNull() {
        assertThat(encryptionService.encrypt(null)).isNull();
    }

    @Test
    void decrypt_withNull_shouldReturnNull() {
        assertThat(encryptionService.decrypt(null)).isNull();
    }

    @Test
    void decrypt_withInvalidCiphertext_shouldThrowVaultException() {
        String invalidCiphertext = "not-a-valid-ciphertext";
        // Depending on Vault version and error handling, this might also be caught by Spring Vault and rethrown.
        // Exact exception type might vary, but VaultException or subclass is expected.
        assertThrows(VaultException.class, () -> {
            encryptionService.decrypt(invalidCiphertext);
        });
    }

    @Test
    void encryptAndDecrypt_emptyString_shouldWork() {
        String originalPlaintext = "";

        String ciphertext = encryptionService.encrypt(originalPlaintext);
        assertThat(ciphertext).isNotNull();
        // Empty string encryption might still produce a non-empty ciphertext (metadata, etc.)
        // but decrypting it should yield an empty string.

        String decryptedPlaintext = encryptionService.decrypt(ciphertext);
        assertThat(decryptedPlaintext).isEqualTo(originalPlaintext);
    }

    @Test
    void multipleEncryptAndDecryptOperations_shouldWorkConsistently() {
        String text1 = "First secret message.";
        String text2 = "Second, different secret message!";

        String cipher1 = encryptionService.encrypt(text1);
        String cipher2 = encryptionService.encrypt(text2);

        assertThat(cipher1).isNotEqualTo(cipher2); // Different plaintexts should result in different ciphertexts

        assertThat(encryptionService.decrypt(cipher1)).isEqualTo(text1);
        assertThat(encryptionService.decrypt(cipher2)).isEqualTo(text2);
    }
}
