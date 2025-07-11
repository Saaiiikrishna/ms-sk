package com.mysillydreams.orderapi.service;

import com.mysillydreams.orderapi.dto.CachedResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service("redisIdempotencyService")
public class RedisIdempotencyService implements IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(RedisIdempotencyService.class);
    private static final String LOCK_KEY_PREFIX = "lock:idempotency:";
    private static final long LOCK_TTL_SECONDS = 10; // Short TTL for the lock itself

    private final RedisTemplate<String, Object> redisTemplate;
    private final ValueOperations<String, Object> valueOps;

    public RedisIdempotencyService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.valueOps = redisTemplate.opsForValue();
    }

    @Override
    public Optional<ResponseEntity<Object>> getCachedResponse(String idempotencyKey) {
        try {
            CachedResponse cachedDto = (CachedResponse) valueOps.get(idempotencyKey);
            if (cachedDto != null) {
                log.debug("Cache hit for idempotency key: {}", idempotencyKey);
                // Reconstruct ResponseEntity from CachedResponse DTO
                HttpHeaders httpHeaders = cachedDto.getHttpHeaders();
                HttpStatus httpStatus = cachedDto.getHttpStatus();
                // The body in CachedResponse is byte[]. ResponseEntity can take Object.
                // The GenericJackson2JsonRedisSerializer should handle deserializing it back to a common type if it was JSON,
                // but here we stored raw bytes. The IdempotencyFilter handles the body as byte[] when caching.
                return Optional.of(new ResponseEntity<>(cachedDto.getBody(), httpHeaders, httpStatus));
            }
            log.debug("Cache miss for idempotency key: {}", idempotencyKey);
            return Optional.empty();
        } catch (Exception e) {
            log.error("Error retrieving cached response for key {}: {}", idempotencyKey, e.getMessage(), e);
            return Optional.empty();
        }
    }

    @Override
    public void cacheResponse(String idempotencyKey, ResponseEntity<Object> response, long ttlMinutes) {
        if (ttlMinutes <= 0) {
            log.debug("TTL is zero or negative, not caching response for key: {}", idempotencyKey);
            redisTemplate.delete(idempotencyKey); // Remove if exists
            return;
        }

        try {
            // Convert ResponseEntity to CachedResponse DTO
            CachedResponse cachedDto = new CachedResponse();
            cachedDto.setHttpStatus(response.getStatusCode());
            cachedDto.setHttpHeaders(response.getHeaders());

            // Body handling: IdempotencyFilter now caches byte[]
            if (response.getBody() instanceof byte[]) {
                cachedDto.setBody((byte[]) response.getBody());
            } else if (response.getBody() != null) {
                // This case should ideally not happen if filter always caches byte[]
                // For safety, log a warning. Consider serializing to byte[] if necessary.
                log.warn("Response body is not byte[] for key {}. Type: {}", idempotencyKey, response.getBody().getClass().getName());
                // Potentially serialize here using ObjectMapper if that's the convention
                // For now, assuming filter provides byte[]
                cachedDto.setBody(null); // Or handle appropriately
            } else {
                cachedDto.setBody(null);
            }

            valueOps.set(idempotencyKey, cachedDto, Duration.ofMinutes(ttlMinutes));
            log.debug("Cached response for idempotency key: {} with TTL: {} minutes", idempotencyKey, ttlMinutes);
        } catch (Exception e) {
            log.error("Error caching response for key {}: {}", idempotencyKey, e.getMessage(), e);
        }
    }

    @Override
    public boolean tryLock(String idempotencyKey) {
        String lockKey = LOCK_KEY_PREFIX + idempotencyKey;
        try {
            // SETNX operation: Set if Not Exists. Returns true if key was set (lock acquired).
            Boolean acquired = valueOps.setIfAbsent(lockKey, "locked", LOCK_TTL_SECONDS, TimeUnit.SECONDS);
            if (acquired != null && acquired) {
                log.debug("Lock acquired for key: {}", idempotencyKey);
                return true;
            }
            log.debug("Lock NOT acquired for key: {} (already locked or error)", idempotencyKey);
            return false;
        } catch (Exception e) {
            log.error("Error trying to acquire lock for key {}: {}", idempotencyKey, e.getMessage(), e);
            return false; // Fail safe
        }
    }

    @Override
    public void unlock(String idempotencyKey) {
        String lockKey = LOCK_KEY_PREFIX + idempotencyKey;
        try {
            redisTemplate.delete(lockKey);
            log.debug("Lock released for key: {}", idempotencyKey);
        } catch (Exception e) {
            log.error("Error releasing lock for key {}: {}", idempotencyKey, e.getMessage(), e);
        }
    }
}
