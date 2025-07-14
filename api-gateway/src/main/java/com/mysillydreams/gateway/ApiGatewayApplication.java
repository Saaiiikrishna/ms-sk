package com.mysillydreams.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * API Gateway Application for MySillyDreams Platform
 * 
 * This gateway serves as the single entry point for all client requests,
 * providing routing, security, rate limiting, and monitoring capabilities.
 * 
 * Features:
 * - Service discovery with Eureka
 * - Configuration management with Zookeeper
 * - JWT-based authentication and authorization
 * - Rate limiting with Redis
 * - Circuit breaker pattern
 * - Distributed tracing with Zipkin
 * - CORS support for frontend applications
 */
@SpringBootApplication
@EnableDiscoveryClient
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
