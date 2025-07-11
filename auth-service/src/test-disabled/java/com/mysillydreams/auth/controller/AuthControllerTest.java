package com.mysillydreams.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mysillydreams.auth.controller.dto.JwtResponse;
import com.mysillydreams.auth.controller.dto.LoginRequest;
import com.mysillydreams.auth.controller.dto.TokenRefreshRequest;
import com.mysillydreams.auth.service.PasswordRotationService;
import com.mysillydreams.auth.util.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;


import jakarta.ws.rs.NotFoundException; // Ensure this is the JAX-RS one if service throws it
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;


// Import SecurityConfig to apply security filters to the MockMvc tests
// This is important for testing @PreAuthorize and authentication flows.
// We also need to provide mocks for Keycloak related beans if SecurityConfig tries to load them.
// For WebMvcTest, it's often simpler to mock services and not load full security config if it's complex.
// However, for @PreAuthorize, some security context is needed.
// For now, let's assume @WebMvcTest provides enough context for controller-level security annotations.
// If Keycloak-specific parts of SecurityConfig cause issues, we might need a custom test configuration.
import com.mysillydreams.auth.config.SecurityConfig; // Assuming this can be loaded or parts mocked

@WebMvcTest(AuthController.class)
// Import SecurityConfig to make sure @PreAuthorize annotations are processed.
// This might require mocking Keycloak specific beans if SecurityConfig depends on them heavily.
// For simpler controller tests, sometimes full SecurityConfig is not imported.
// Let's try importing it. We'll need to mock Keycloak related beans if they are wired up in SecurityConfig.
// Since AuthenticationManager is already a mock bean, this might work.
@Import(SecurityConfig.class) //This will apply security filters. Ensure Keycloak specific beans in SecurityConfig are also mocked if not provided by Spring Boot auto-config for tests.
public class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthenticationManager authenticationManager; // Spring Security's AuthenticationManager

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private PasswordRotationService passwordRotationService;

    // We need to mock KeycloakSpringBootConfigResolver if SecurityConfig @Imports it via @Bean
    // Or ensure our test slice doesn't try to fully initialize Keycloak related parts if not needed for controller logic.
    // @MockBean for KeycloakConfigResolver if SecurityConfig tries to create it.
    // private org.keycloak.adapters.KeycloakConfigResolver keycloakConfigResolver;


    @Autowired
    private ObjectMapper objectMapper;

    private LoginRequest loginRequest;
    private String sampleJwt;
    private Authentication mockAuthentication;

    @BeforeEach
    void setUp() {
        loginRequest = new LoginRequest("testuser", "password");
        sampleJwt = "sample.jwt.token";
        mockAuthentication = new UsernamePasswordAuthenticationToken(
                "testuser", null, List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }

    @Test
    void login_success() throws Exception {
        given(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .willReturn(mockAuthentication);
        given(jwtTokenProvider.generateToken(mockAuthentication)).willReturn(sampleJwt);
        given(jwtTokenProvider.getExpiryDateFromToken(sampleJwt)).willReturn(System.currentTimeMillis() + 3600000);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest))
                        .with(csrf())) // If CSRF is enabled, which it is by default in tests unless disabled in SecurityConfig
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", is(sampleJwt)))
                .andExpect(jsonPath("$.tokenType", is("Bearer")))
                .andExpect(jsonPath("$.expiresIn", notNullValue()));
    }

    @Test
    void login_badCredentials() throws Exception {
        given(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .willThrow(new BadCredentialsException("Invalid credentials"));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest))
                        .with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error", is("Invalid credentials")));
    }

    @Test
    void login_invalidRequest_noUsername() throws Exception {
        LoginRequest invalidLogin = new LoginRequest(null, "password");
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidLogin))
                        .with(csrf()))
                .andExpect(status().isBadRequest()); // Assuming @Valid and global exception handler for MethodArgumentNotValidException
    }


    @Test
    void refreshToken_success() throws Exception {
        TokenRefreshRequest refreshRequest = new TokenRefreshRequest(sampleJwt);
        String newJwt = "new.sample.jwt.token";

        given(jwtTokenProvider.validateToken(sampleJwt)).willReturn(true);
        given(jwtTokenProvider.getAuthentication(sampleJwt)).willReturn(mockAuthentication);
        given(jwtTokenProvider.generateToken(mockAuthentication)).willReturn(newJwt);
        given(jwtTokenProvider.getExpiryDateFromToken(newJwt)).willReturn(System.currentTimeMillis() + 3600000);


        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", is(newJwt)));
    }

    @Test
    void refreshToken_invalidOldToken() throws Exception {
        TokenRefreshRequest refreshRequest = new TokenRefreshRequest("invalid.token");
        given(jwtTokenProvider.validateToken("invalid.token")).willReturn(false);

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest))
                        .with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error", is("Invalid token for refresh")));
    }

    @Test
    void validateToken_success() throws Exception {
        given(jwtTokenProvider.validateToken(sampleJwt)).willReturn(true);
        given(jwtTokenProvider.getAuthentication(sampleJwt)).willReturn(mockAuthentication);

        mockMvc.perform(get("/auth/validate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sampleJwt)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("valid")))
                .andExpect(jsonPath("$.user", is("testuser")));
    }

    @Test
    void validateToken_invalid() throws Exception {
        given(jwtTokenProvider.validateToken(sampleJwt)).willReturn(false);

        mockMvc.perform(get("/auth/validate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sampleJwt)
                        .with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is("invalid")));
    }

    @Test
    void validateToken_missingHeader() throws Exception {
        mockMvc.perform(get("/auth/validate")
                        .with(csrf())) // CSRF might still be relevant depending on setup
                .andExpect(status().isBadRequest()) // Or 401 if security chain processes it first
                .andExpect(jsonPath("$.error", is("Authorization header is missing or malformed.")));
    }


    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"}) // Simulate an admin user being logged in
    void rotatePassword_success() throws Exception {
        UUID userIdToRotate = UUID.randomUUID();
        doNothing().when(passwordRotationService).rotatePassword(userIdToRotate);

        mockMvc.perform(post("/auth/password-rotate")
                        .param("userId", userIdToRotate.toString())
                        .with(csrf())) // For POST requests with Spring Security
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("Password rotation process initiated for user " + userIdToRotate)));

        verify(passwordRotationService).rotatePassword(userIdToRotate);
    }

    @Test
    @WithMockUser(username = "user", roles = {"USER"}) // Non-admin user
    void rotatePassword_forbiddenForNonAdmin() throws Exception {
        UUID userIdToRotate = UUID.randomUUID();

        mockMvc.perform(post("/auth/password-rotate")
                        .param("userId", userIdToRotate.toString())
                        .with(csrf()))
                .andExpect(status().isForbidden()); // @PreAuthorize("hasRole('ADMIN')") should deny

        verify(passwordRotationService, never()).rotatePassword(any(UUID.class));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void rotatePassword_userNotFound() throws Exception {
        UUID userIdToRotate = UUID.randomUUID();
        doThrow(new NotFoundException("User not found: " + userIdToRotate))
            .when(passwordRotationService).rotatePassword(userIdToRotate);

        mockMvc.perform(post("/auth/password-rotate")
                        .param("userId", userIdToRotate.toString())
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", is("User not found: " + userIdToRotate)));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void rotatePassword_serviceThrowsRuntimeException() throws Exception {
        UUID userIdToRotate = UUID.randomUUID();
        doThrow(new RuntimeException("Internal service error"))
            .when(passwordRotationService).rotatePassword(userIdToRotate);

        mockMvc.perform(post("/auth/password-rotate")
                        .param("userId", userIdToRotate.toString())
                        .with(csrf()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error", is("An error occurred while initiating password rotation for user " + userIdToRotate)));
    }

    @Test
    // Test without @WithMockUser to simulate anonymous access to a protected endpoint
    void rotatePassword_anonymousAccessForbidden() throws Exception {
        UUID userIdToRotate = UUID.randomUUID();

        mockMvc.perform(post("/auth/password-rotate")
                        .param("userId", userIdToRotate.toString())
                        .with(csrf()))
                .andExpect(status().isUnauthorized()); // Or 403 if anonymous is treated as authenticated without roles
                                                     // Depends on how Spring Security is configured for anonymous users.
                                                     // Given our SecurityConfig, it should be 401 as it requires .authenticated()
                                                     // and then @PreAuthorize checks roles.
    }
}
