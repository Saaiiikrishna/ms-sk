package com.mysillydreams.payment.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.event.CircuitBreakerOnStateTransitionEvent;
import io.github.resilience4j.core.EventConsumer;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.retry.event.RetryOnRetryEvent;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class Resilience4jLoggingConsumer {

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;

    @PostConstruct
    public void registerEventConsumers() {
        circuitBreakerRegistry.getAllCircuitBreakers()
                .forEach(cb -> cb.getEventPublisher()
                        .onStateTransition(createCircuitBreakerStateTransitionConsumer(cb.getName())));

        retryRegistry.getAllRetries()
                .forEach(retry -> retry.getEventPublisher()
                        .onRetry(createRetryConsumer(retry.getName())));
    }

    private EventConsumer<CircuitBreakerOnStateTransitionEvent> createCircuitBreakerStateTransitionConsumer(String cbName) {
        return event -> {
            log.info("CircuitBreaker '{}': State transition from {} to {}. Failure rate: {}%, Buffered calls: {}, Failed calls: {}, Slow calls: {}",
                    cbName,
                    event.getStateTransition().getFromState(),
                    event.getStateTransition().getToState(),
                    // Metrics available when transitioning to OPEN or if CB is time-based and window ends
                    // These might not be available for all transitions, hence the check.
                    getMetricsSafely(cbName, CircuitBreaker.Metrics::getFailureRate, -1f),
                    getMetricsSafely(cbName, CircuitBreaker.Metrics::getNumberOfBufferedCalls, -1),
                    getMetricsSafely(cbName, CircuitBreaker.Metrics::getNumberOfFailedCalls, -1),
                    getMetricsSafely(cbName, CircuitBreaker.Metrics::getNumberOfSlowCalls, -1)
            );
            if (event.getStateTransition().getToState() == CircuitBreaker.State.OPEN) {
                log.warn("CircuitBreaker '{}' has opened due to high failure rate or slow calls.", cbName);
            } else if (event.getStateTransition().getToState() == CircuitBreaker.State.CLOSED) {
                log.info("CircuitBreaker '{}' has closed. Service is operational again.", cbName);
            } else if (event.getStateTransition().getToState() == CircuitBreaker.State.HALF_OPEN) {
                log.info("CircuitBreaker '{}' is now HALF_OPEN. Test calls will be permitted.", cbName);
            }
        };
    }

    private EventConsumer<RetryOnRetryEvent> createRetryConsumer(String retryName) {
        return event -> {
            log.warn("Retry '{}': Attempt #{} due to exception: {}. Last message: {}",
                    retryName,
                    event.getNumberOfRetryAttempts(),
                    event.getLastThrowable() != null ? event.getLastThrowable().getClass().getName() : "N/A",
                    event.getLastThrowable() != null ? event.getLastThrowable().getMessage() : "N/A"
            );
            // Log more details if needed, e.g., event.getLastThrowable() for stack trace if debugging
        };
    }

    // Helper to safely access metrics that might not be available for a CB if it hasn't recorded enough calls yet.
    private <T> T getMetricsSafely(String cbName, java.util.function.Function<CircuitBreaker.Metrics, T> metricsExtractor, T defaultValue) {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(cbName);
        if (cb != null) {
            try {
                return metricsExtractor.apply(cb.getMetrics());
            } catch (Exception e) {
                // This can happen if metrics are not yet available (e.g. not enough calls in sliding window)
                log.trace("Could not retrieve metric for CircuitBreaker '{}': {}", cbName, e.getMessage());
            }
        }
        return defaultValue;
    }
}
