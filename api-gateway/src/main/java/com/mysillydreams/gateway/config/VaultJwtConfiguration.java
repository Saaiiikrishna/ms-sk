package com.mysillydreams.gateway.config;

import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.vault.core.VaultOperations;
import org.springframework.vault.support.VaultResponse;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * Vault-based JWT configuration for API Gateway.
 * Loads JWT secrets from Vault for token validation.
 */
@Configuration
@ConditionalOnProperty(name = "spring.cloud.vault.enabled", havingValue = "true")
public class VaultJwtConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(VaultJwtConfiguration.class);
    
    private static final String JWT_SECRET_PATH = "secret/jwt";
    private static final String JWT_SECRET_KEY = "signing-key";
    private static final String JWT_REFRESH_SECRET_KEY = "refresh-signing-key";
    
    private final VaultOperations vaultOperations;
    
    @Value("${jwt.vault.path:secret/jwt}")
    private String jwtVaultPath;

    public VaultJwtConfiguration(VaultOperations vaultOperations) {
        this.vaultOperations = vaultOperations;
    }

    /**
     * Primary JWT secret key from Vault for token validation
     */
    @Bean
    @Primary
    public SecretKey jwtSecretKey() {
        return getSecretKeyFromVault(JWT_SECRET_KEY, "JWT signing key");
    }

    /**
     * Refresh token secret key from Vault
     */
    @Bean
    public SecretKey jwtRefreshSecretKey() {
        return getSecretKeyFromVault(JWT_REFRESH_SECRET_KEY, "JWT refresh token signing key");
    }

    /**
     * JWT secret string for backward compatibility
     */
    @Bean
    public String jwtSecretString() {
        try {
            VaultResponse response = vaultOperations.read(jwtVaultPath);
            if (response != null && response.getData() != null) {
                String secret = (String) response.getData().get(JWT_SECRET_KEY);
                if (secret != null && !secret.isEmpty()) {
                    logger.info("JWT secret loaded from Vault at path: {}", jwtVaultPath);
                    return secret;
                }
            }
            
            throw new IllegalStateException("JWT secret not found in Vault at path: " + jwtVaultPath);
            
        } catch (Exception e) {
            logger.error("Failed to load JWT secret from Vault: {}", e.getMessage());
            throw new IllegalStateException("Unable to configure JWT secret from Vault", e);
        }
    }

    private SecretKey getSecretKeyFromVault(String keyName, String description) {
        try {
            VaultResponse response = vaultOperations.read(jwtVaultPath);
            if (response != null && response.getData() != null) {
                String secret = (String) response.getData().get(keyName);
                if (secret != null && !secret.isEmpty()) {
                    logger.info("{} loaded from Vault", description);
                    return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
                }
            }
            
            throw new IllegalStateException(description + " not found in Vault at path: " + jwtVaultPath);
            
        } catch (Exception e) {
            logger.error("Failed to load {} from Vault: {}", description, e.getMessage());
            throw new IllegalStateException("Unable to configure " + description + " from Vault", e);
        }
    }
}
