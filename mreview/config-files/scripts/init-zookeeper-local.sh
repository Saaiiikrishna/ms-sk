#!/bin/bash

# Local Zookeeper initialization script for MySillyDreams Platform
# This script sets up configuration management for all environments

set -e

echo "Starting Zookeeper configuration initialization for local development..."

# Zookeeper connection details
ZK_HOST=${ZK_HOST:-localhost:2181}
ZK_CLI="docker exec zookeeper-local zookeeper-shell localhost:2181"

# Wait for Zookeeper to be ready
echo "Waiting for Zookeeper to be ready..."
until docker exec zookeeper-local nc -z localhost 2181; do
    echo "Zookeeper is not ready yet, waiting..."
    sleep 2
done

echo "Zookeeper is ready!"

# Function to create znode if it doesn't exist
create_znode() {
    local path=$1
    local data=$2
    
    echo "Creating znode: $path"
    docker exec zookeeper-local zookeeper-shell localhost:2181 <<EOF
create $path "$data"
quit
EOF
}

# Function to set znode data
set_znode() {
    local path=$1
    local data=$2
    
    echo "Setting znode data: $path"
    docker exec zookeeper-local zookeeper-shell localhost:2181 <<EOF
set $path "$data"
quit
EOF
}

# Create root structure
echo "Creating root configuration structure..."
create_znode "/mysillydreams" "MySillyDreams Platform Configuration Root"

# Create environment directories
echo "Creating environment directories..."
create_znode "/mysillydreams/dev" "Development Environment"
create_znode "/mysillydreams/qa" "QA Environment"
create_znode "/mysillydreams/staging" "Staging Environment"

# Create service directories for each environment
environments=("dev" "qa" "staging")
services=("api-gateway" "auth-service" "user-service" "admin-server" "eureka-server" "zipkin" "redis" "vault" "keycloak")

for env in "${environments[@]}"; do
    echo "Setting up $env environment..."
    
    for service in "${services[@]}"; do
        echo "Creating service directory: /mysillydreams/$env/$service"
        create_znode "/mysillydreams/$env/$service" "$service configuration for $env"
    done
done

# Set up development environment configurations
echo "Setting up development environment configurations..."

# API Gateway Dev Configuration
create_znode "/mysillydreams/dev/api-gateway/server.port" "8080"
create_znode "/mysillydreams/dev/api-gateway/eureka.client.service-url.defaultZone" "http://localhost:8761/eureka/"
create_znode "/mysillydreams/dev/api-gateway/spring.data.redis.host" "localhost"
create_znode "/mysillydreams/dev/api-gateway/spring.data.redis.port" "6379"
create_znode "/mysillydreams/dev/api-gateway/management.zipkin.tracing.endpoint" "http://localhost:9411/api/v2/spans"

# Auth Service Dev Configuration
create_znode "/mysillydreams/dev/auth-service/server.port" "8081"
create_znode "/mysillydreams/dev/auth-service/spring.datasource.url" "jdbc:postgresql://localhost:5432/authdb"
create_znode "/mysillydreams/dev/auth-service/spring.datasource.username" "authuser"
create_znode "/mysillydreams/dev/auth-service/spring.datasource.password" "authpass123"
create_znode "/mysillydreams/dev/auth-service/eureka.client.service-url.defaultZone" "http://localhost:8761/eureka/"
create_znode "/mysillydreams/dev/auth-service/keycloak.auth-server-url" "http://localhost:8180"
create_znode "/mysillydreams/dev/auth-service/management.zipkin.tracing.endpoint" "http://localhost:9411/api/v2/spans"

# User Service Dev Configuration
create_znode "/mysillydreams/dev/user-service/server.port" "8082"
create_znode "/mysillydreams/dev/user-service/spring.datasource.url" "jdbc:postgresql://localhost:5433/userdb"
create_znode "/mysillydreams/dev/user-service/spring.datasource.username" "useruser"
create_znode "/mysillydreams/dev/user-service/spring.datasource.password" "userpass123"
create_znode "/mysillydreams/dev/user-service/eureka.client.service-url.defaultZone" "http://localhost:8761/eureka/"
create_znode "/mysillydreams/dev/user-service/management.zipkin.tracing.endpoint" "http://localhost:9411/api/v2/spans"

