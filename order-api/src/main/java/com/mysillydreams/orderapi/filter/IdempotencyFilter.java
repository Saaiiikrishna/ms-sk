package com.mysillydreams.orderapi.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mysillydreams.orderapi.service.IdempotencyService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1) // Ensure it runs early, but after security filters
public class IdempotencyFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyFilter.class);
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final IdempotencyService idempotencyService;
    private final long cacheTtlMinutes;
    private final ObjectMapper objectMapper = new ObjectMapper(); // For creating error responses
    private final Counter idempotentHitsCounter;
    private final Counter idempotentMissesCounter;
    private final Counter missingIdempotencyKeyCounter;

    // Inject the specific RedisIdempotencyService or the interface if only one impl is expected
    public IdempotencyFilter(IdempotencyService idempotencyService, // Spring will inject RedisIdempotencyService if it's primary or only one
                             @Value("${app.idempotency.cache-ttl-minutes:60}") long cacheTtlMinutes,
                             MeterRegistry meterRegistry) {
        this.idempotencyService = idempotencyService; // Now expects RedisIdempotencyService
        this.cacheTtlMinutes = cacheTtlMinutes;
        this.idempotentHitsCounter = Counter.builder("idempotency.filter.hits")
            .description("Number of requests served from idempotency cache")
            .register(meterRegistry);
        this.idempotentMissesCounter = Counter.builder("idempotency.filter.misses")
            .description("Number of requests processed and potentially cached (cache miss or new request)")
            .register(meterRegistry);
        this.missingIdempotencyKeyCounter = Counter.builder("idempotency.filter.missing_key")
            .description("Number of requests rejected due to missing Idempotency-Key header")
            .register(meterRegistry);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // Only apply to POST /orders requests as specified in the guide
        if (!"POST".equalsIgnoreCase(request.getMethod()) || !request.getRequestURI().startsWith("/orders")) {
            filterChain.doFilter(request, response);
            return;
        }

        String idempotencyKey = request.getHeader(IDEMPOTENCY_KEY_HEADER);

        if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            log.warn("Idempotency-Key header is missing for POST request to {}", request.getRequestURI());
            missingIdempotencyKeyCounter.increment();
            sendErrorResponse(response, HttpStatus.BAD_REQUEST, "Idempotency-Key header is missing or empty.");
            return;
        }

        MDC.put("idempotencyKey", idempotencyKey); // Add to MDC

        // Try to acquire a lock for this key to handle concurrent requests
        if (!idempotencyService.tryLock(idempotencyKey)) {
            log.warn("Concurrent request detected for Idempotency-Key: {}", idempotencyKey);
            // HTTP 429 Too Many Requests or 409 Conflict can be used. 409 seems appropriate.
            sendErrorResponse(response, HttpStatus.CONFLICT, "A request with this Idempotency-Key is already being processed.");
            return;
        }

        try {
            Optional<ResponseEntity<Object>> cachedResponseOpt = idempotencyService.getCachedResponse(idempotencyKey);

            if (cachedResponseOpt.isPresent()) {
                idempotentHitsCounter.increment(); // Metric for cache hit
                ResponseEntity<Object> cachedResponse = cachedResponseOpt.get();
                log.info("Returning cached response for Idempotency-Key: {}", idempotencyKey);
                response.setStatus(cachedResponse.getStatusCodeValue());
                cachedResponse.getHeaders().forEach((name, values) ->
                    values.forEach(value -> response.addHeader(name, value))
                );
                // Ensure content type is set, especially for JSON responses
                if (cachedResponse.getHeaders().getContentType() != null) {
                    response.setContentType(cachedResponse.getHeaders().getContentType().toString());
                } else if (cachedResponse.getBody() != null && !(cachedResponse.getBody() instanceof byte[])) {
                     // Default to JSON if not specified and body is not raw bytes
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                }

                if (cachedResponse.getBody() != null) {
                    objectMapper.writeValue(response.getWriter(), cachedResponse.getBody());
                }
                return; // Return cached response
            }

            // Wrap the response to cache it after the request is processed
            ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);
            filterChain.doFilter(request, responseWrapper); // Proceed with the actual request handling

            // After request processing, if successful (e.g., 2xx status codes), cache the response
            int status = responseWrapper.getStatus();
            if (status >= 200 && status < 300) { // Successful response, eligible for caching
                idempotentMissesCounter.increment(); // Metric for cache miss (processed and will be cached)
                // Construct ResponseEntity from the wrapper
                HttpHeaders responseHeaders = new HttpHeaders();
                responseWrapper.getHeaderNames().forEach(headerName ->
                    responseWrapper.getHeaders(headerName).forEach(headerValue ->
                        responseHeaders.add(headerName, headerValue)
                    )
                );

                // Ensure Content-Type is captured correctly
                if (responseWrapper.getContentType() != null) {
                    responseHeaders.setContentType(MediaType.parseMediaType(responseWrapper.getContentType()));
                }


                ResponseEntity<Object> responseToCache;
                // Try to parse body as JSON if it's JSON, otherwise handle as byte array or string
                // For simplicity, we'll cache the raw body bytes if Content-Type suggests it's not text/JSON
                // or if parsing fails. A more robust solution might be needed depending on expected response types.

                // We need to be careful here. The body of the response might be already written.
                // The `ContentCachingResponseWrapper` captures the output.
                // We should cache what was written to the client.
                // The guide implies caching the "request->response mapping".
                // The controller returns ResponseEntity<Map<String, UUID>>.
                // We need to reconstruct this or a similar object to cache.

                // Let's try to deserialize the JSON response body if it's JSON
                // This is tricky because the response body might be complex.
                // For now, let's cache the important parts: status, headers, and body.
                // The body will be cached as a byte array.

                byte[] body = responseWrapper.getContentAsByteArray();
                responseToCache = new ResponseEntity<>(body, responseHeaders, HttpStatus.valueOf(status));

                idempotencyService.cacheResponse(idempotencyKey, responseToCache, cacheTtlMinutes);
                log.info("Cached response for Idempotency-Key: {} with status {}", idempotencyKey, status);
            }
            responseWrapper.copyBodyToResponse(); // IMPORTANT: copy content of wrapper to original response

        } finally {
            idempotencyService.unlock(idempotencyKey); // Always release the lock
            MDC.remove("idempotencyKey"); // Clean up MDC
        }
    }

    private void sendErrorResponse(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        Map<String, String> errorBody = Collections.singletonMap("error", message);
        response.getWriter().write(objectMapper.writeValueAsString(errorBody));
    }

     @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        // Apply the filter only to POST requests to /orders
        return !("POST".equalsIgnoreCase(request.getMethod()) && request.getRequestURI().startsWith("/orders"));
    }
}
