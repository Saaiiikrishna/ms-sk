# Auth Service with ZooKeeper - Production Deployment Guide

## Overview

This guide provides step-by-step instructions for deploying the Auth Service with ZooKeeper integration in a production-ready manner. All secrets are properly managed, and configurations are loaded from ZooKeeper.

## Prerequisites

1. **Kubernetes cluster** with kubectl access
2. **Namespace** `mysillydreams-dev` created
3. **PostgreSQL** database for auth service
4. **Redis** instance for caching
5. **Keycloak** for authentication
6. **Vault** for secret management (optional)

## Architecture

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Auth Service  │────│   ZooKeeper     │────│ ZooKeeper       │
│   (Spring Boot) │    │   (Native)      │    │ Service         │
│                 │    │   Port: 2181    │    │ (Config Mgmt)   │
└─────────────────┘    └─────────────────┘    └─────────────────┘
         │                       │                       │
         │                       │                       │
         ▼                       ▼                       ▼
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   PostgreSQL    │    │   Kubernetes    │    │   Redis Cache   │
│   (Database)    │    │   Secrets       │    │   (Session)     │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

## Deployment Steps

### Step 1: Update Secrets (CRITICAL for Production)

**⚠️ IMPORTANT: Change all default passwords and secrets before production deployment!**

Edit `k8s/06-auth-service-secrets.yaml` and update all base64-encoded values:

```bash
# Generate new secrets
echo -n "your-actual-db-password" | base64
echo -n "your-actual-jwt-secret-min-32-chars" | base64
echo -n "your-actual-encryption-key-32-chars" | base64
```

### Step 2: Deploy Infrastructure

```bash
# 1. Deploy ZooKeeper (if not already deployed)
kubectl apply -f k8s/12-zookeeper-native.yaml

# 2. Deploy ZooKeeper Service (Spring Boot wrapper)
kubectl apply -f k8s/05-zookeeper-service.yaml

# Wait for ZooKeeper to be ready
kubectl wait --for=condition=available --timeout=300s deployment/zookeeper -n mysillydreams-dev
kubectl wait --for=condition=available --timeout=300s deployment/zookeeper-service -n mysillydreams-dev
```

### Step 3: Deploy Auth Service

```bash
# Option A: Use the automated deployment script
chmod +x k8s/deploy-auth-service-with-zookeeper.sh
./k8s/deploy-auth-service-with-zookeeper.sh

# Option B: Manual deployment
kubectl apply -f k8s/06-auth-service-secrets.yaml
kubectl apply -f k8s/zookeeper-service-configs.yaml
kubectl apply -f k8s/zookeeper-config-loader.yaml
kubectl wait --for=condition=complete --timeout=300s job/zookeeper-config-loader -n mysillydreams-dev
kubectl apply -f k8s/06-auth-service.yaml
```

### Step 4: Verify Deployment

```bash
# Check all pods are running
kubectl get pods -n mysillydreams-dev

# Check auth service logs
kubectl logs -f deployment/auth-service -n mysillydreams-dev

# Test health endpoint
kubectl port-forward service/auth-service 8081:8081 -n mysillydreams-dev &
curl http://localhost:8081/actuator/health
```

## Configuration Management

### ZooKeeper Path Structure

```
/mysillydreams/
├── dev/
│   ├── auth-service/          # Auth service configuration
│   ├── user-service/          # User service configuration
│   └── api-gateway/           # API Gateway configuration
├── qa/
│   └── ...
└── prod/
    └── ...
```

### Configuration Loading

Configurations are automatically loaded into ZooKeeper using the `zookeeper-config-loader` job. The auth service reads its configuration from `/mysillydreams/dev/auth-service`.

### Environment Variables

The auth service uses environment variables for:
- Database credentials (from Kubernetes secrets)
- JWT secrets (from Kubernetes secrets)
- Encryption keys (from Kubernetes secrets)
- ZooKeeper connection string
- Environment-specific settings

## Security Features

### 1. Secret Management
- All sensitive data stored in Kubernetes secrets
- No hardcoded passwords or keys
- Base64 encoding for secret values

### 2. Network Security
- Init containers ensure ZooKeeper is ready before service starts
- Proper health checks and readiness probes
- Resource limits and requests defined

### 3. Configuration Security
- Configurations loaded from ZooKeeper at runtime
- Environment-specific configuration paths
- Audit logging enabled

## Monitoring and Health Checks

### Health Endpoints
- **Liveness**: `/actuator/health` (checks if service is alive)
- **Readiness**: `/actuator/health/readiness` (checks if service is ready to serve traffic)
- **Startup**: `/actuator/health` (checks during startup phase)

### Monitoring
- Prometheus metrics exposed at `/actuator/prometheus`
- Zipkin tracing integration
- Comprehensive logging with structured format

## Troubleshooting

### Common Issues

1. **Auth Service fails to start**
   ```bash
   # Check ZooKeeper connectivity
   kubectl exec -n mysillydreams-dev deployment/auth-service -- nc -z zookeeper.mysillydreams-dev 2181
   
   # Check configuration in ZooKeeper
   kubectl exec -n mysillydreams-dev deployment/zookeeper -- \
     /opt/kafka/bin/kafka-run-class.sh org.apache.zookeeper.ZooKeeperMain \
     -server localhost:2181 get /mysillydreams/dev/auth-service
   ```

2. **Configuration not loading**
   ```bash
   # Check config loader job logs
   kubectl logs job/zookeeper-config-loader -n mysillydreams-dev
   
   # Manually verify ZooKeeper paths
   kubectl exec -n mysillydreams-dev deployment/zookeeper -- \
     /opt/kafka/bin/kafka-run-class.sh org.apache.zookeeper.ZooKeeperMain \
     -server localhost:2181 ls /mysillydreams/dev
   ```

3. **Database connection issues**
   ```bash
   # Check database secrets
   kubectl get secret auth-service-db-secret -n mysillydreams-dev -o yaml
   
   # Test database connectivity
   kubectl exec -n mysillydreams-dev deployment/auth-service -- \
     nc -z postgres-auth.mysillydreams-dev 5432
   ```

### Logs and Debugging

```bash
# View auth service logs
kubectl logs -f deployment/auth-service -n mysillydreams-dev

# View ZooKeeper logs
kubectl logs -f deployment/zookeeper -n mysillydreams-dev

# View configuration loader logs
kubectl logs job/zookeeper-config-loader -n mysillydreams-dev

# Check events
kubectl get events -n mysillydreams-dev --sort-by='.lastTimestamp'
```

## Production Checklist

- [ ] All default passwords changed
- [ ] JWT secrets are strong and unique
- [ ] Encryption keys are properly generated
- [ ] Database credentials are secure
- [ ] Resource limits are appropriate for your environment
- [ ] Monitoring is configured
- [ ] Backup strategy is in place
- [ ] Network policies are configured (if required)
- [ ] SSL/TLS certificates are configured (if required)
- [ ] Log aggregation is configured

## Scaling

To scale the auth service:

```bash
# Scale to 3 replicas
kubectl scale deployment auth-service --replicas=3 -n mysillydreams-dev

# Enable horizontal pod autoscaling
kubectl autoscale deployment auth-service --cpu-percent=70 --min=2 --max=10 -n mysillydreams-dev
```

## Cleanup

To remove the deployment:

```bash
kubectl delete -f k8s/06-auth-service.yaml
kubectl delete -f k8s/06-auth-service-secrets.yaml
kubectl delete -f k8s/zookeeper-config-loader.yaml
kubectl delete -f k8s/zookeeper-service-configs.yaml
```
