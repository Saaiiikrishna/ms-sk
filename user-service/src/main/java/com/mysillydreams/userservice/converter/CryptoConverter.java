package com.mysillydreams.userservice.converter;

import com.mysillydreams.userservice.service.EncryptionServiceInterface;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * JPA AttributeConverter to automatically encrypt and decrypt String entity attributes.
 * Uses ApplicationContextAware to safely access Spring beans from JPA converter context.
 * This approach eliminates race conditions that can occur with static injection.
 */
@Converter
@Component
public class CryptoConverter implements AttributeConverter<String, String>, ApplicationContextAware {

    private static final Logger logger = LoggerFactory.getLogger(CryptoConverter.class);
    private static ApplicationContext applicationContext;
    private static volatile EncryptionServiceInterface encryptionService;

    @Override
    public void setApplicationContext(ApplicationContext context) throws BeansException {
        CryptoConverter.applicationContext = context;
        logger.info("ApplicationContext set in CryptoConverter");
    }

    // Fallback static setter for backward compatibility and testing
    @Autowired
    public void setEncryptionService(EncryptionServiceInterface service) {
        synchronized (CryptoConverter.class) {
            if (CryptoConverter.encryptionService == null) {
                CryptoConverter.encryptionService = service;
                logger.info("EncryptionService statically injected into CryptoConverter.");
            }
        }
    }

    /**
     * Get EncryptionService instance safely, with fallback to ApplicationContext lookup
     */
    private EncryptionServiceInterface getEncryptionService() {
        if (encryptionService != null) {
            return encryptionService;
        }

        // Double-checked locking for thread safety
        synchronized (CryptoConverter.class) {
            if (encryptionService != null) {
                return encryptionService;
            }

            if (applicationContext != null) {
                try {
                    encryptionService = applicationContext.getBean(EncryptionServiceInterface.class);
                    logger.info("EncryptionService retrieved from ApplicationContext");
                    return encryptionService;
                } catch (Exception e) {
                    logger.error("Failed to get EncryptionService from ApplicationContext: {}", e.getMessage());
                }
            }

            throw new IllegalStateException("EncryptionService not available for CryptoConverter. " +
                "Ensure Spring context is properly initialized and EncryptionService bean exists.");
        }
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return attribute; // Return null or empty string as is
        }

        try {
            EncryptionServiceInterface service = getEncryptionService();
            return service.encrypt(attribute);
        } catch (Exception e) {
            logger.error("Encryption failed during convertToDatabaseColumn: {}", e.getMessage(), e);
            // Re-throwing will typically roll back the transaction, which is desired for encryption failures
            throw new RuntimeException("Failed to encrypt data for database persistence.", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return dbData; // Return null or empty string as is
        }

        try {
            EncryptionServiceInterface service = getEncryptionService();
            return service.decrypt(dbData);
        } catch (Exception e) {
            logger.error("Decryption failed during convertToEntityAttribute: {}", e.getMessage(), e);
            // Re-throwing will prevent entity loading, which is desired for decryption failures
            throw new RuntimeException("Failed to decrypt data from database.", e);
        }
    }
}
