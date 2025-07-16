package com.mysillydreams.auth.config;

import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * JWT Configuration for environments without Vault.
 * Provides JWT secret keys using configuration properties or generated keys.
 */
@Configuration
public class JwtConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(JwtConfiguration.class);

    @Value("${jwt.secret:}")
    private String jwtSecret;

    /**
     * Primary JWT secret key
     */
    @Bean
    @Primary
    public SecretKey jwtSecretKey() {
        if (jwtSecret == null || jwtSecret.length() < 64) {
            logger.warn("JWT secret is weak or not configured. Using a generated key. THIS IS NOT SAFE FOR PRODUCTION.");
            SecretKey generatedKey = Keys.secretKeyFor(SignatureAlgorithm.HS512);
            logger.info("Generated JWT secret key for development");
            return generatedKey;
        } else {
            logger.info("Using configured JWT secret key");
            return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Separate refresh token secret key
     */
    @Bean
    public SecretKey jwtRefreshSecretKey() {
        if (jwtSecret == null || jwtSecret.length() < 64) {
            logger.warn("JWT refresh secret is weak or not configured. Using a generated key. THIS IS NOT SAFE FOR PRODUCTION.");
            SecretKey generatedKey = Keys.secretKeyFor(SignatureAlgorithm.HS512);
            logger.info("Generated JWT refresh secret key for development");
            return generatedKey;
        } else {
            logger.info("Using configured JWT refresh secret key");
            return Keys.hmacShaKeyFor((jwtSecret + "_refresh").getBytes(StandardCharsets.UTF_8));
        }
    }
}
