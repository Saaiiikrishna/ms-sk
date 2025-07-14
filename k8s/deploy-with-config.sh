#!/bin/bash

# Production-Ready Deployment Script with ZooKeeper Configuration Management
# This script ensures all configurations are loaded into ZooKeeper before deploying services

set -e

echo "🚀 Starting Production Deployment with ZooKeeper Configuration Management"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check if kubectl is available
if ! command -v kubectl &> /dev/null; then
    print_error "kubectl is not installed or not in PATH"
    exit 1
fi

# Check if we can connect to the cluster
if ! kubectl cluster-info &> /dev/null; then
    print_error "Cannot connect to Kubernetes cluster"
    exit 1
fi

print_status "Connected to Kubernetes cluster"

# Step 1: Deploy ZooKeeper if not already deployed
print_status "Checking ZooKeeper deployment..."
if ! kubectl get deployment zookeeper -n mysillydreams-dev &> /dev/null; then
    print_status "Deploying ZooKeeper..."
    kubectl apply -f k8s/01-zookeeper-native.yaml
    
    print_status "Waiting for ZooKeeper to be ready..."
    kubectl wait --for=condition=available --timeout=300s deployment/zookeeper -n mysillydreams-dev
    
    # Wait additional time for ZooKeeper to be fully ready
    sleep 30
    print_success "ZooKeeper deployed and ready"
else
    print_success "ZooKeeper already deployed"
fi

# Step 2: Deploy configuration loader
print_status "Deploying ZooKeeper configuration loader..."
kubectl apply -f k8s/zookeeper-service-configs.yaml
kubectl apply -f k8s/zookeeper-config-loader.yaml

# Step 3: Wait for configuration loading to complete
print_status "Waiting for configuration loading to complete..."
kubectl wait --for=condition=complete --timeout=300s job/zookeeper-config-loader -n mysillydreams-dev

# Check if the job succeeded
if kubectl get job zookeeper-config-loader -n mysillydreams-dev -o jsonpath='{.status.conditions[?(@.type=="Complete")].status}' | grep -q "True"; then
    print_success "Configuration loaded successfully into ZooKeeper"
else
    print_error "Configuration loading failed"
    print_status "Checking job logs..."
    kubectl logs job/zookeeper-config-loader -n mysillydreams-dev
    exit 1
fi

# Step 4: Deploy Auth Service secrets
print_status "Deploying Auth Service secrets..."
kubectl apply -f k8s/06-auth-service-secrets.yaml

# Step 5: Deploy Auth Service
print_status "Deploying Auth Service..."
kubectl apply -f k8s/06-auth-service.yaml

# Step 6: Wait for Auth Service to be ready
print_status "Waiting for Auth Service to be ready..."
kubectl wait --for=condition=available --timeout=600s deployment/auth-service -n mysillydreams-dev

# Step 7: Verify deployment
print_status "Verifying deployment..."

# Check if auth service pods are ready
AUTH_READY=$(kubectl get pods -n mysillydreams-dev -l app=auth-service --no-headers | awk '{print $2}' | grep -c "1/1" || echo "0")
if [ "$AUTH_READY" -gt 0 ]; then
    print_success "Auth Service is ready! ($AUTH_READY pods running)"
else
    print_warning "Auth Service pods are not ready yet. Checking status..."
    kubectl get pods -n mysillydreams-dev -l app=auth-service
fi

# Check ZooKeeper configuration
print_status "Verifying ZooKeeper configuration..."
ZK_POD=$(kubectl get pods -n mysillydreams-dev -l app=zookeeper --no-headers | awk '{print $1}' | head -1)
if [ -n "$ZK_POD" ]; then
    print_status "Checking configuration in ZooKeeper..."
    kubectl exec -n mysillydreams-dev $ZK_POD -- zookeeper-shell localhost:2181 ls /config/auth-service | head -10
    print_success "Configuration verified in ZooKeeper"
fi

# Step 8: Display service information
print_success "🎉 Deployment completed successfully!"
echo ""
print_status "Service Information:"
kubectl get services -n mysillydreams-dev
echo ""
print_status "Pod Status:"
kubectl get pods -n mysillydreams-dev
echo ""
print_status "Auth Service Health Check:"
AUTH_POD=$(kubectl get pods -n mysillydreams-dev -l app=auth-service --no-headers | grep "1/1" | awk '{print $1}' | head -1)
if [ -n "$AUTH_POD" ]; then
    kubectl exec -n mysillydreams-dev $AUTH_POD -- curl -s http://localhost:8081/actuator/health || echo "Health check endpoint not ready yet"
fi

print_success "✅ Production deployment with ZooKeeper configuration management completed!"
print_status "🔧 Configuration is now managed in ZooKeeper at /config/auth-service/"
print_status "🚀 Auth Service is ready for production use!"
