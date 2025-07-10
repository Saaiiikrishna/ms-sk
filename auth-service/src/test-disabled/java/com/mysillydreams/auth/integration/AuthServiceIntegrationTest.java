package com.mysillydreams.auth.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mysillydreams.auth.controller.dto.JwtResponse;
import com.mysillydreams.auth.controller.dto.LoginRequest;
import com.mysillydreams.auth.domain.AdminMfaConfig;
import com.mysillydreams.auth.repository.AdminMfaConfigRepository;
import com.mysillydreams.auth.service.AdminMfaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the Auth Service.
 * Tests the complete authentication flow including MFA.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "keycloak.enabled=false",
    "app.simple-encryption.secret-key=TestEncryptionKeyForIntegrationTests123456789!",
    "jwt.secret=TestJwtSecretKeyForIntegrationTestsMinimum256BitsLong123456789!",
    "app.internal-api.secret-key=TestInternalApiKeyForIntegrationTests123456789!"
})
@Transactional
public class AuthServiceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AdminMfaConfigRepository adminMfaConfigRepository;

    @Autowired
    private AdminMfaService adminMfaService;

    private UUID testAdminUserId;
    private String testAdminUsername;

    @BeforeEach
    void setUp() {
        testAdminUserId = UUID.randomUUID();
        testAdminUsername = "test-admin";
        
        // Clean up any existing test data
        adminMfaConfigRepository.deleteAll();
    }

    @Test
    void healthEndpoint_shouldReturnOk() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void loginEndpoint_withoutCredentials_shouldReturnBadRequest() throws Exception {
        LoginRequest loginRequest = new LoginRequest("", "");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest))
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void validateToken_withoutToken_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(get("/auth/validate"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Authorization header is missing or malformed."));
    }

    @Test
    void validateToken_withInvalidToken_shouldReturnUnauthorized() throws Exception {
        mockMvc.perform(get("/auth/validate")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("invalid"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminMfaSetup_shouldGenerateSecretAndQrCode() throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/admin/mfa/setup")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rawSecret").exists())
                .andExpect(jsonPath("$.qrCodeDataUri").exists())
                .andReturn();

        String responseContent = result.getResponse().getContentAsString();
        AdminMfaService.MfaSetupResponse response = objectMapper.readValue(responseContent, AdminMfaService.MfaSetupResponse.class);
        
        assertThat(response.getRawSecret()).isNotEmpty();
        assertThat(response.getQrCodeDataUri()).startsWith("data:image/png;base64,");
    }

    @Test
    void rateLimiting_shouldBlockExcessiveLoginAttempts() throws Exception {
        LoginRequest loginRequest = new LoginRequest("testuser", "wrongpassword");
        String requestBody = objectMapper.writeValueAsString(loginRequest);

        // Make multiple failed login attempts (exceeding the rate limit)
        for (int i = 0; i < 6; i++) {
            if (i < 5) {
                // First 5 attempts should be processed (even if they fail due to auth)
                mockMvc.perform(post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                                .with(csrf()))
                        .andExpect(status().isUnauthorized()); // Auth failure, not rate limit
            } else {
                // 6th attempt should be rate limited
                mockMvc.perform(post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                                .with(csrf()))
                        .andExpect(status().isTooManyRequests())
                        .andExpect(jsonPath("$.error").value("Too many login attempts. Please try again later."));
            }
        }
    }

    @Test
    void securityHeaders_shouldBePresent() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("X-XSS-Protection", "1; mode=block"))
                .andExpect(header().exists("Content-Security-Policy"))
                .andExpect(header().exists("Strict-Transport-Security"))
                .andExpect(header().exists("Referrer-Policy"));
    }

    @Test
    void internalEndpoint_withoutApiKey_shouldReturnUnauthorized() throws Exception {
        String requestBody = objectMapper.writeValueAsString(Map.of(
            "adminUserId", testAdminUserId.toString(),
            "adminUsername", testAdminUsername
        ));

        mockMvc.perform(post("/internal/auth/provision-mfa-setup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void internalEndpoint_withValidApiKey_shouldProvisionMfa() throws Exception {
        String requestBody = objectMapper.writeValueAsString(Map.of(
            "adminUserId", testAdminUserId.toString(),
            "adminUsername", testAdminUsername
        ));

        mockMvc.perform(post("/internal/auth/provision-mfa-setup")
                        .header("X-Internal-API-Key", "TestInternalApiKeyForIntegrationTests123456789!")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rawSecret").exists())
                .andExpect(jsonPath("$.qrCodeDataUri").exists());
    }

    @Test
    void mfaWorkflow_shouldCompleteSuccessfully() throws Exception {
        // 1. Generate MFA setup
        AdminMfaService.MfaSetupResponse setupResponse = adminMfaService.generateMfaSetup(testAdminUserId, testAdminUsername);
        assertThat(setupResponse.getRawSecret()).isNotEmpty();

        // 2. Verify that MFA is not yet enabled
        AdminMfaConfig config = adminMfaConfigRepository.findByUserId(testAdminUserId).orElse(null);
        assertThat(config).isNotNull();
        assertThat(config.isMfaEnabled()).isFalse();

        // 3. Generate a valid OTP (this would normally be done by an authenticator app)
        // For testing, we'll use the TOTP library directly
        String validOtp = generateValidOtp(setupResponse.getRawSecret());

        // 4. Verify and enable MFA
        boolean verificationResult = adminMfaService.verifyAndEnableMfa(testAdminUserId, validOtp);
        assertThat(verificationResult).isTrue();

        // 5. Verify that MFA is now enabled
        config = adminMfaConfigRepository.findByUserId(testAdminUserId).orElse(null);
        assertThat(config).isNotNull();
        assertThat(config.isMfaEnabled()).isTrue();

        // 6. Test OTP verification for login
        String newValidOtp = generateValidOtp(setupResponse.getRawSecret());
        boolean loginOtpResult = adminMfaService.verifyOtp(testAdminUserId, newValidOtp);
        assertThat(loginOtpResult).isTrue();
    }

    private String generateValidOtp(String secret) {
        // Use the same TOTP library that the service uses
        dev.samstevens.totp.code.DefaultCodeGenerator codeGenerator = new dev.samstevens.totp.code.DefaultCodeGenerator();
        dev.samstevens.totp.time.SystemTimeProvider timeProvider = new dev.samstevens.totp.time.SystemTimeProvider();
        
        try {
            return codeGenerator.generate(secret, timeProvider.getTime() / 30);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate OTP for testing", e);
        }
    }
}
