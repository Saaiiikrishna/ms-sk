#!/bin/bash

# Local development startup script for MySillyDreams Platform
# This script starts all infrastructure services and initializes them

set -e

echo "🚀 Starting MySillyDreams Platform Local Development Environment"
echo "================================================================"

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
    echo "❌ Docker is not running. Please start Docker and try again."
    exit 1
fi

# Check if docker-compose is available
if ! command -v docker-compose &> /dev/null; then
    echo "❌ docker-compose is not installed. Please install docker-compose and try again."
    exit 1
fi

# Function to wait for service to be healthy
wait_for_service() {
    local service_name=$1
    local max_attempts=30
    local attempt=1
    
    echo "⏳ Waiting for $service_name to be healthy..."
    
    while [ $attempt -le $max_attempts ]; do
        if docker-compose -f docker-compose-local.yml ps $service_name | grep -q "healthy"; then
            echo "✅ $service_name is healthy!"
            return 0
        fi
        
        echo "   Attempt $attempt/$max_attempts - $service_name not ready yet..."
        sleep 10
        attempt=$((attempt + 1))
    done
    
    echo "❌ $service_name failed to become healthy after $max_attempts attempts"
    return 1
}

# Start infrastructure services
echo ""
echo "📦 Starting infrastructure services..."
docker-compose -f docker-compose-local.yml up -d

# Wait for services to be healthy
echo ""
echo "🔍 Checking service health..."

# Wait for PostgreSQL databases
wait_for_service postgres-auth
wait_for_service postgres-user
wait_for_service postgres-keycloak

# Wait for Redis
wait_for_service redis

# Wait for Vault
wait_for_service vault

# Wait for Keycloak
wait_for_service keycloak

# Wait for Zookeeper
wait_for_service zookeeper

# Wait for Zipkin
wait_for_service zipkin

# Initialize Zookeeper
echo ""
echo "🔧 Initializing Zookeeper..."
if [ -f "scripts/init-zookeeper-local.sh" ]; then
    chmod +x scripts/init-zookeeper-local.sh
    ./scripts/init-zookeeper-local.sh
else
    echo "⚠️  Zookeeper initialization script not found. Please run it manually."
fi

# Initialize Vault
echo ""
echo "🔐 Initializing Vault..."
if [ -f "scripts/init-vault-local.sh" ]; then
    chmod +x scripts/init-vault-local.sh
    # Run vault initialization inside the vault container
    docker exec vault-local /vault/init-vault.sh
else
    echo "⚠️  Vault initialization script not found. Please run it manually."
fi

# Display service URLs
echo ""
echo "🎉 All services are ready!"
echo "=========================="
echo ""
echo "📊 Service URLs:"
echo "   API Gateway:        http://localhost:8080"
echo "   Keycloak Admin:     http://localhost:8180 (admin/admin123)"
echo "   Vault UI:           http://localhost:8200 (token: root-token)"
echo "   Zipkin UI:          http://localhost:9411"
echo "   Zookeeper:          localhost:2181"
echo "   Redis:              localhost:6379"
echo ""
echo "🗄️  Database Connections:"
echo "   Auth Service DB:    localhost:5432/authdb (authuser/authpass123)"
echo "   User Service DB:    localhost:5433/userdb (useruser/userpass123)"
echo "   Keycloak DB:        localhost:5434/keycloak (postgres/postgres123)"
echo ""
echo "🔧 Development Configuration:"
echo "   Spring Profile:     local"
echo "   API Gateway:        http://localhost:8080"
echo "   Eureka Server:      http://localhost:8761"
echo "   Admin Server:       http://localhost:8083"
echo "   Zookeeper Service:  http://localhost:8084"
echo ""
echo "🚀 Ready to start your microservices!"
echo "   API Gateway:        mvn spring-boot:run -Dspring-boot.run.profiles=local (Port 8080)"
echo "   Auth Service:       mvn spring-boot:run -Dspring-boot.run.profiles=local (Port 8081)"
echo "   User Service:       mvn spring-boot:run -Dspring-boot.run.profiles=local (Port 8082)"
echo "   Admin Server:       mvn spring-boot:run -Dspring-boot.run.profiles=local (Port 8083)"
echo "   Zookeeper Service:  mvn spring-boot:run -Dspring-boot.run.profiles=local (Port 8084)"
echo ""
echo "📝 Keycloak Realm: mysillydreams"
echo "   Test Users:"
echo "   - admin/admin123 (admin role)"
echo "   - testuser/testuser123 (user role)"
echo ""
echo "🛑 To stop all services: docker-compose -f docker-compose-local.yml down"
echo "🗑️  To clean up volumes: docker-compose -f docker-compose-local.yml down -v"
