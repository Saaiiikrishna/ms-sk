package com.mysillydreams.orderapi.service;

import com.mysillydreams.orderapi.config.RedisConfig; // Ensure RedisConfig is loaded
import com.mysillydreams.orderapi.dto.CachedResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = {RedisIdempotencyService.class, RedisConfig.class}) // Load only necessary config
@ActiveProfiles("test") // Ensure application-test.yml is loaded (for potential embedded redis properties)
// The it.ozimov.spring-boot-starter-redis-embedded should auto-configure an embedded server
// if spring.redis.embedded=true is set or by default if no other Redis config is found.
// No explicit properties needed here if the starter works as expected.
class RedisIdempotencyServiceTest {

    @Autowired
    private RedisIdempotencyService redisIdempotencyService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private String testKey;

    @BeforeEach
    void setUp() {
        testKey = "idempotency-" + UUID.randomUUID().toString();
        // Clean up the key before each test to ensure independence
        redisTemplate.delete(testKey);
        redisTemplate.delete("lock:idempotency:" + testKey);
    }

    @Test
    void tryLock_whenNotLocked_acquiresLockAndReturnsTrue() {
        assertTrue(redisIdempotencyService.tryLock(testKey));
        // Verify lock exists in Redis
        assertThat(redisTemplate.hasKey("lock:idempotency:" + testKey)).isTrue();
    }

    @Test
    void tryLock_whenAlreadyLocked_returnsFalse() {
        redisIdempotencyService.tryLock(testKey); // First lock
        assertFalse(redisIdempotencyService.tryLock(testKey)); // Second attempt should fail
    }

    @Test
    void unlock_releasesLock() {
        redisIdempotencyService.tryLock(testKey);
        assertTrue(redisTemplate.hasKey("lock:idempotency:" + testKey));

        redisIdempotencyService.unlock(testKey);
        assertThat(redisTemplate.hasKey("lock:idempotency:" + testKey)).isFalse();
    }

    @Test
    void cacheResponse_and_getCachedResponse_worksCorrectly() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("X-Custom-Header", "TestValue");

        Map<String, String> bodyMap = Collections.singletonMap("orderId", UUID.randomUUID().toString());
        // Simulating how IdempotencyFilter would prepare the body as byte[]
        byte[] bodyBytes = "{\"orderId\":\"some-uuid\"}".getBytes(StandardCharsets.UTF_8);

        ResponseEntity<Object> originalResponse = new ResponseEntity<>(bodyBytes, headers, HttpStatus.ACCEPTED);
        long ttlMinutes = 1;

        // Cache the response
        redisIdempotencyService.cacheResponse(testKey, originalResponse, ttlMinutes);

        // Retrieve the cached response
        Optional<ResponseEntity<Object>> cachedResponseOpt = redisIdempotencyService.getCachedResponse(testKey);

        assertTrue(cachedResponseOpt.isPresent());
        ResponseEntity<Object> cachedResponse = cachedResponseOpt.get();

        // Assertions
        assertEquals(HttpStatus.ACCEPTED, cachedResponse.getStatusCode());
        assertTrue(cachedResponse.getHeaders().containsKey("X-Custom-Header"));
        assertEquals("TestValue", cachedResponse.getHeaders().getFirst("X-Custom-Header"));
        assertEquals(MediaType.APPLICATION_JSON_VALUE, cachedResponse.getHeaders().getContentType().toString());

        // Compare body content
        assertArrayEquals(bodyBytes, (byte[]) cachedResponse.getBody());
    }

    @Test
    void getCachedResponse_whenKeyDoesNotExist_returnsEmpty() {
        Optional<ResponseEntity<Object>> response = redisIdempotencyService.getCachedResponse("non-existent-key");
        assertFalse(response.isPresent());
    }

    @Test
    void cacheResponse_withZeroTtl_doesNotCache() {
        ResponseEntity<Object> response = new ResponseEntity<>("test body".getBytes(), HttpStatus.OK);
        redisIdempotencyService.cacheResponse(testKey, response, 0);

        Optional<ResponseEntity<Object>> cachedResponse = redisIdempotencyService.getCachedResponse(testKey);
        assertFalse(cachedResponse.isPresent());
    }

    @Test
    void cacheResponse_withNegativeTtl_doesNotCache() {
        ResponseEntity<Object> response = new ResponseEntity<>("test body".getBytes(), HttpStatus.OK);
        redisIdempotencyService.cacheResponse(testKey, response, -1);

        Optional<ResponseEntity<Object>> cachedResponse = redisIdempotencyService.getCachedResponse(testKey);
        assertFalse(cachedResponse.isPresent());
    }
}
