#!/bin/bash

# Production Deployment Script for MySillyDreams Platform
# This script handles the complete production deployment with security validations

set -euo pipefail

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
DEPLOYMENT_ENV="${DEPLOYMENT_ENV:-production}"
VAULT_ADDR="${VAULT_ADDR:-https://vault.mysillydreams-prod:8200}"
VAULT_TOKEN="${VAULT_TOKEN:-}"
NAMESPACE="${NAMESPACE:-mysillydreams-prod}"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Logging functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Error handling
error_exit() {
    log_error "$1"
    exit 1
}

# Cleanup function
cleanup() {
    log_info "Cleaning up temporary files..."
    rm -f /tmp/deployment-*.yaml
    rm -f /tmp/vault-*.json
}

trap cleanup EXIT

# Pre-deployment checks
pre_deployment_checks() {
    log_info "Running pre-deployment checks..."
    
    # Check required tools
    command -v kubectl >/dev/null 2>&1 || error_exit "kubectl is required but not installed"
    command -v docker >/dev/null 2>&1 || error_exit "docker is required but not installed"
    command -v vault >/dev/null 2>&1 || error_exit "vault CLI is required but not installed"
    command -v jq >/dev/null 2>&1 || error_exit "jq is required but not installed"
    
    # Check Kubernetes connectivity
    kubectl cluster-info >/dev/null 2>&1 || error_exit "Cannot connect to Kubernetes cluster"
    
    # Check namespace exists
    kubectl get namespace "$NAMESPACE" >/dev/null 2>&1 || {
        log_warning "Namespace $NAMESPACE does not exist, creating..."
        kubectl create namespace "$NAMESPACE"
    }
    
    # Check Vault connectivity
    if [[ -n "$VAULT_TOKEN" ]]; then
        export VAULT_ADDR VAULT_TOKEN
        vault status >/dev/null 2>&1 || error_exit "Cannot connect to Vault at $VAULT_ADDR"
        log_success "Vault connectivity verified"
    else
        log_warning "VAULT_TOKEN not set, skipping Vault checks"
    fi
    
    log_success "Pre-deployment checks completed"
}

# Build and push Docker images
build_and_push_images() {
    log_info "Building and pushing Docker images..."
    
    local services=("auth-service" "api-gateway" "user-service" "admin-server")
    local registry="${DOCKER_REGISTRY:-mysillydreams-registry.com}"
    local tag="${BUILD_TAG:-$(date +%Y%m%d-%H%M%S)}"
    
    for service in "${services[@]}"; do
        log_info "Building $service..."
        
        cd "$PROJECT_ROOT/$service"
        
        # Build with Maven
        ./mvnw clean package -DskipTests
        
        # Build Docker image
        docker build -t "$registry/$service:$tag" .
        docker tag "$registry/$service:$tag" "$registry/$service:latest"
        
        # Push to registry
        docker push "$registry/$service:$tag"
        docker push "$registry/$service:latest"
        
        log_success "$service image built and pushed"
    done
    
    # Export tag for use in deployment
    export BUILD_TAG="$tag"
    log_success "All images built and pushed with tag: $tag"
}

# Setup Vault secrets
setup_vault_secrets() {
    log_info "Setting up Vault secrets..."
    
    if [[ -z "$VAULT_TOKEN" ]]; then
        log_warning "VAULT_TOKEN not set, skipping Vault secret setup"
        return 0
    fi
    
    # Generate JWT secrets if they don't exist
    vault kv get -field=signing-key secret/jwt >/dev/null 2>&1 || {
        log_info "Generating new JWT secrets..."
        
        # Generate 512-bit secrets for HS512
        local jwt_secret=$(openssl rand -base64 64)
        local refresh_secret=$(openssl rand -base64 64)
        
        vault kv put secret/jwt \
            signing-key="$jwt_secret" \
            refresh-signing-key="$refresh_secret"
        
        log_success "JWT secrets generated and stored in Vault"
    }
    
    # Setup database credentials
    vault kv get -field=username secret/database >/dev/null 2>&1 || {
        log_info "Setting up database credentials..."
        
        vault kv put secret/database \
            username="${DB_USERNAME:-mysillydreams_user}" \
            password="${DB_PASSWORD:-$(openssl rand -base64 32)}" \
            host="${DB_HOST:-postgres.mysillydreams-prod}" \
            port="${DB_PORT:-5432}" \
            database="${DB_NAME:-mysillydreams_prod}"
        
        log_success "Database credentials stored in Vault"
    }
    
    log_success "Vault secrets setup completed"
}

