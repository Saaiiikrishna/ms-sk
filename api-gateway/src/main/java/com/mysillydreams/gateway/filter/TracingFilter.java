package com.mysillydreams.gateway.filter;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Tracing filter for distributed tracing across gateway
 */
@Component
public class TracingFilter implements GatewayFilter {

    private static final Logger logger = LoggerFactory.getLogger(TracingFilter.class);
    
    private final Tracer tracer;

    public TracingFilter(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        
        // Create a new span for the gateway request
        Span span = tracer.nextSpan()
                .name("gateway.request")
                .tag("http.method", request.getMethod().name())
                .tag("http.path", request.getPath().value())
                .tag("http.url", request.getURI().toString())
                .tag("user.agent", request.getHeaders().getFirst("User-Agent"))
                .start();

        try (Tracer.SpanInScope ws = tracer.withSpan(span)) {
            // Add trace information to request headers
            ServerHttpRequest modifiedRequest = request.mutate()
                    .header("X-Trace-Id", span.context().traceId())
                    .header("X-Span-Id", span.context().spanId())
                    .build();

            return chain.filter(exchange.mutate().request(modifiedRequest).build())
                    .doOnSuccess(aVoid -> {
                        span.tag("http.status_code", String.valueOf(exchange.getResponse().getStatusCode().value()));
                        span.end();
                    })
                    .doOnError(throwable -> {
                        span.tag("error", throwable.getMessage());
                        span.tag("http.status_code", "500");
                        span.end();
                    });
        }
    }
}
