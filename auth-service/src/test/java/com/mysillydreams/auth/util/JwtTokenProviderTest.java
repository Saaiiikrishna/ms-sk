package com.mysillydreams.auth.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class JwtTokenProviderTest {

    @InjectMocks
    private JwtTokenProvider jwtTokenProvider;

    private final String mockSecret = "TestSecretKeyForJwtTokenProviderTestMinimumLengthRequirementOkay"; // 64 chars
    private final long mockExpirationMs = 3600000; // 1 hour
    private SecretKey actualKey;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtSecretString", mockSecret);
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtExpirationInMs", mockExpirationMs);
        jwtTokenProvider.init(); // Manually call init to set up the key based on mockSecret
        actualKey = Keys.hmacShaKeyFor(mockSecret.getBytes(StandardCharsets.UTF_8));
    }

    private Authentication createMockAuthentication(String username, String... roles) {
        List<GrantedAuthority> authorities = Arrays.stream(roles)
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
        return new UsernamePasswordAuthenticationToken(username, "password", authorities);
    }

    @Test
    void testGenerateToken() {
        Authentication authentication = createMockAuthentication("testUser", "ROLE_USER", "ROLE_VIEWER");
        String token = jwtTokenProvider.generateToken(authentication);

        assertNotNull(token);

        Claims claims = Jwts.parserBuilder().setSigningKey(actualKey).build().parseClaimsJws(token).getBody();
        assertEquals("testUser", claims.getSubject());
        assertTrue(claims.get("roles", String.class).contains("ROLE_USER"));
        assertTrue(claims.get("roles", String.class).contains("ROLE_VIEWER"));
        assertNotNull(claims.getIssuedAt());
        assertNotNull(claims.getExpiration());
        assertTrue(claims.getExpiration().after(new Date()));
        assertTrue(claims.getExpiration().getTime() - claims.getIssuedAt().getTime() <= mockExpirationMs + 1000); // allow slight diff
    }

    @Test
    void testValidateToken_validToken() {
        Authentication authentication = createMockAuthentication("testUser", "ROLE_USER");
        String token = jwtTokenProvider.generateToken(authentication);

        assertTrue(jwtTokenProvider.validateToken(token));
    }

    @Test
    void testValidateToken_invalidSignature() {
        Authentication authentication = createMockAuthentication("testUser", "ROLE_USER");
        String token = jwtTokenProvider.generateToken(authentication);

        // Tamper with the token or use a different key for validation
        SecretKey wrongKey = Keys.secretKeyFor(SignatureAlgorithm.HS512);
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtSecretKey", wrongKey); // Temporarily use wrong key

        assertFalse(jwtTokenProvider.validateToken(token));

        ReflectionTestUtils.setField(jwtTokenProvider, "jwtSecretKey", actualKey); // Reset to correct key
    }

    @Test
    void testValidateToken_nullOrEmpty() {
        assertFalse(jwtTokenProvider.validateToken(null));
        assertFalse(jwtTokenProvider.validateToken(""));
        assertFalse(jwtTokenProvider.validateToken("  "));
    }


    @Test
    void testValidateToken_expiredToken() throws InterruptedException {
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtExpirationInMs", 1L); // 1 ms expiration
        jwtTokenProvider.init(); // Re-init with short expiration if key generation depends on it (it doesn't here but good practice)

        Authentication authentication = createMockAuthentication("testUser", "ROLE_USER");
        String token = jwtTokenProvider.generateToken(authentication);

        Thread.sleep(50); // Wait for token to expire

        assertFalse(jwtTokenProvider.validateToken(token));

        // Reset expiration for other tests
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtExpirationInMs", mockExpirationMs);
        jwtTokenProvider.init();
    }

    @Test
    void testGetAuthentication() {
        Authentication originalAuthentication = createMockAuthentication("testUser", "ROLE_USER", "ROLE_ADMIN");
        String token = jwtTokenProvider.generateToken(originalAuthentication);

        Authentication retrievedAuthentication = jwtTokenProvider.getAuthentication(token);

        assertNotNull(retrievedAuthentication);
        assertEquals("testUser", retrievedAuthentication.getName());
        Collection<? extends GrantedAuthority> expectedAuthorities = originalAuthentication.getAuthorities();
        assertTrue(retrievedAuthentication.getAuthorities().containsAll(expectedAuthorities));
        assertTrue(expectedAuthorities.containsAll(retrievedAuthentication.getAuthorities()));
    }

    @Test
    void testGetAuthentication_emptyRolesInToken() {
        // Generate a token with an empty roles string
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + mockExpirationMs);
        String tokenWithEmptyRoles = Jwts.builder()
                .setSubject("testUserWithEmptyRoles")
                .claim("roles", "") // Empty roles claim
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(actualKey, SignatureAlgorithm.HS512)
                .compact();

        Authentication retrievedAuthentication = jwtTokenProvider.getAuthentication(tokenWithEmptyRoles);
        assertNotNull(retrievedAuthentication);
        assertEquals("testUserWithEmptyRoles", retrievedAuthentication.getName());
        assertTrue(retrievedAuthentication.getAuthorities().isEmpty());
    }


    @Test
    void testGetUsernameFromToken() {
        Authentication authentication = createMockAuthentication("testUser", "ROLE_USER");
        String token = jwtTokenProvider.generateToken(authentication);
        assertEquals("testUser", jwtTokenProvider.getUsernameFromToken(token));
    }

    @Test
    void testGetExpiryDateFromToken() {
        Authentication authentication = createMockAuthentication("testUser", "ROLE_USER");
        String token = jwtTokenProvider.generateToken(authentication);
        long expiryTime = jwtTokenProvider.getExpiryDateFromToken(token);
        assertTrue(expiryTime > System.currentTimeMillis());
        assertTrue(expiryTime <= System.currentTimeMillis() + mockExpirationMs + 1000); // allow slight diff
    }

    @Test
    void init_usesDefaultKeyIfSecretIsTooShort() {
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtSecretString", "short");
        jwtTokenProvider.init(); // Call init to test the fallback logic
        // We can't easily assert the exact key, but we can check it's not null
        // and perhaps that a warning was logged (though testing logs is more complex)
        assertNotNull(ReflectionTestUtils.getField(jwtTokenProvider, "jwtSecretKey"));
    }

    @Test
    void init_usesDefaultKeyIfSecretIsNull() {
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtSecretString", null);
        jwtTokenProvider.init();
        assertNotNull(ReflectionTestUtils.getField(jwtTokenProvider, "jwtSecretKey"));
    }
}
