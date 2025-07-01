package com.mysillydreams.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mysillydreams.auth.config.BaseControllerIntegrationTest;
import com.mysillydreams.auth.controller.dto.LoginRequest;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.Jwt;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;


import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS) // Ensure containers are reset if needed between test classes
@EmbeddedKafka(partitions = 1, topics = {"auth.events"}, brokerProperties = {"listeners=PLAINTEXT://localhost:9095", "port=9095"})
public class AuthControllerLoginIntegrationTest extends BaseControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${jwt.secret}")
    private String configuredJwtSecret;


    @Test
    void login_withValidUserFromKeycloak_shouldReturnServiceJwt() throws Exception {
        LoginRequest loginRequest = new LoginRequest(NORMAL_USER, NORMAL_PASSWORD); // Defined in test-realm.json

        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.tokenType", is("Bearer")))
                .andExpect(jsonPath("$.expiresIn", notNullValue()))
                .andReturn();

        String responseString = result.getResponse().getContentAsString();
        Map<String, Object> responseMap = objectMapper.readValue(responseString, Map.class);
        String accessToken = (String) responseMap.get("accessToken");

        assertThat(accessToken).isNotNull();

        // Validate the service-generated JWT structure and claims
        // We need the actual key used by JwtTokenProvider. The one from application-test.yml
        SecretKey hmacKey = io.jsonwebtoken.security.Keys.hmacShaKeyFor(configuredJwtSecret.getBytes(StandardCharsets.UTF_8));

        @SuppressWarnings({"rawtypes", "unchecked"}) // Jwts.parser() returns raw Jwt
        Jwt<JwsHeader, Claims> parsedJwt = Jwts.parserBuilder()
                .setSigningKey(hmacKey)
                .build()
                .parseClaimsJws(accessToken);

        Claims claims = parsedJwt.getBody();
        assertThat(claims.getSubject()).isEqualTo(NORMAL_USER);
        assertThat(claims.get("roles", String.class)).contains("ROLE_USER"); // From test-realm.json mapping
        assertThat(claims.getExpiration()).isNotNull();
        assertThat(claims.getIssuedAt()).isNotNull();
    }

    @Test
    void login_withAdminUserFromKeycloak_shouldReturnServiceJwtWithAdminRole() throws Exception {
        LoginRequest loginRequest = new LoginRequest(ADMIN_USER, ADMIN_PASSWORD); // Defined in test-realm.json

        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andReturn();

        String responseString = result.getResponse().getContentAsString();
        Map<String, Object> responseMap = objectMapper.readValue(responseString, Map.class);
        String accessToken = (String) responseMap.get("accessToken");

        SecretKey hmacKey = io.jsonwebtoken.security.Keys.hmacShaKeyFor(configuredJwtSecret.getBytes(StandardCharsets.UTF_8));
        @SuppressWarnings({"rawtypes", "unchecked"})
        Jwt<JwsHeader, Claims> parsedJwt = Jwts.parserBuilder().setSigningKey(hmacKey).build().parseClaimsJws(accessToken);
        Claims claims = parsedJwt.getBody();

        assertThat(claims.getSubject()).isEqualTo(ADMIN_USER);
        assertThat(claims.get("roles", String.class)).contains("ROLE_ADMIN");
        assertThat(claims.get("roles", String.class)).contains("ROLE_USER");
    }


    @Test
    void login_withInvalidKeycloakUser_shouldReturnUnauthorized() throws Exception {
        LoginRequest loginRequest = new LoginRequest("unknownuser", "wrongpassword");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest))
                        .with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error", is("Invalid credentials"))); // Or "Authentication failed"
    }
}
