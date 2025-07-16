package com.mysillydreams.auth.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mysillydreams.auth.controller.dto.JwtResponse;
import com.mysillydreams.auth.controller.dto.LoginRequest;
import com.mysillydreams.auth.domain.RefreshToken;
import com.mysillydreams.auth.repository.RefreshTokenRepository;
import com.mysillydreams.auth.service.RefreshTokenService;
import com.mysillydreams.auth.util.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the complete authentication flow.
 * Tests the end-to-end authentication process including JWT generation,
 * refresh token management, and security validations.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.cloud.vault.enabled=false",
    "jwt.secret=TestSecretKeyForIntegrationTestsMinimum256BitsLong123456789!",
    "jwt.expiration-ms=3600000",
    "jwt.refresh-expiration-hours=168"
})
@Transactional
public class AuthenticationFlowIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void testCompleteAuthenticationFlow() throws Exception {
        // Test data
        String username = "testuser@example.com";
        String password = "testpassword";
        
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername(username);
        loginRequest.setPassword(password);

        // Step 1: Login and get tokens
        MvcResult loginResult = mockMvc.perform(post("/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn();

        String loginResponse = loginResult.getResponse().getContentAsString();
        JwtResponse jwtResponse = objectMapper.readValue(loginResponse, JwtResponse.class);

        // Verify JWT token structure
        assertThat(jwtResponse.getAccessToken()).isNotNull();
        assertThat(jwtResponse.getRefreshToken()).isNotNull();
        assertThat(jwtTokenProvider.validateToken(jwtResponse.getAccessToken())).isTrue();

        // Verify refresh token is stored in database
        Optional<RefreshToken> storedRefreshToken = refreshTokenRepository.findByToken(jwtResponse.getRefreshToken());
        assertThat(storedRefreshToken).isPresent();
        assertThat(storedRefreshToken.get().getUsername()).isEqualTo(username);
        assertThat(storedRefreshToken.get().isValid()).isTrue();

        // Step 2: Use refresh token to get new access token
        String refreshTokenRequest = "{\"refreshToken\":\"" + jwtResponse.getRefreshToken() + "\"}";
        
        MvcResult refreshResult = mockMvc.perform(post("/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(refreshTokenRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andReturn();

        String refreshResponse = refreshResult.getResponse().getContentAsString();
        JwtResponse newJwtResponse = objectMapper.readValue(refreshResponse, JwtResponse.class);

        // Verify new tokens are different
        assertThat(newJwtResponse.getAccessToken()).isNotEqualTo(jwtResponse.getAccessToken());
        assertThat(newJwtResponse.getRefreshToken()).isNotEqualTo(jwtResponse.getRefreshToken());

        // Verify old refresh token is revoked
        Optional<RefreshToken> oldRefreshToken = refreshTokenRepository.findByToken(jwtResponse.getRefreshToken());
        assertThat(oldRefreshToken).isPresent();
        assertThat(oldRefreshToken.get().isRevoked()).isTrue();

        // Verify new refresh token is valid
        Optional<RefreshToken> newRefreshToken = refreshTokenRepository.findByToken(newJwtResponse.getRefreshToken());
        assertThat(newRefreshToken).isPresent();
        assertThat(newRefreshToken.get().isValid()).isTrue();

        // Step 3: Validate the new access token
        mockMvc.perform(post("/validate")
                .header("Authorization", "Bearer " + newJwtResponse.getAccessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.username").value(username));
    }

    @Test
    void testInvalidRefreshToken() throws Exception {
        String invalidRefreshTokenRequest = "{\"refreshToken\":\"invalid-token\"}";
        
        mockMvc.perform(post("/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidRefreshTokenRequest))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid or expired refresh token"));
    }

    @Test
    void testExpiredRefreshToken() throws Exception {
        // Create an expired refresh token
        String username = "testuser@example.com";
        UUID userId = UUID.randomUUID();
        
        RefreshToken expiredToken = new RefreshToken(
            "expired-token", 
            username, 
            userId, 
            LocalDateTime.now().minusHours(1) // Expired 1 hour ago
        );
        refreshTokenRepository.save(expiredToken);

        String expiredRefreshTokenRequest = "{\"refreshToken\":\"expired-token\"}";
        
        mockMvc.perform(post("/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(expiredRefreshTokenRequest))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid or expired refresh token"));
    }

    @Test
    void testRevokedRefreshToken() throws Exception {
        // Create a revoked refresh token
        String username = "testuser@example.com";
        UUID userId = UUID.randomUUID();
        
        RefreshToken revokedToken = new RefreshToken(
            "revoked-token", 
            username, 
            userId, 
            LocalDateTime.now().plusHours(24)
        );
        revokedToken.revoke();
        refreshTokenRepository.save(revokedToken);

        String revokedRefreshTokenRequest = "{\"refreshToken\":\"revoked-token\"}";
        
        mockMvc.perform(post("/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(revokedRefreshTokenRequest))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid or expired refresh token"));
    }

    @Test
    void testConcurrentSessionLimiting() throws Exception {
        String username = "testuser@example.com";
        UUID userId = UUID.randomUUID();

        // Create multiple refresh tokens for the same user
        for (int i = 0; i < 6; i++) { // Assuming max concurrent sessions is 5
            RefreshToken token = new RefreshToken(
                "token-" + i, 
                username, 
                userId, 
                LocalDateTime.now().plusHours(24)
            );
            refreshTokenRepository.save(token);
        }

        // Generate a new refresh token (should trigger cleanup)
        RefreshToken newToken = refreshTokenService.generateRefreshToken(username, userId, null);

        // Verify that old tokens were revoked
        List<RefreshToken> validTokens = refreshTokenService.getUserValidTokens(username);
        assertThat(validTokens.size()).isLessThanOrEqualTo(5); // Max concurrent sessions
    }

    @Test
    void testSecurityHeaders() throws Exception {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("testuser@example.com");
        loginRequest.setPassword("testpassword");

        mockMvc.perform(post("/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(header().exists("X-Content-Type-Options"))
                .andExpect(header().exists("X-Frame-Options"))
                .andExpect(header().exists("Cache-Control"));
    }

    @Test
    void testRateLimitingHeaders() throws Exception {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("testuser@example.com");
        loginRequest.setPassword("testpassword");

        // Make multiple requests to test rate limiting
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(header().exists("X-RateLimit-Remaining"));
        }
    }
}
