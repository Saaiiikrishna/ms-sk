package com.mysillydreams.payment.config;

import io.github.resilience4j.circuitbreaker.event.CircuitBreakerOnStateTransitionEvent;
import io.github.resilience4j.retry.event.RetryOnRetryEvent;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ResilienceEventLogger {

    private static final Logger logger = LoggerFactory.getLogger(ResilienceEventLogger.class);

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;

    @PostConstruct
    public void registerEventListeners() {
        // Subscribe to all CircuitBreaker state transitions
        circuitBreakerRegistry.getAllCircuitBreakers()
            .forEach(cb -> cb.getEventPublisher()
                .onStateTransition(this::onStateTransition));

        // Subscribe to all Retry retry events
        retryRegistry.getAllRetries()
            .forEach(retry -> retry.getEventPublisher()
                .onRetry(this::onRetry));
    }

    private void onStateTransition(CircuitBreakerOnStateTransitionEvent ev) {
        // Note: ev.getFailureRate() and ev.getNumberOfBufferedCalls() might return -1.0 or -1
        // if the event is for a transition where these metrics are not applicable or not yet calculated.
        // The previous Resilience4jLoggingConsumer had a helper for this; direct access is also fine.
        logger.warn("[CB:{}] State {} → {} (failureRate={}%, bufferedCalls={})",
            ev.getCircuitBreakerName(),
            ev.getStateTransition().getFromState(),
            ev.getStateTransition().getToState(),
            String.format("%.2f", ev.getFailureRate()), // Format float for logging
            ev.getNumberOfBufferedCalls()
        );
    }

    private void onRetry(RetryOnRetryEvent ev) {
        logger.info("[Retry:{}] attempt #{}, lastError={}",
            ev.getName(),
            ev.getNumberOfRetryAttempts(),
            ev.getLastThrowable() != null
                ? ev.getLastThrowable().getMessage()
                : "<none>"
        );
    }
}
