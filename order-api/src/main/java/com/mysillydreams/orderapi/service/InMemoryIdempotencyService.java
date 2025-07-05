package com.mysillydreams.orderapi.service;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

// For simplicity in this example. A distributed cache like Redis is recommended for production.
@Service("inMemoryIdempotencyService") // Qualify bean name
public class InMemoryIdempotencyService implements IdempotencyService {

    private static class CacheEntry {
        final ResponseEntity<Object> response;
        final long expiryTime;

        CacheEntry(ResponseEntity<Object> response, long ttlMinutes) {
            this.response = response;
            this.expiryTime = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(ttlMinutes);
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expiryTime;
        }
    }

    private final ConcurrentHashMap<String, CacheEntry> responseCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Lock> keyLocks = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public InMemoryIdempotencyService() {
        // Periodically clean up expired entries and locks
        scheduler.scheduleAtFixedRate(this::cleanupExpiredEntries, 10, 10, TimeUnit.MINUTES);
    }

    @Override
    public Optional<ResponseEntity<Object>> getCachedResponse(String idempotencyKey) {
        CacheEntry entry = responseCache.get(idempotencyKey);
        if (entry != null) {
            if (entry.isExpired()) {
                responseCache.remove(idempotencyKey, entry); // Remove if expired
                return Optional.empty();
            }
            return Optional.of(entry.response);
        }
        return Optional.empty();
    }

    @Override
    public void cacheResponse(String idempotencyKey, ResponseEntity<Object> response, long ttlMinutes) {
        if (ttlMinutes <= 0) {
            // If TTL is zero or negative, don't cache or remove if exists
            responseCache.remove(idempotencyKey);
            return;
        }
        CacheEntry entry = new CacheEntry(response, ttlMinutes);
        responseCache.put(idempotencyKey, entry);
    }

    @Override
    public boolean tryLock(String idempotencyKey) {
        // Get or create a lock for the key.
        // computeIfAbsent ensures that only one lock is created per key.
        Lock lock = keyLocks.computeIfAbsent(idempotencyKey, k -> new ReentrantLock());
        return lock.tryLock();
    }

    @Override
    public void unlock(String idempotencyKey) {
        Lock lock = keyLocks.get(idempotencyKey);
        if (lock != null && ((ReentrantLock) lock).isHeldByCurrentThread()) {
            lock.unlock();
            // Optional: Consider removing the lock from keyLocks if it's no longer needed
            // to prevent memory growth, but be careful about race conditions if a new request
            // for the same key arrives just as it's being removed.
            // For this simple version, we rely on periodic cleanup.
        }
    }

    private void cleanupExpiredEntries() {
        responseCache.forEach((key, entry) -> {
            if (entry.isExpired()) {
                responseCache.remove(key, entry);
            }
        });
        // Cleanup locks that are no longer associated with any cached response
        // and are not currently locked. This is a simplification.
        // A more robust solution would track lock usage more carefully.
        keyLocks.forEach((key, lock) -> {
            if (!responseCache.containsKey(key)) {
                 // Try to acquire the lock to see if it's free, then release it.
                 // This is not perfectly safe, as the lock could be acquired by another thread
                 // between the check and the remove operation.
                 // For a production system, a more sophisticated lock management or distributed lock manager is needed.
                ReentrantLock reentrantLock = (ReentrantLock) lock;
                if (!reentrantLock.isLocked()) {
                    keyLocks.remove(key);
                }
            }
        });
    }

    // Ensure scheduler is shut down when the bean is destroyed
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
