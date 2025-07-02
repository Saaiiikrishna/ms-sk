package com.mysillydreams.auth.repository;

import com.mysillydreams.auth.config.AuthIntegrationTestBase; // A base class for Auth-Service integration tests
import com.mysillydreams.auth.converter.TotpSecretConverter;
import com.mysillydreams.auth.domain.AdminMfaConfig;
import com.mysillydreams.auth.service.SimpleEncryptionService; // Needed for Spring context
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;


import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Using @SpringBootTest to get the full context including SimpleEncryptionService and its config.
// @DataJpaTest might be too restrictive if SimpleEncryptionService isn't easily injectable
// into the static context of TotpSecretConverter.
@SpringBootTest
// @ContextConfiguration(initializers = AuthIntegrationTestBase.Initializer.class) // If AuthIntegrationTestBase exists and sets up DB
// For now, assume application-test.yml for Auth-Service handles DB (e.g. H2 or separate Testcontainers PG setup for Auth)
// We need a test properties file that provides 'app.simple-encryption.secret-key'.
// Let's assume AuthIntegrationTestBase provides this, or it's in application-test.yml for auth-service.
public class AdminMfaConfigRepositoryIntegrationTest extends AuthIntegrationTestBase { // Extend a base test class

    @Autowired
    private AdminMfaConfigRepository adminMfaConfigRepository;

    @Autowired
    private SimpleEncryptionService simpleEncryptionService; // Autowire to manually encrypt for comparison

    @Autowired
    private TotpSecretConverter totpSecretConverter; // To ensure its static field is set by Spring

    @BeforeEach
    void setUp() {
        // Ensure converter has the service. Spring should do this via @Component and @Autowired setter.
        // Forcing it here to be absolutely sure in test context if issues arise.
        totpSecretConverter.setEncryptionService(simpleEncryptionService);
        adminMfaConfigRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        adminMfaConfigRepository.deleteAll();
    }


    @Test
    void saveAndFindByUserId_withEncryptedSecret_shouldWork() {
        UUID userId = UUID.randomUUID();
        String rawSecret = "MyRawTotpSecret12345"; // Min length for TOTP secrets often 32 chars Base32

        AdminMfaConfig config = new AdminMfaConfig();
        config.setUserId(userId);
        // The converter will encrypt this rawSecret before saving
        config.setEncryptedTotpSecret(rawSecret);
        config.setMfaEnabled(false);

        AdminMfaConfig savedConfig = adminMfaConfigRepository.saveAndFlush(config);
        assertThat(savedConfig).isNotNull();
        assertThat(savedConfig.getUserId()).isEqualTo(userId);

        // Fetch from DB
        // entityManager.clear(); // If using EntityManager to ensure fresh load
        Optional<AdminMfaConfig> foundOpt = adminMfaConfigRepository.findByUserId(userId);
        assertThat(foundOpt).isPresent();
        AdminMfaConfig foundConfig = foundOpt.get();

        // The getEncryptedTotpSecret() method on the entity will return the DECRYPTED secret
        // because of the @Convert annotation on the field.
        assertThat(foundConfig.getEncryptedTotpSecret()).isEqualTo(rawSecret);
        assertThat(foundConfig.isMfaEnabled()).isFalse();

        // To verify it's actually encrypted in DB, we'd need to query raw data or
        // encrypt the rawSecret manually and compare with what might be stored if we could access it pre-conversion.
        // This test primarily verifies that what we save (plaintext) is what we get back (plaintext)
        // meaning encryption and decryption via the converter worked.
    }

    @Test
    void save_mfaEnabledTrue_shouldPersist() {
        UUID userId = UUID.randomUUID();
        String rawSecret = "AnotherSecretKeyForMfa";
        AdminMfaConfig config = new AdminMfaConfig(userId, rawSecret); // Constructor sets mfaEnabled=false
        config.setMfaEnabled(true); // Explicitly set to true

        adminMfaConfigRepository.saveAndFlush(config);

        Optional<AdminMfaConfig> foundOpt = adminMfaConfigRepository.findByUserId(userId);
        assertThat(foundOpt).isPresent();
        assertThat(foundOpt.get().isMfaEnabled()).isTrue();
        assertThat(foundOpt.get().getEncryptedTotpSecret()).isEqualTo(rawSecret); // Decrypted
    }

    @Test
    void findByUserId_notFound_shouldReturnEmpty() {
        Optional<AdminMfaConfig> foundOpt = adminMfaConfigRepository.findByUserId(UUID.randomUUID());
        assertThat(foundOpt).isNotPresent();
    }
}
```

I need an `AuthIntegrationTestBase.java` similar to `UserIntegrationTestBase.java` but for the Auth-Service context. This base class would set up Testcontainers for PostgreSQL if Auth-Service uses its own DB, and provide necessary properties like `app.simple-encryption.secret-key`.

`auth-service/src/test/java/com/mysillydreams/auth/config/AuthIntegrationTestBase.java`:
