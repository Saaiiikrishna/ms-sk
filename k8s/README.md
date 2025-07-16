# MySillyDreams Kubernetes Manifests

## 📁 Directory Organization

This directory contains the **production-ready** Kubernetes manifests for the MySillyDreams microservices architecture. All outdated, duplicate, and experimental files have been moved to the `mreview/` folder.

## 🚀 Essential Production Manifests

### Core Infrastructure (Deploy in Order)

1. **`00-namespace.yaml`** - Creates the `mysillydreams-dev` namespace
2. **`01-postgresql.yaml`** - PostgreSQL database for auth service
3. **`02-redis.yaml`** - Redis for caching and session storage
4. **`03-keycloak.yaml`** - Keycloak authentication provider
5. **`04-vault.yaml`** - HashiCorp Vault for secrets management
6. **`05-zookeeper-service.yaml`** - ZooKeeper for configuration management

### Service Discovery & Monitoring

7. **`09-eureka-server.yaml`** - Eureka service discovery
8. **`11-zipkin.yaml`** - Distributed tracing

### Application Services

9. **`06-auth-service-secrets.yaml`** - Auth service secrets
10. **`06-auth-service.yaml`** - Authentication service
11. **`07-user-service-production.yaml`** - User management service (with Vault integration)
12. **`08-api-gateway-fixed.yaml`** - API Gateway (with CORS configuration)
13. **`09-admin-server-fixed.yaml`** - Admin management service

### Configuration

14. **`zookeeper-service-configs.yaml`** - All microservice configurations for ZooKeeper

## 📋 Deployment Order

```bash
# 1. Infrastructure
kubectl apply -f 00-namespace.yaml
kubectl apply -f 01-postgresql.yaml
kubectl apply -f 02-redis.yaml
kubectl apply -f 03-keycloak.yaml
kubectl apply -f 04-vault.yaml
kubectl apply -f 05-zookeeper-service.yaml

# 2. Configuration
kubectl apply -f zookeeper-service-configs.yaml

# 3. Service Discovery & Monitoring
kubectl apply -f 09-eureka-server.yaml
kubectl apply -f 11-zipkin.yaml

# 4. Application Services
kubectl apply -f 06-auth-service-secrets.yaml
kubectl apply -f 06-auth-service.yaml
kubectl apply -f 07-user-service-production.yaml
kubectl apply -f 08-api-gateway-fixed.yaml
kubectl apply -f 09-admin-server-fixed.yaml
```

## 🏗️ Architecture Overview

- **Namespace**: `mysillydreams-dev`
- **Configuration Management**: ZooKeeper-based centralized configuration
- **Service Discovery**: Eureka Server
- **Authentication**: Keycloak + JWT
- **Secrets Management**: HashiCorp Vault (for user service)
- **Database**: PostgreSQL (for auth service)
- **Caching**: Redis
- **Monitoring**: Zipkin for distributed tracing

## 📚 Documentation

- **`AUTH-SERVICE-DEPLOYMENT-GUIDE.md`** - Detailed auth service deployment guide

## 🗂️ Archived Files

The `mreview/` folder contains:
- Duplicate manifest versions
- Old configuration files
- Deployment scripts
- Experimental configurations
- Temporary utility files

These files are kept for reference but are not part of the current production deployment.

## ✅ Current Status

All services are deployed and operational:
- ✅ Infrastructure services running
- ✅ ZooKeeper configuration management active
- ✅ All microservices deployed with proper service discovery
- ✅ Frontend-backend integration working
- ✅ CSRF protection configured in API Gateway

## 🔧 Configuration Management

All service configurations are centrally managed through ZooKeeper using the `zookeeper-service-configs.yaml` file. This includes:
- Database connections
- Service discovery settings
- Security configurations
- CORS settings
- Monitoring configurations

## 🚀 Next Steps

1. Deploy CSRF fix for API Gateway
2. Test admin authentication
3. Complete admin creation workflow
