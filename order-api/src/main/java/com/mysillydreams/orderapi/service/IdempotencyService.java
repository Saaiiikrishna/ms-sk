package com.mysillydreams.orderapi.service;

import org.springframework.http.ResponseEntity;

import java.util.Optional;

public interface IdempotencyService {

    /**
     * Checks if a request with the given idempotency key has been processed.
     *
     * @param idempotencyKey The idempotency key from the request header.
     * @return An Optional containing the cached ResponseEntity if the key exists and is valid,
     *         otherwise an empty Optional.
     */
    Optional<ResponseEntity<Object>> getCachedResponse(String idempotencyKey);

    /**
     * Caches the response for a given idempotency key.
     *
     * @param idempotencyKey The idempotency key.
     * @param response       The ResponseEntity to cache.
     * @param ttlMinutes     Time-to-live for the cache entry in minutes.
     */
    void cacheResponse(String idempotencyKey, ResponseEntity<Object> response, long ttlMinutes);

    /**
     * Marks an idempotency key as currently being processed.
     * This is to prevent concurrent requests with the same key from processing simultaneously.
     *
     * @param idempotencyKey The idempotency key.
     * @return true if the lock was acquired, false otherwise (e.g., key already locked).
     */
    boolean tryLock(String idempotencyKey);

    /**
     * Releases the lock for an idempotency key.
     * Should be called after processing is complete or if an error occurs.
     *
     * @param idempotencyKey The idempotency key.
     */
    void unlock(String idempotencyKey);
}