# Admin Server Dev Configuration
create_znode "/mysillydreams/dev/admin-server/server.port" "8083"
create_znode "/mysillydreams/dev/admin-server/eureka.client.service-url.defaultZone" "http://localhost:8761/eureka/"
create_znode "/mysillydreams/dev/admin-server/management.zipkin.tracing.endpoint" "http://localhost:9411/api/v2/spans"

# Eureka Server Dev Configuration
create_znode "/mysillydreams/dev/eureka-server/server.port" "8761"
create_znode "/mysillydreams/dev/eureka-server/eureka.client.register-with-eureka" "false"
create_znode "/mysillydreams/dev/eureka-server/eureka.client.fetch-registry" "false"

# Zipkin Dev Configuration
create_znode "/mysillydreams/dev/zipkin/server.port" "9411"
create_znode "/mysillydreams/dev/zipkin/storage.type" "mem"
create_znode "/mysillydreams/dev/zipkin/java.opts" "-Xms256m -Xmx512m"

# Redis Dev Configuration
create_znode "/mysillydreams/dev/redis/host" "localhost"
create_znode "/mysillydreams/dev/redis/port" "6379"
create_znode "/mysillydreams/dev/redis/timeout" "2000ms"

# Vault Dev Configuration
create_znode "/mysillydreams/dev/vault/url" "http://localhost:8200"
create_znode "/mysillydreams/dev/vault/token" "root-token"
create_znode "/mysillydreams/dev/vault/enabled" "true"

# Keycloak Dev Configuration
create_znode "/mysillydreams/dev/keycloak/auth-server-url" "http://localhost:8180"
create_znode "/mysillydreams/dev/keycloak/realm" "mysillydreams"
create_znode "/mysillydreams/dev/keycloak/admin.username" "admin"
create_znode "/mysillydreams/dev/keycloak/admin.password" "admin123"

# Security Configuration for Dev
create_znode "/mysillydreams/dev/security/jwt.secret" "LocalJwtSecretKeyForDevelopmentMinimum256BitsLong123456789!"
create_znode "/mysillydreams/dev/security/internal-api.secret" "LocalInternalApiSecretKeyForDevelopment123456789!"
create_znode "/mysillydreams/dev/security/mfa.issuer-name" "MySillyDreamsPlatform-Local"

echo "Development environment configuration completed!"

# Set up QA environment configurations
echo "Setting up QA environment configurations..."

# API Gateway QA Configuration
create_znode "/mysillydreams/qa/api-gateway/server.port" "8080"
create_znode "/mysillydreams/qa/api-gateway/eureka.client.service-url.defaultZone" "http://eureka-server-qa:8761/eureka/"
create_znode "/mysillydreams/qa/api-gateway/spring.data.redis.host" "redis-qa"
create_znode "/mysillydreams/qa/api-gateway/spring.data.redis.port" "6379"
create_znode "/mysillydreams/qa/api-gateway/management.zipkin.tracing.endpoint" "http://zipkin-qa:9411/api/v2/spans"

# Auth Service QA Configuration
create_znode "/mysillydreams/qa/auth-service/server.port" "8081"
create_znode "/mysillydreams/qa/auth-service/spring.datasource.url" "jdbc:postgresql://postgres-auth-qa:5432/authdb"
create_znode "/mysillydreams/qa/auth-service/spring.datasource.username" "authuser"
create_znode "/mysillydreams/qa/auth-service/spring.datasource.password" "\${AUTH_DB_PASSWORD}"
create_znode "/mysillydreams/qa/auth-service/eureka.client.service-url.defaultZone" "http://eureka-server-qa:8761/eureka/"
create_znode "/mysillydreams/qa/auth-service/keycloak.auth-server-url" "http://keycloak-qa:8080"
create_znode "/mysillydreams/qa/auth-service/management.zipkin.tracing.endpoint" "http://zipkin-qa:9411/api/v2/spans"