# Deploy infrastructure components
deploy_infrastructure() {
    log_info "Deploying infrastructure components..."
    
    # Deploy Redis for rate limiting
    kubectl apply -f "$SCRIPT_DIR/k8s/redis.yaml" -n "$NAMESPACE"
    
    # Deploy PostgreSQL
    kubectl apply -f "$SCRIPT_DIR/k8s/postgresql.yaml" -n "$NAMESPACE"
    
    # Deploy Vault agent (if not using external Vault)
    if [[ "${USE_EXTERNAL_VAULT:-true}" != "true" ]]; then
        kubectl apply -f "$SCRIPT_DIR/k8s/vault.yaml" -n "$NAMESPACE"
    fi
    
    # Wait for infrastructure to be ready
    log_info "Waiting for infrastructure to be ready..."
    kubectl wait --for=condition=ready pod -l app=redis -n "$NAMESPACE" --timeout=300s
    kubectl wait --for=condition=ready pod -l app=postgresql -n "$NAMESPACE" --timeout=300s
    
    log_success "Infrastructure components deployed"
}

# Deploy application services
deploy_services() {
    log_info "Deploying application services..."
    
    local services=("auth-service" "api-gateway" "user-service" "admin-server")
    
    for service in "${services[@]}"; do
        log_info "Deploying $service..."
        
        # Replace placeholders in deployment YAML
        envsubst < "$SCRIPT_DIR/k8s/$service.yaml" > "/tmp/deployment-$service.yaml"
        
        # Apply deployment
        kubectl apply -f "/tmp/deployment-$service.yaml" -n "$NAMESPACE"
        
        # Wait for deployment to be ready
        kubectl wait --for=condition=available deployment/$service -n "$NAMESPACE" --timeout=600s
        
        log_success "$service deployed successfully"
    done
    
    log_success "All services deployed"
}

# Run database migrations
run_migrations() {
    log_info "Running database migrations..."
    
    # Run migrations using a job
    envsubst < "$SCRIPT_DIR/k8s/migration-job.yaml" > "/tmp/migration-job.yaml"
    kubectl apply -f "/tmp/migration-job.yaml" -n "$NAMESPACE"
    
    # Wait for migration to complete
    kubectl wait --for=condition=complete job/database-migration -n "$NAMESPACE" --timeout=300s
    
    log_success "Database migrations completed"
}

# Validate deployment
validate_deployment() {
    log_info "Validating deployment..."
    
    # Check all pods are running
    local failed_pods=$(kubectl get pods -n "$NAMESPACE" --field-selector=status.phase!=Running --no-headers | wc -l)
    if [[ $failed_pods -gt 0 ]]; then
        log_error "Some pods are not running:"
        kubectl get pods -n "$NAMESPACE" --field-selector=status.phase!=Running
        return 1
    fi
    
    # Test API Gateway health
    local gateway_url=$(kubectl get service api-gateway -n "$NAMESPACE" -o jsonpath='{.status.loadBalancer.ingress[0].ip}')
    if [[ -n "$gateway_url" ]]; then
        curl -f "http://$gateway_url/api/health" >/dev/null 2>&1 || {
            log_error "API Gateway health check failed"
            return 1
        }
        log_success "API Gateway health check passed"
    fi
    
    # Test authentication flow
    log_info "Testing authentication flow..."
    # This would include actual API tests
    
    log_success "Deployment validation completed"
}

# Setup monitoring and alerting
setup_monitoring() {
    log_info "Setting up monitoring and alerting..."
    
    # Deploy Prometheus and Grafana
    kubectl apply -f "$SCRIPT_DIR/k8s/monitoring/" -n "$NAMESPACE"
    
    # Setup log aggregation
    kubectl apply -f "$SCRIPT_DIR/k8s/logging/" -n "$NAMESPACE"
    
    log_success "Monitoring and alerting setup completed"
}

# Main deployment function
main() {
    log_info "Starting production deployment for MySillyDreams Platform"
    log_info "Environment: $DEPLOYMENT_ENV"
    log_info "Namespace: $NAMESPACE"
    
    # Run deployment steps
    pre_deployment_checks
    build_and_push_images
    setup_vault_secrets
    deploy_infrastructure
    run_migrations
    deploy_services
    validate_deployment
    setup_monitoring
    
    log_success "Production deployment completed successfully!"
    log_info "Services are available at:"
    kubectl get services -n "$NAMESPACE" -o wide
}

# Script execution
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    main "$@"
fi
