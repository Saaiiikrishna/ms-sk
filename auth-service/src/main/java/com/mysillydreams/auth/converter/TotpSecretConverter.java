package com.mysillydreams.auth.converter;

import com.mysillydreams.auth.service.SimpleEncryptionService;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

// Based on the CryptoConverter pattern from User-Service
@Converter
@Component // Make it a Spring component so EncryptionService can be injected
public class TotpSecretConverter implements AttributeConverter<String, String> {

    private static final Logger logger = LoggerFactory.getLogger(TotpSecretConverter.class);
    private static SimpleEncryptionService encryptionService;

    @Autowired
    public void setEncryptionService(SimpleEncryptionService service) {
        if (TotpSecretConverter.encryptionService == null) {
            TotpSecretConverter.encryptionService = service;
            logger.info("SimpleEncryptionService statically injected into TotpSecretConverter.");
        } else {
            logger.warn("SimpleEncryptionService already set in TotpSecretConverter. Re-injection attempt ignored or accepted.");
            TotpSecretConverter.encryptionService = service;
        }
    }

    @Override
    public String convertToDatabaseColumn(String attribute) { // Plaintext TOTP secret
        if (attribute == null || attribute.isEmpty()) {
            return attribute;
        }
        if (encryptionService == null) {
            logger.error("TotpSecretConverter: SimpleEncryptionService is not initialized. Cannot encrypt TOTP secret.");
            throw new IllegalStateException("SimpleEncryptionService not available for TotpSecretConverter.");
        }
        try {
            return encryptionService.encrypt(attribute);
        } catch (SimpleEncryptionService.EncryptionOperationException e) {
            logger.error("Encryption of TOTP secret failed: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to encrypt TOTP secret for database persistence.", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) { // Encrypted TOTP secret from DB
        if (dbData == null || dbData.isEmpty()) {
            return dbData;
        }
        if (encryptionService == null) {
            logger.error("TotpSecretConverter: SimpleEncryptionService is not initialized. Cannot decrypt TOTP secret.");
            throw new IllegalStateException("SimpleEncryptionService not available for TotpSecretConverter.");
        }
        try {
            return encryptionService.decrypt(dbData);
        } catch (SimpleEncryptionService.EncryptionOperationException e) {
            logger.error("Decryption of TOTP secret failed: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to decrypt TOTP secret from database.", e);
        }
    }
}