# User Service QA Configuration
create_znode "/mysillydreams/qa/user-service/server.port" "8082"
create_znode "/mysillydreams/qa/user-service/spring.datasource.url" "jdbc:postgresql://postgres-user-qa:5432/userdb"
create_znode "/mysillydreams/qa/user-service/spring.datasource.username" "useruser"
create_znode "/mysillydreams/qa/user-service/spring.datasource.password" "\${USER_DB_PASSWORD}"
create_znode "/mysillydreams/qa/user-service/eureka.client.service-url.defaultZone" "http://eureka-server-qa:8761/eureka/"
create_znode "/mysillydreams/qa/user-service/management.zipkin.tracing.endpoint" "http://zipkin-qa:9411/api/v2/spans"

# Admin Server QA Configuration
create_znode "/mysillydreams/qa/admin-server/server.port" "8083"
create_znode "/mysillydreams/qa/admin-server/eureka.client.service-url.defaultZone" "http://eureka-server-qa:8761/eureka/"
create_znode "/mysillydreams/qa/admin-server/management.zipkin.tracing.endpoint" "http://zipkin-qa:9411/api/v2/spans"

# Eureka Server QA Configuration
create_znode "/mysillydreams/qa/eureka-server/server.port" "8761"
create_znode "/mysillydreams/qa/eureka-server/eureka.client.register-with-eureka" "false"
create_znode "/mysillydreams/qa/eureka-server/eureka.client.fetch-registry" "false"

# Zipkin QA Configuration
create_znode "/mysillydreams/qa/zipkin/server.port" "9411"
create_znode "/mysillydreams/qa/zipkin/storage.type" "elasticsearch"
create_znode "/mysillydreams/qa/zipkin/storage.elasticsearch.hosts" "http://elasticsearch-qa:9200"

# Redis QA Configuration
create_znode "/mysillydreams/qa/redis/host" "redis-qa"
create_znode "/mysillydreams/qa/redis/port" "6379"
create_znode "/mysillydreams/qa/redis/timeout" "2000ms"

# Vault QA Configuration
create_znode "/mysillydreams/qa/vault/url" "http://vault-qa:8200"
create_znode "/mysillydreams/qa/vault/token" "\${VAULT_TOKEN}"
create_znode "/mysillydreams/qa/vault/enabled" "true"

# Keycloak QA Configuration
create_znode "/mysillydreams/qa/keycloak/auth-server-url" "http://keycloak-qa:8080"
create_znode "/mysillydreams/qa/keycloak/realm" "mysillydreams"
create_znode "/mysillydreams/qa/keycloak/admin.username" "\${KEYCLOAK_ADMIN_USERNAME}"
create_znode "/mysillydreams/qa/keycloak/admin.password" "\${KEYCLOAK_ADMIN_PASSWORD}"

# Security Configuration for QA
create_znode "/mysillydreams/qa/security/jwt.secret" "\${JWT_SECRET}"
create_znode "/mysillydreams/qa/security/internal-api.secret" "\${INTERNAL_API_SECRET}"
create_znode "/mysillydreams/qa/security/mfa.issuer-name" "MySillyDreamsPlatform-QA"

echo "QA environment configuration completed!"

# Set up Staging environment configurations
echo "Setting up Staging environment configurations..."

# API Gateway Staging Configuration
create_znode "/mysillydreams/staging/api-gateway/server.port" "8080"
create_znode "/mysillydreams/staging/api-gateway/eureka.client.service-url.defaultZone" "http://eureka-server-staging:8761/eureka/"
create_znode "/mysillydreams/staging/api-gateway/spring.data.redis.host" "redis-staging"
create_znode "/mysillydreams/staging/api-gateway/spring.data.redis.port" "6379"
create_znode "/mysillydreams/staging/api-gateway/management.zipkin.tracing.endpoint" "http://zipkin-staging:9411/api/v2/spans"

# Auth Service Staging Configuration
create_znode "/mysillydreams/staging/auth-service/server.port" "8081"
create_znode "/mysillydreams/staging/auth-service/spring.datasource.url" "jdbc:postgresql://postgres-auth-staging:5432/authdb"
create_znode "/mysillydreams/staging/auth-service/spring.datasource.username" "authuser"
create_znode "/mysillydreams/staging/auth-service/spring.datasource.password" "\${AUTH_DB_PASSWORD}"
create_znode "/mysillydreams/staging/auth-service/eureka.client.service-url.defaultZone" "http://eureka-server-staging:8761/eureka/"
create_znode "/mysillydreams/staging/auth-service/keycloak.auth-server-url" "http://keycloak-staging:8080"
create_znode "/mysillydreams/staging/auth-service/management.zipkin.tracing.endpoint" "http://zipkin-staging:9411/api/v2/spans"

