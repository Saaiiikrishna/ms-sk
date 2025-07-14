#!/bin/bash

# Production-Ready Auth Service Deployment with ZooKeeper Integration
# This script deploys the auth service with proper ZooKeeper configuration management

set -e

echo "=========================================="
echo "Auth Service with ZooKeeper Deployment"
echo "=========================================="

# Configuration
NAMESPACE="mysillydreams-dev"
TIMEOUT=300

# Function to wait for deployment to be ready
wait_for_deployment() {
    local deployment=$1
    local namespace=$2
    local timeout=${3:-300}
    
    echo "Waiting for deployment $deployment to be ready..."
    kubectl wait --for=condition=available --timeout=${timeout}s deployment/$deployment -n $namespace
    
    if [ $? -eq 0 ]; then
        echo "✅ Deployment $deployment is ready"
    else
        echo "❌ Deployment $deployment failed to become ready within ${timeout}s"
        return 1
    fi
}

# Function to wait for pods to be running
wait_for_pods() {
    local label=$1
    local namespace=$2
    local timeout=${3:-300}
    
    echo "Waiting for pods with label $label to be running..."
    kubectl wait --for=condition=ready --timeout=${timeout}s pod -l $label -n $namespace
    
    if [ $? -eq 0 ]; then
        echo "✅ Pods with label $label are ready"
    else
        echo "❌ Pods with label $label failed to become ready within ${timeout}s"
        return 1
    fi
}

# Step 1: Deploy ZooKeeper (if not already deployed)
echo "Step 1: Checking ZooKeeper deployment..."
if kubectl get deployment zookeeper -n $NAMESPACE >/dev/null 2>&1; then
    echo "✅ ZooKeeper deployment already exists"
else
    echo "Deploying ZooKeeper..."
    kubectl apply -f k8s/12-zookeeper-native.yaml
    wait_for_deployment "zookeeper" $NAMESPACE $TIMEOUT
fi

# Step 2: Deploy ZooKeeper Service (Spring Boot wrapper)
echo "Step 2: Checking ZooKeeper Service deployment..."
if kubectl get deployment zookeeper-service -n $NAMESPACE >/dev/null 2>&1; then
    echo "✅ ZooKeeper Service deployment already exists"
else
    echo "Deploying ZooKeeper Service..."
    kubectl apply -f k8s/05-zookeeper-service.yaml
    wait_for_deployment "zookeeper-service" $NAMESPACE $TIMEOUT
fi

# Step 3: Create Auth Service Secrets
echo "Step 3: Creating Auth Service secrets..."
kubectl apply -f k8s/06-auth-service-secrets.yaml
echo "✅ Auth Service secrets created"

# Step 4: Create ZooKeeper Service Configurations
echo "Step 4: Creating ZooKeeper service configurations..."
kubectl apply -f k8s/zookeeper-service-configs.yaml
echo "✅ ZooKeeper service configurations created"

# Step 5: Load configurations into ZooKeeper
echo "Step 5: Loading configurations into ZooKeeper..."
kubectl apply -f k8s/zookeeper-config-loader.yaml

# Wait for the config loader job to complete
echo "Waiting for configuration loading job to complete..."
kubectl wait --for=condition=complete --timeout=300s job/zookeeper-config-loader -n $NAMESPACE

if [ $? -eq 0 ]; then
    echo "✅ ZooKeeper configurations loaded successfully"
else
    echo "❌ Configuration loading job failed"
    kubectl logs job/zookeeper-config-loader -n $NAMESPACE
    exit 1
fi

# Step 6: Deploy Auth Service
echo "Step 6: Deploying Auth Service..."
kubectl apply -f k8s/06-auth-service.yaml
wait_for_deployment "auth-service" $NAMESPACE $TIMEOUT

# Step 7: Verify deployment
echo "Step 7: Verifying deployment..."

# Check pod status
echo "Checking pod status..."
kubectl get pods -n $NAMESPACE -l app=auth-service

# Check service status
echo "Checking service status..."
kubectl get svc -n $NAMESPACE -l app=auth-service

# Test health endpoint
echo "Testing health endpoint..."
AUTH_POD=$(kubectl get pods -n $NAMESPACE -l app=auth-service --no-headers | head -1 | awk '{print $1}')
if [ ! -z "$AUTH_POD" ]; then
    echo "Testing health endpoint on pod: $AUTH_POD"
    kubectl exec -n $NAMESPACE $AUTH_POD -- curl -f http://localhost:8081/actuator/health || echo "Health check failed, but pod might still be starting..."
fi

# Step 8: Display connection information
echo "=========================================="
echo "✅ Deployment completed successfully!"
echo "=========================================="
echo ""
echo "Auth Service Information:"
echo "- Namespace: $NAMESPACE"
echo "- Service: auth-service"
echo "- Port: 8081"
echo ""
echo "To access the service:"
echo "kubectl port-forward service/auth-service 8081:8081 -n $NAMESPACE"
echo ""
echo "To check logs:"
echo "kubectl logs -f deployment/auth-service -n $NAMESPACE"
echo ""
echo "To check ZooKeeper configurations:"
echo "kubectl exec -n $NAMESPACE deployment/zookeeper -- /opt/kafka/bin/kafka-run-class.sh org.apache.zookeeper.ZooKeeperMain -server localhost:2181 ls /mysillydreams/dev"
echo ""
echo "Health check URL (after port-forward):"
echo "http://localhost:8081/actuator/health"
echo ""
echo "=========================================="
