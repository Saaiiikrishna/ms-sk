#!/bin/bash

# Configuration Verification Script
# This script verifies that all configurations are properly loaded in ZooKeeper

set -e

echo "🔍 Verifying ZooKeeper Configuration"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

print_status() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Get ZooKeeper pod
ZK_POD=$(kubectl get pods -n mysillydreams-dev -l app=zookeeper --no-headers | awk '{print $1}' | head -1)

if [ -z "$ZK_POD" ]; then
    print_error "ZooKeeper pod not found"
    exit 1
fi

print_status "Using ZooKeeper pod: $ZK_POD"

# Function to check if a ZooKeeper path exists
check_zk_path() {
    local path=$1
    local description=$2
    
    if kubectl exec -n mysillydreams-dev $ZK_POD -- zookeeper-shell localhost:2181 ls "$path" &> /dev/null; then
        print_success "$description exists at $path"
        return 0
    else
        print_error "$description missing at $path"
        return 1
    fi
}

# Function to get and display ZooKeeper value
get_zk_value() {
    local path=$1
    local description=$2
    
    print_status "Checking $description..."
    if kubectl exec -n mysillydreams-dev $ZK_POD -- zookeeper-shell localhost:2181 get "$path" 2>/dev/null; then
        print_success "$description configured"
    else
        print_error "$description not configured at $path"
    fi
}

echo ""
print_status "=== ZooKeeper Configuration Verification ==="

# Check base paths
check_zk_path "/config" "Base config path"
check_zk_path "/config/auth-service" "Auth service config path"

echo ""
print_status "=== Auth Service Configuration Details ==="

# List all auth service configurations
print_status "All auth service configurations:"
kubectl exec -n mysillydreams-dev $ZK_POD -- zookeeper-shell localhost:2181 ls /config/auth-service

echo ""
print_status "=== Key Configuration Values ==="

# Check critical configurations
get_zk_value "/config/auth-service/spring.datasource.url" "Database URL"
get_zk_value "/config/auth-service/spring.datasource.username" "Database Username"
get_zk_value "/config/auth-service/eureka.client.service-url.defaultZone" "Eureka URL"
get_zk_value "/config/auth-service/keycloak.auth-server-url" "Keycloak URL"
get_zk_value "/config/auth-service/spring.data.redis.host" "Redis Host"

echo ""
print_status "=== Service Status ==="

# Check service status
print_status "ZooKeeper Status:"
kubectl get pods -n mysillydreams-dev -l app=zookeeper

print_status "Auth Service Status:"
kubectl get pods -n mysillydreams-dev -l app=auth-service

# Check if auth service is registered with Eureka
print_status "Checking Eureka Registration..."
EUREKA_POD=$(kubectl get pods -n mysillydreams-dev -l app=eureka-server --no-headers | awk '{print $1}' | head -1)
if [ -n "$EUREKA_POD" ]; then
    if kubectl exec -n mysillydreams-dev $EUREKA_POD -- curl -s http://localhost:8761/eureka/apps | grep -q "AUTH-SERVICE"; then
        print_success "Auth Service is registered with Eureka"
    else
        print_error "Auth Service is not registered with Eureka"
    fi
fi

# Check auth service health
print_status "Checking Auth Service Health..."
AUTH_POD=$(kubectl get pods -n mysillydreams-dev -l app=auth-service --no-headers | grep "1/1" | awk '{print $1}' | head -1)
if [ -n "$AUTH_POD" ]; then
    if kubectl exec -n mysillydreams-dev $AUTH_POD -- curl -s http://localhost:8081/actuator/health | grep -q '"status":"UP"'; then
        print_success "Auth Service is healthy"
    else
        print_error "Auth Service health check failed"
    fi
else
    print_error "No ready Auth Service pods found"
fi

echo ""
print_success "✅ Configuration verification completed!"