# User Service Staging Configuration
create_znode "/mysillydreams/staging/user-service/server.port" "8082"
create_znode "/mysillydreams/staging/user-service/spring.datasource.url" "jdbc:postgresql://postgres-user-staging:5432/userdb"
create_znode "/mysillydreams/staging/user-service/spring.datasource.username" "useruser"
create_znode "/mysillydreams/staging/user-service/spring.datasource.password" "\${USER_DB_PASSWORD}"
create_znode "/mysillydreams/staging/user-service/eureka.client.service-url.defaultZone" "http://eureka-server-staging:8761/eureka/"
create_znode "/mysillydreams/staging/user-service/management.zipkin.tracing.endpoint" "http://zipkin-staging:9411/api/v2/spans"

# Admin Server Staging Configuration
create_znode "/mysillydreams/staging/admin-server/server.port" "8083"
create_znode "/mysillydreams/staging/admin-server/eureka.client.service-url.defaultZone" "http://eureka-server-staging:8761/eureka/"
create_znode "/mysillydreams/staging/admin-server/management.zipkin.tracing.endpoint" "http://zipkin-staging:9411/api/v2/spans"

# Eureka Server Staging Configuration
create_znode "/mysillydreams/staging/eureka-server/server.port" "8761"
create_znode "/mysillydreams/staging/eureka-server/eureka.client.register-with-eureka" "false"
create_znode "/mysillydreams/staging/eureka-server/eureka.client.fetch-registry" "false"

# Zipkin Staging Configuration
create_znode "/mysillydreams/staging/zipkin/server.port" "9411"
create_znode "/mysillydreams/staging/zipkin/storage.type" "elasticsearch"
create_znode "/mysillydreams/staging/zipkin/storage.elasticsearch.hosts" "http://elasticsearch-staging:9200"

# Redis Staging Configuration
create_znode "/mysillydreams/staging/redis/host" "redis-staging"
create_znode "/mysillydreams/staging/redis/port" "6379"
create_znode "/mysillydreams/staging/redis/timeout" "2000ms"

# Vault Staging Configuration
create_znode "/mysillydreams/staging/vault/url" "http://vault-staging:8200"
create_znode "/mysillydreams/staging/vault/token" "\${VAULT_TOKEN}"
create_znode "/mysillydreams/staging/vault/enabled" "true"

# Keycloak Staging Configuration
create_znode "/mysillydreams/staging/keycloak/auth-server-url" "http://keycloak-staging:8080"
create_znode "/mysillydreams/staging/keycloak/realm" "mysillydreams"
create_znode "/mysillydreams/staging/keycloak/admin.username" "\${KEYCLOAK_ADMIN_USERNAME}"
create_znode "/mysillydreams/staging/keycloak/admin.password" "\${KEYCLOAK_ADMIN_PASSWORD}"

# Security Configuration for Staging
create_znode "/mysillydreams/staging/security/jwt.secret" "\${JWT_SECRET}"
create_znode "/mysillydreams/staging/security/internal-api.secret" "\${INTERNAL_API_SECRET}"
create_znode "/mysillydreams/staging/security/mfa.issuer-name" "MySillyDreamsPlatform-Staging"

echo "Staging environment configuration completed!"

echo ""
echo "=== Zookeeper Configuration Initialization Complete ==="
echo ""
echo "Configuration Structure Created:"
echo "- /mysillydreams/dev/ (Development Environment)"
echo "- /mysillydreams/qa/ (QA Environment)"  
echo "- /mysillydreams/staging/ (Staging Environment)"
echo ""
echo "Services Configured:"
echo "- api-gateway"
echo "- auth-service"
echo "- user-service"
echo "- admin-server"
echo "- eureka-server"
echo ""
echo "You can now start your microservices with environment-specific profiles!"
echo "Example: mvn spring-boot:run -Dspring-boot.run.profiles=dev"
