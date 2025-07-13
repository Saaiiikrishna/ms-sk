#!/bin/bash

# Zookeeper Configuration Setup Script
# This script sets up all configuration in Zookeeper for local development

ZOOKEEPER_HOST=${ZOOKEEPER_HOST:-localhost:2181}
ZOOKEEPER_ADMIN_URL=${ZOOKEEPER_ADMIN_URL:-http://localhost:8084}

echo "Setting up Zookeeper configuration for local development..."
echo "Zookeeper Host: $ZOOKEEPER_HOST"
echo "Zookeeper Admin URL: $ZOOKEEPER_ADMIN_URL"

# Wait for Zookeeper to be ready
echo "Waiting for Zookeeper to be ready..."
while ! nc -z localhost 2181; do
  sleep 1
done
echo "Zookeeper is ready!"

# Wait for Zookeeper Admin service to be ready
echo "Waiting for Zookeeper Admin service to be ready..."
while ! curl -f $ZOOKEEPER_ADMIN_URL/health > /dev/null 2>&1; do
  sleep 1
done
echo "Zookeeper Admin service is ready!"

# Function to create configuration
create_config() {
    local path=$1
    local value=$2
    echo "Creating config: $path"
    curl -X POST "$ZOOKEEPER_ADMIN_URL/config" \
         -H "Content-Type: application/json" \
         -d "{\"path\":\"$path\",\"data\":\"$value\"}" \
         -s -o /dev/null
}

# Common configurations
echo "Setting up common configurations..."

# Database configurations
create_config "/config/local/common/spring.datasource.driver-class-name" "org.postgresql.Driver"
create_config "/config/local/common/spring.jpa.database-platform" "org.hibernate.dialect.PostgreSQLDialect"
create_config "/config/local/common/spring.jpa.hibernate.ddl-auto" "update"
create_config "/config/local/common/spring.jpa.show-sql" "false"
create_config "/config/local/common/spring.jpa.properties.hibernate.format_sql" "true"

# Redis configurations
create_config "/config/local/common/spring.data.redis.host" "redis"
create_config "/config/local/common/spring.data.redis.port" "6379"
create_config "/config/local/common/spring.data.redis.database" "0"
create_config "/config/local/common/spring.data.redis.timeout" "2000ms"

# Eureka configurations
create_config "/config/local/common/eureka.client.service-url.defaultZone" "http://eureka-server:8761/eureka"
create_config "/config/local/common/eureka.client.register-with-eureka" "true"
create_config "/config/local/common/eureka.client.fetch-registry" "true"
create_config "/config/local/common/eureka.instance.prefer-ip-address" "true"

# Zipkin configurations
create_config "/config/local/common/management.zipkin.tracing.endpoint" "http://zipkin:9411/api/v2/spans"
create_config "/config/local/common/management.tracing.sampling.probability" "1.0"

# Management endpoints
create_config "/config/local/common/management.endpoints.web.exposure.include" "health,info,metrics,prometheus,zipkin"
create_config "/config/local/common/management.endpoint.health.show-details" "always"
create_config "/config/local/common/management.security.enabled" "false"

# Auth Service specific configurations
echo "Setting up Auth Service configurations..."

# Database configuration
create_config "/config/local/auth-service/spring.datasource.url" "jdbc:postgresql://postgres-auth:5432/authdb"
create_config "/config/local/auth-service/spring.datasource.username" "authuser"
create_config "/config/local/auth-service/spring.datasource.password" "authpass123"

# JWT Configuration - CRITICAL for auth service
create_config "/config/local/auth-service/jwt.secret" "mySecretKey123456789012345678901234567890123456789012345678901234567890"
create_config "/config/local/auth-service/jwt.expiration-ms" "86400000"

# Encryption key for sensitive data
create_config "/config/local/auth-service/app.simple-encryption.secret-key" "myEncryptionKey123456789012345678901234567890"

# Keycloak Configuration (if using external auth)
create_config "/config/local/auth-service/keycloak.auth-server-url" "http://keycloak:8080"
create_config "/config/local/auth-service/keycloak.realm" "mysillydreams"
create_config "/config/local/auth-service/keycloak.admin-client.client-id" "admin-cli"
create_config "/config/local/auth-service/keycloak.admin-client.username" "admin"
create_config "/config/local/auth-service/keycloak.admin-client.password" "admin123"

# Vault Configuration (if using external secrets)
create_config "/config/local/auth-service/vault.uri" "http://vault:8200"
create_config "/config/local/auth-service/vault.token" "dev-token"

# User Service specific configurations
echo "Setting up User Service configurations..."
create_config "/config/local/user-service/server.port" "8082"
create_config "/config/local/user-service/spring.application.name" "user-service"

# Database
create_config "/config/local/user-service/spring.datasource.url" "jdbc:postgresql://postgres-user:5432/userdb"
create_config "/config/local/user-service/spring.datasource.username" "useruser"
create_config "/config/local/user-service/spring.datasource.password" "userpass123"

# Auth Service Integration
create_config "/config/local/user-service/auth.service.url" "http://auth-service:8081"
create_config "/config/local/user-service/auth.service.validate-token-endpoint" "/api/auth/validate"

# API Gateway specific configurations
echo "Setting up API Gateway configurations..."
create_config "/config/local/api-gateway/server.port" "8080"
create_config "/config/local/api-gateway/spring.application.name" "api-gateway"

# Gateway routes
create_config "/config/local/api-gateway/spring.cloud.gateway.routes[0].id" "auth-service"
create_config "/config/local/api-gateway/spring.cloud.gateway.routes[0].uri" "lb://auth-service"
create_config "/config/local/api-gateway/spring.cloud.gateway.routes[0].predicates[0]" "Path=/api/auth/**"

create_config "/config/local/api-gateway/spring.cloud.gateway.routes[1].id" "user-service"
create_config "/config/local/api-gateway/spring.cloud.gateway.routes[1].uri" "lb://user-service"
create_config "/config/local/api-gateway/spring.cloud.gateway.routes[1].predicates[0]" "Path=/api/users/**"

# CORS Configuration
create_config "/config/local/api-gateway/spring.cloud.gateway.globalcors.cors-configurations.[/**].allowed-origins" "*"
create_config "/config/local/api-gateway/spring.cloud.gateway.globalcors.cors-configurations.[/**].allowed-methods" "GET,POST,PUT,DELETE,OPTIONS"
create_config "/config/local/api-gateway/spring.cloud.gateway.globalcors.cors-configurations.[/**].allowed-headers" "*"

# Eureka Server configurations
echo "Setting up Eureka Server configurations..."
create_config "/config/local/eureka-server/server.port" "8761"
create_config "/config/local/eureka-server/spring.application.name" "eureka-server"
create_config "/config/local/eureka-server/eureka.client.register-with-eureka" "false"
create_config "/config/local/eureka-server/eureka.client.fetch-registry" "false"
create_config "/config/local/eureka-server/eureka.server.enable-self-preservation" "false"

echo "Zookeeper configuration setup completed!"
echo "You can verify the configuration at: $ZOOKEEPER_ADMIN_URL"
