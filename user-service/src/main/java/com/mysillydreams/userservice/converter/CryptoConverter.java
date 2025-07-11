package com.mysillydreams.userservice.converter;

import com.mysillydreams.userservice.service.EncryptionServiceInterface;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * JPA AttributeConverter to automatically encrypt and decrypt String entity attributes.
 * Uses a static EncryptionService field, which is a common pattern for
 * JPA converters that need Spring-managed beans, as JPA instantiates converters directly.
 *
 * To make this more robust and align with standard Spring dependency injection,
 * consider making this converter a Spring bean itself (@Component) and ensuring
 * that entities are managed in a way that allows Spring to inject dependencies
 * into converters if the JPA provider supports it, or use a static application context lookup
 * as a last resort (though generally discouraged).
 *
 * The @Component annotation allows Spring to manage this converter if it's scanned.
 * The static setter injection is a workaround for JPA's lifecycle.
 */
@Converter
@Component // Make it a Spring component so it can be a candidate for injection
public class CryptoConverter implements AttributeConverter<String, String> {

    private static final Logger logger = LoggerFactory.getLogger(CryptoConverter.class);
    private static EncryptionServiceInterface encryptionService;

    // Static setter method for Spring to inject the EncryptionService.
    // This method will be called by Spring after the CryptoConverter bean is created.
    @Autowired
    public void setEncryptionService(EncryptionServiceInterface service) {
        if (CryptoConverter.encryptionService == null) {
            CryptoConverter.encryptionService = service;
            logger.info("EncryptionService statically injected into CryptoConverter.");
        } else {
            // This might happen if multiple contexts try to set it, or in certain test scenarios.
            // Usually, it's benign if the same instance is re-injected.
            logger.warn("EncryptionService already set in CryptoConverter. Re-injection attempt ignored or accepted.");
            // To be safer, you could prevent re-injection if service != encryptionService, but that's unlikely with Spring's singleton default scope.
             CryptoConverter.encryptionService = service; // Allow re-injection for testability or context reloads
        }
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return attribute; // Return null or empty string as is
        }
        if (encryptionService == null) {
            logger.error("CryptoConverter: EncryptionService is not initialized. Cannot encrypt data.");
            // This should ideally not happen in a correctly configured Spring application.
            // Throwing an exception here might be too disruptive during entity persistence.
            // Depending on policy, you might return the attribute unencrypted with a severe warning,
            // or throw a specific runtime exception.
            throw new IllegalStateException("EncryptionService not available for CryptoConverter.");
        }
        try {
            return encryptionService.encrypt(attribute);
        } catch (Exception e) {
            logger.error("Encryption failed during convertToDatabaseColumn: {}", e.getMessage(), e);
            // Handle encryption failure: re-throw, or return a specific marker, or null.
            // Re-throwing will typically roll back the transaction.
            throw new RuntimeException("Failed to encrypt data for database persistence.", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return dbData; // Return null or empty string as is
        }
        if (encryptionService == null) {
            logger.error("CryptoConverter: EncryptionService is not initialized. Cannot decrypt data.");
            throw new IllegalStateException("EncryptionService not available for CryptoConverter.");
        }
        try {
            return encryptionService.decrypt(dbData);
        } catch (Exception e) {
            logger.error("Decryption failed during convertToEntityAttribute: {}", e.getMessage(), e);
            // Handle decryption failure. Re-throwing will typically prevent entity loading or cause issues.
            // Depending on the application's requirements, you might return null, a marker string,
            // or throw a custom exception.
            throw new RuntimeException("Failed to decrypt data from database.", e);
        }
    }
}
