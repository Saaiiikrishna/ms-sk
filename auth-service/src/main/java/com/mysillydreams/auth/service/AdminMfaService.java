package com.mysillydreams.auth.service;

import com.mysillydreams.auth.domain.AdminMfaConfig;
import com.mysillydreams.auth.repository.AdminMfaConfigRepository;
import dev.samstevens.totp.code.*;
import dev.samstevens.totp.exceptions.QrGenerationException;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.QrGenerator;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.util.UUID;

@Service
public class AdminMfaService {

    private static final Logger logger = LoggerFactory.getLogger(AdminMfaService.class);

    private final AdminMfaConfigRepository adminMfaConfigRepository;
    // SimpleEncryptionService is used by TotpSecretConverter, not directly here for TOTP generation/verification.
    // The raw secret is needed for TOTP library.

    @Value("${app.mfa.issuer-name:MySillyDreamsPlatform}")
    private String mfaIssuerName;

    private final SecretGenerator secretGenerator;
    private final QrGenerator qrGenerator;
    private final CodeVerifier codeVerifier;

    @Autowired
    public AdminMfaService(AdminMfaConfigRepository adminMfaConfigRepository) {
        this.adminMfaConfigRepository = adminMfaConfigRepository;
        this.secretGenerator = new DefaultSecretGenerator(64); // 64 bytes for a strong secret
        this.qrGenerator = new ZxingPngQrGenerator();
        // Default HashingAlgorithm is SHA1, TimePeriod is 30, CodeDigits is 6
        this.codeVerifier = new DefaultCodeVerifier(new DefaultCodeGenerator(), new SystemTimeProvider());
    }

    public static class MfaSetupResponse {
        private String rawSecret; // For manual entry
        private String qrCodeDataUri; // For QR code display

        public MfaSetupResponse(String rawSecret, String qrCodeDataUri) {
            this.rawSecret = rawSecret;
            this.qrCodeDataUri = qrCodeDataUri;
        }
        public String getRawSecret() { return rawSecret; }
        public String getQrCodeDataUri() { return qrCodeDataUri; }
    }


    @Transactional
    public MfaSetupResponse generateMfaSetup(UUID adminUserId, String adminUsername) {
        logger.info("Generating MFA setup for admin User ID: {}", adminUserId);

        String rawTotpSecret = secretGenerator.generate();

        AdminMfaConfig mfaConfig = adminMfaConfigRepository.findByUserId(adminUserId)
                .orElse(new AdminMfaConfig(adminUserId, "")); // Create if not exists, secret will be set

        // The raw secret is stored encrypted by the TotpSecretConverter
        mfaConfig.setEncryptedTotpSecret(rawTotpSecret); // Converter will encrypt this on save
        mfaConfig.setMfaEnabled(false); // Ensure it's disabled until verified
        adminMfaConfigRepository.save(mfaConfig);
        logger.info("Stored (encrypted) TOTP secret for admin User ID: {} and marked MFA as not yet enabled.", adminUserId);

        QrData qrData = new QrData.Builder()
                .label(adminUsername) // Typically user's email or username
                .secret(rawTotpSecret)
                .issuer(mfaIssuerName)
                .algorithm(HashingAlgorithm.SHA1) // Default, can be changed
                .digits(6)                         // Default
                .period(30)                        // Default
                .build();

        String qrCodeDataUri;
        try {
            qrCodeDataUri = qrGenerator.getImageUri(qrData);
        } catch (QrGenerationException e) {
            logger.error("Failed to generate QR code for admin User ID {}: {}", adminUserId, e.getMessage(), e);
            throw new MfaOperationException("Failed to generate QR code for MFA setup.", e);
        }

        logger.info("MFA setup generated for admin User ID: {}. QR Code URI created.", adminUserId);
        return new MfaSetupResponse(rawTotpSecret, qrCodeDataUri);
    }

    @Transactional
    public boolean verifyAndEnableMfa(UUID adminUserId, String otp) {
        logger.info("Attempting to verify and enable MFA for admin User ID: {}", adminUserId);
        AdminMfaConfig mfaConfig = adminMfaConfigRepository.findByUserId(adminUserId)
                .orElseThrow(() -> {
                    logger.warn("MFA config not found for admin User ID {} during verification.", adminUserId);
                    return new EntityNotFoundException("MFA configuration not found for user. Please run setup first.");
                });

        // The encryptedTotpSecret field in mfaConfig will be automatically decrypted by TotpSecretConverter
        // when the entity is loaded by JPA if the converter is correctly applied.
        // So, mfaConfig.getEncryptedTotpSecret() here would return the *plaintext* secret.
        String rawTotpSecret = mfaConfig.getEncryptedTotpSecret(); // This is actually the decrypted secret due to converter

        if (rawTotpSecret == null || rawTotpSecret.isEmpty()) {
            logger.error("No TOTP secret found for admin User ID {} during verification. Possible data issue or setup not completed.", adminUserId);
            throw new MfaOperationException("No TOTP secret configured for user. Please run setup first.");
        }

        if (codeVerifier.isValidCode(rawTotpSecret, otp)) {
            mfaConfig.setMfaEnabled(true);
            adminMfaConfigRepository.save(mfaConfig);
            logger.info("MFA successfully verified and enabled for admin User ID: {}", adminUserId);
            return true;
        } else {
            logger.warn("Invalid OTP provided for admin User ID: {}", adminUserId);
            return false;
        }
    }

    /**
     * Verifies a TOTP code against the user's stored secret.
     * Does not change enabled status. Used during login.
     */
    public boolean verifyOtp(UUID adminUserId, String otp) {
        if (otp == null || otp.trim().isEmpty()) return false;

        AdminMfaConfig mfaConfig = adminMfaConfigRepository.findByUserId(adminUserId).orElse(null);
        if (mfaConfig == null || !mfaConfig.isMfaEnabled() || mfaConfig.getEncryptedTotpSecret() == null) {
            logger.warn("MFA not configured, not enabled, or no secret found for admin User ID {} during OTP verification.", adminUserId);
            return false; // Or throw if admin is expected to always have MFA config once admin role is present
        }
        // getEncryptedTotpSecret() returns decrypted secret due to converter
        return codeVerifier.isValidCode(mfaConfig.getEncryptedTotpSecret(), otp);
    }


    public static class MfaOperationException extends RuntimeException {
        public MfaOperationException(String message) {
            super(message);
        }
        public MfaOperationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
