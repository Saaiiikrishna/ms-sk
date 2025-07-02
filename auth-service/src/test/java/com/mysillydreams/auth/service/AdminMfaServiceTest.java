package com.mysillydreams.auth.service;

import com.mysillydreams.auth.domain.AdminMfaConfig;
import com.mysillydreams.auth.repository.AdminMfaConfigRepository;
import dev.samstevens.totp.exceptions.QrGenerationException;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.QrGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;


import jakarta.persistence.EntityNotFoundException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminMfaServiceTest {

    @Mock
    private AdminMfaConfigRepository mockAdminMfaConfigRepository;

    // We can @Spy some components of TOTP library if we want to verify interactions with them,
    // but for basic tests, we can trust they work and focus on our service's logic.
    // For this test, we'll use real instances of TOTP utilities as they are part of the service's internal construction.
    // @Spy private SecretGenerator secretGenerator = new DefaultSecretGenerator(64);
    // @Spy private QrGenerator qrGenerator = new ZxingPngQrGenerator();
    // @Spy private CodeVerifier codeVerifier = new DefaultCodeVerifier(new DefaultCodeGenerator(), new SystemTimeProvider());

    @InjectMocks
    private AdminMfaService adminMfaService;

    private UUID testAdminUserId;
    private String testAdminUsername;

    @BeforeEach
    void setUp() {
        testAdminUserId = UUID.randomUUID();
        testAdminUsername = "testadmin";
        // Set mfaIssuerName via reflection as it's @Value injected
        ReflectionTestUtils.setField(adminMfaService, "mfaIssuerName", "TestApp");
    }

    @Test
    void generateMfaSetup_newUser_createsConfigAndReturnsSetupResponse() {
        when(mockAdminMfaConfigRepository.findByUserId(testAdminUserId)).thenReturn(Optional.empty());
        when(mockAdminMfaConfigRepository.save(any(AdminMfaConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        AdminMfaService.MfaSetupResponse response = adminMfaService.generateMfaSetup(testAdminUserId, testAdminUsername);

        assertNotNull(response);
        assertNotNull(response.getRawSecret());
        assertTrue(response.getRawSecret().length() >= 32); // DefaultSecretGenerator(64) produces longer base32 string
        assertNotNull(response.getQrCodeDataUri());
        assertTrue(response.getQrCodeDataUri().startsWith("data:image/png;base64,"));

        ArgumentCaptor<AdminMfaConfig> configCaptor = ArgumentCaptor.forClass(AdminMfaConfig.class);
        verify(mockAdminMfaConfigRepository).save(configCaptor.capture());
        AdminMfaConfig savedConfig = configCaptor.getValue();
        assertEquals(testAdminUserId, savedConfig.getUserId());
        assertEquals(response.getRawSecret(), savedConfig.getEncryptedTotpSecret()); // Storing raw for now, converter would encrypt
        assertFalse(savedConfig.isMfaEnabled());
    }

    @Test
    void generateMfaSetup_existingUser_updatesConfigAndReturnsSetupResponse() {
        AdminMfaConfig existingConfig = new AdminMfaConfig(testAdminUserId, "oldEncryptedSecret");
        existingConfig.setMfaEnabled(true); // Should be reset to false

        when(mockAdminMfaConfigRepository.findByUserId(testAdminUserId)).thenReturn(Optional.of(existingConfig));
        when(mockAdminMfaConfigRepository.save(any(AdminMfaConfig.class))).thenReturn(existingConfig);

        AdminMfaService.MfaSetupResponse response = adminMfaService.generateMfaSetup(testAdminUserId, testAdminUsername);

        assertNotNull(response);
        // Raw secret should be new
        assertNotEquals("oldEncryptedSecret", response.getRawSecret()); // Assuming converter doesn't change it for this check

        ArgumentCaptor<AdminMfaConfig> configCaptor = ArgumentCaptor.forClass(AdminMfaConfig.class);
        verify(mockAdminMfaConfigRepository).save(configCaptor.capture());
        AdminMfaConfig savedConfig = configCaptor.getValue();
        assertEquals(response.getRawSecret(), savedConfig.getEncryptedTotpSecret());
        assertFalse(savedConfig.isMfaEnabled()); // Reset to false
    }

    @Test
    void generateMfaSetup_qrGenerationFails_throwsMfaOperationException() throws QrGenerationException {
        // Temporarily use a mock QrGenerator that throws an exception
        QrGenerator failingQrGenerator = mock(QrGenerator.class);
        when(failingQrGenerator.getImageUri(any(QrData.class))).thenThrow(new QrGenerationException("QR fail"));
        ReflectionTestUtils.setField(adminMfaService, "qrGenerator", failingQrGenerator);

        when(mockAdminMfaConfigRepository.findByUserId(testAdminUserId)).thenReturn(Optional.empty());
        when(mockAdminMfaConfigRepository.save(any(AdminMfaConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        AdminMfaService.MfaOperationException ex = assertThrows(AdminMfaService.MfaOperationException.class, () -> {
            adminMfaService.generateMfaSetup(testAdminUserId, testAdminUsername);
        });
        assertTrue(ex.getMessage().contains("Failed to generate QR code"));

        // Reset the qrGenerator if other tests depend on the real one from @InjectMocks
         ReflectionTestUtils.setField(adminMfaService, "qrGenerator", new dev.samstevens.totp.qr.ZxingPngQrGenerator());
    }


    @Test
    void verifyAndEnableMfa_validOtp_enablesMfaAndReturnsTrue() {
        // This test is more complex as it involves the actual TOTP algorithm.
        // We need a known secret and then generate a valid OTP for it.
        String knownSecret = new DefaultSecretGenerator(64).generate();
        AdminMfaConfig mfaConfig = new AdminMfaConfig(testAdminUserId, knownSecret); // Converter would encrypt
        mfaConfig.setMfaEnabled(false);

        when(mockAdminMfaConfigRepository.findByUserId(testAdminUserId)).thenReturn(Optional.of(mfaConfig));
        when(mockAdminMfaConfigRepository.save(any(AdminMfaConfig.class))).thenReturn(mfaConfig);

        // Generate a valid OTP for the knownSecret
        TimeProvider timeProvider = new SystemTimeProvider();
        DefaultCodeGenerator codeGenerator = new DefaultCodeGenerator();
        String validOtp = null;
        try {
            validOtp = codeGenerator.generate(knownSecret, timeProvider.getTime() / 30);
        } catch (CodeGenerationException e) {
            fail("Code generation failed for test setup: " + e.getMessage());
        }

        boolean result = adminMfaService.verifyAndEnableMfa(testAdminUserId, validOtp);

        assertTrue(result);
        assertTrue(mfaConfig.isMfaEnabled());
        verify(mockAdminMfaConfigRepository).save(mfaConfig);
    }

    @Test
    void verifyAndEnableMfa_invalidOtp_returnsFalseAndMfaNotEnabled() {
        String knownSecret = new DefaultSecretGenerator(64).generate();
        AdminMfaConfig mfaConfig = new AdminMfaConfig(testAdminUserId, knownSecret);
        mfaConfig.setMfaEnabled(false);

        when(mockAdminMfaConfigRepository.findByUserId(testAdminUserId)).thenReturn(Optional.of(mfaConfig));
        // No save expected if OTP is invalid

        boolean result = adminMfaService.verifyAndEnableMfa(testAdminUserId, "invalidOtp");

        assertFalse(result);
        assertFalse(mfaConfig.isMfaEnabled()); // Should remain false
        verify(mockAdminMfaConfigRepository, never()).save(any(AdminMfaConfig.class));
    }

    @Test
    void verifyAndEnableMfa_configNotFound_throwsEntityNotFoundException() {
        when(mockAdminMfaConfigRepository.findByUserId(testAdminUserId)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> {
            adminMfaService.verifyAndEnableMfa(testAdminUserId, "anyOtp");
        });
    }

    @Test
    void verifyAndEnableMfa_noSecretInConfig_throwsMfaOperationException() {
        AdminMfaConfig mfaConfigNoSecret = new AdminMfaConfig(testAdminUserId, null); // Null secret
        mfaConfigNoSecret.setEncryptedTotpSecret(null); // Explicitly null for clarity
        when(mockAdminMfaConfigRepository.findByUserId(testAdminUserId)).thenReturn(Optional.of(mfaConfigNoSecret));

        AdminMfaService.MfaOperationException ex = assertThrows(AdminMfaService.MfaOperationException.class, () -> {
            adminMfaService.verifyAndEnableMfa(testAdminUserId, "anyOtp");
        });
        assertTrue(ex.getMessage().contains("No TOTP secret configured"));
    }


    @Test
    void verifyOtp_loginScenario_validOtpAndEnabled_returnsTrue() {
        String knownSecret = new DefaultSecretGenerator(64).generate();
        AdminMfaConfig mfaConfig = new AdminMfaConfig(testAdminUserId, knownSecret);
        mfaConfig.setMfaEnabled(true); // MFA is enabled
        when(mockAdminMfaConfigRepository.findByUserId(testAdminUserId)).thenReturn(Optional.of(mfaConfig));

        String validOtp = null;
        try {
            validOtp = new DefaultCodeGenerator().generate(knownSecret, new SystemTimeProvider().getTime() / 30);
        } catch (CodeGenerationException e) { fail(e); }

        assertTrue(adminMfaService.verifyOtp(testAdminUserId, validOtp));
    }

    @Test
    void verifyOtp_loginScenario_mfaNotEnabled_returnsFalse() {
        String knownSecret = new DefaultSecretGenerator(64).generate();
        AdminMfaConfig mfaConfig = new AdminMfaConfig(testAdminUserId, knownSecret);
        mfaConfig.setMfaEnabled(false); // MFA is NOT enabled
        when(mockAdminMfaConfigRepository.findByUserId(testAdminUserId)).thenReturn(Optional.of(mfaConfig));

        // OTP itself doesn't matter here, as mfaEnabled is false
        assertFalse(adminMfaService.verifyOtp(testAdminUserId, "123456"));
    }

    @Test
    void verifyOtp_loginScenario_configNotFound_returnsFalse() {
        when(mockAdminMfaConfigRepository.findByUserId(testAdminUserId)).thenReturn(Optional.empty());
        assertFalse(adminMfaService.verifyOtp(testAdminUserId, "123456"));
    }

    @Test
    void verifyOtp_loginScenario_nullOtp_returnsFalse() {
        // No need to mock repository for this, as it should fail before DB access
        assertFalse(adminMfaService.verifyOtp(testAdminUserId, null));
    }
}
