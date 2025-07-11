# MySillyDreams Platform - Microservices Architecture

A comprehensive microservices platform built with Spring Boot 3.2.0, featuring service discovery, distributed tracing, caching, and monitoring.

## 🏗️ Architecture Overview

### Services
- **Auth Service** (Port 8081) - Authentication and authorization with Keycloak integration
- **User Service** (Port 8082) - User management and profiles
- **Eureka Server** (Port 8761) - Service discovery
- **Admin Server** (Port 8080) - Spring Boot Admin for monitoring
- **Keycloak** (Port 8180) - Identity and access management
- **Zipkin** (Port 9411) - Distributed tracing
- **Redis** (Port 6379) - Caching layer
- **PostgreSQL** - Separate databases for each service

### Key Features
- ✅ **Service Discovery** with Eureka
- ✅ **Distributed Tracing** with Zipkin
- ✅ **Production-level Redis** caching
- ✅ **Spring Boot Admin** monitoring
- ✅ **Separate databases** per service
- ✅ **Keycloak integration** for authentication
- ✅ **Kubernetes deployment** ready
- ✅ **Docker containerization**
- ✅ **Comprehensive actuator endpoints**

## 🚀 Quick Start

### Prerequisites
- Java 17+
- Docker Desktop
- Minikube
- kubectl
- PowerShell (for Windows deployment script)

### Kubernetes Deployment (Recommended)

1. **Start Minikube**
```bash
minikube start
```

2. **Deploy using PowerShell script**
```powershell
# Build images and deploy everything
.\deploy.ps1 -BuildImages

# Deploy only (if images already built)
.\deploy.ps1 -DeployOnly

# Clean and redeploy
.\deploy.ps1 -Clean -BuildImages
```

3. **Access services**
```bash
# Get Minikube IP
minikube ip

# Services will be available at:
# Eureka: http://<minikube-ip>:30761
# Admin: http://<minikube-ip>:30080
# Keycloak: http://<minikube-ip>:30180
# Zipkin: http://<minikube-ip>:30411
# Auth: http://<minikube-ip>:30081
# User: http://<minikube-ip>:30082
```

### Local Development Setup

1. **Start infrastructure services**
```bash
# Start PostgreSQL instances
docker run -d --name postgres-auth -e POSTGRES_DB=authdb -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=0000 -p 5432:5432 postgres:15-alpine
docker run -d --name postgres-user -e POSTGRES_DB=userdb -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=0000 -p 5433:5432 postgres:15-alpine
docker run -d --name postgres-keycloak -e POSTGRES_DB=keycloak -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=0000 -p 5434:5432 postgres:15-alpine

# Start Redis
docker run -d --name redis -p 6379:6379 redis:7-alpine

# Start Zipkin
docker run -d --name zipkin -p 9411:9411 openzipkin/zipkin:latest

# Start Keycloak
docker run -d --name keycloak -p 8180:8080 -e KEYCLOAK_ADMIN=admin -e KEYCLOAK_ADMIN_PASSWORD=admin123 -e KC_DB=postgres -e KC_DB_URL=jdbc:postgresql://host.docker.internal:5434/keycloak -e KC_DB_USERNAME=postgres -e KC_DB_PASSWORD=0000 quay.io/keycloak/keycloak:latest start-dev
```

2. **Start services in order**
```bash
# 1. Start Eureka Server
cd eureka-server && ./mvnw spring-boot:run &

# 2. Start Admin Server
cd admin-server && ./mvnw spring-boot:run &

# 3. Start Auth Service
cd auth-service && ./mvnw spring-boot:run &

# 4. Start User Service
cd user-service && ./mvnw spring-boot:run &
```

## 📊 Monitoring and Observability

### Spring Boot Admin
- **URL**: http://localhost:8080 (local) or http://<minikube-ip>:30080 (k8s)
- **Features**:
  - Application health monitoring
  - JVM metrics
  - Environment properties
  - Log file viewing
  - Thread dumps
  - HTTP traces

### Zipkin Distributed Tracing
- **URL**: http://localhost:9411 (local) or http://<minikube-ip>:30411 (k8s)
- **Features**:
  - Request tracing across services
  - Performance analysis
  - Dependency mapping
  - Error tracking

### Eureka Service Discovery
- **URL**: http://localhost:8761 (local) or http://<minikube-ip>:30761 (k8s)
- **Features**:
  - Service registration
  - Health checks
  - Load balancing
  - Service discovery

### Keycloak Identity Management
- **URL**: http://localhost:8180 (local) or http://<minikube-ip>:30180 (k8s)
- **Admin Credentials**: admin/admin123
- **Features**:
  - User authentication
  - Role-based access control
  - OAuth2/OpenID Connect
  - Admin console

## 🔧 Configuration

### Database Configuration
Each service has its own database:
- **Auth Service**: `authdb` on postgres-auth-service:5432
- **User Service**: `userdb` on postgres-user-service:5432
- **Keycloak**: `keycloak` on postgres-keycloak-service:5432

### Redis Configuration
- **Auth Service**: Database 0
- **User Service**: Database 1
- **Features**: Connection pooling, TTL management, production-ready settings

### Actuator Endpoints
All services expose comprehensive actuator endpoints:
- `/actuator/health` - Health status
- `/actuator/info` - Application information
- `/actuator/metrics` - Application metrics
- `/actuator/env` - Environment properties
- `/actuator/loggers` - Logger configuration
- `/actuator/threaddump` - Thread dump
- `/actuator/heapdump` - Heap dump

## 🐳 Docker Images

### Building Images
```bash
# Auth Service
cd auth-service && docker build -t mysillydreams/auth-service:latest .

# User Service
cd user-service && docker build -t mysillydreams/user-service:latest .

# Eureka Server
cd eureka-server && docker build -t mysillydreams/eureka-server:latest .

# Admin Server
cd admin-server && docker build -t mysillydreams/admin-server:latest .
```

## ☸️ Kubernetes Resources

### Deployment Order
1. **Infrastructure**: PostgreSQL, Redis, Zipkin
2. **Keycloak**: Identity management
3. **Service Discovery**: Eureka Server
4. **Monitoring**: Spring Boot Admin
5. **Applications**: Auth Service, User Service

### Storage
- PostgreSQL with persistent volumes for each service
- Redis with persistent storage
- Separate PVCs for each database

### Services
- ClusterIP services for internal communication
- NodePort services for external access
- Load balancing across replicas

### ConfigMaps and Secrets
- Environment-specific configuration
- Database credentials
- Service URLs
- Keycloak configuration

## 🔍 Troubleshooting

### Common Issues

1. **Service not registering with Eureka**
   ```bash
   kubectl logs -f deployment/eureka-server -n mysillydreams
   kubectl logs -f deployment/auth-service -n mysillydreams
   ```

2. **Database connection issues**
   ```bash
   kubectl get pods -n mysillydreams
   kubectl logs -f deployment/postgres-auth -n mysillydreams
   ```

3. **Keycloak not starting**
   ```bash
   kubectl logs -f deployment/keycloak -n mysillydreams
   kubectl logs -f deployment/postgres-keycloak -n mysillydreams
   ```

### Health Checks
```bash
# Check all pods
kubectl get pods -n mysillydreams

# Check services
kubectl get services -n mysillydreams

# Check deployments
kubectl get deployments -n mysillydreams

# Check logs
kubectl logs -f deployment/<service-name> -n mysillydreams
```

## 📈 Performance Tuning

### JVM Settings
- Heap size: 512Mi-1Gi per service
- GC: G1GC recommended for production
- Monitoring: JFR enabled

### Database
- Connection pooling configured
- Separate databases for isolation
- Optimized for read/write patterns

### Redis
- Connection pooling
- Appropriate TTL settings
- Memory optimization

### Keycloak
- Database backend for persistence
- Optimized for authentication workloads
- Clustering support for production

## 🔐 Security

### Authentication
- Keycloak integration for centralized auth
- JWT token-based authentication
- MFA support for admin users
- OAuth2/OpenID Connect protocols

### Network Security
- Service-to-service communication
- Kubernetes network policies
- Secure secrets management
- TLS termination at ingress

## 🚀 Deployment Strategies

### Development
- Local Docker Compose
- Individual service startup
- Hot reloading support

### Production
- Kubernetes deployment
- Rolling updates
- Health checks and readiness probes
- Resource limits and requests
- Horizontal pod autoscaling

## 📚 API Documentation

### Swagger/OpenAPI
- Auth Service: http://localhost:8081/swagger-ui.html
- User Service: http://localhost:8082/swagger-ui.html

### Key Endpoints
- **Auth Service**: `/api/auth/login`, `/api/auth/register`
- **User Service**: `/api/users`, `/api/users/{id}`
- **Health checks**: `/actuator/health`
- **Metrics**: `/actuator/metrics`

## 🎯 Next Steps

1. **Security Hardening**
   - Implement proper TLS certificates
   - Configure network policies
   - Set up proper RBAC

2. **Monitoring Enhancement**
   - Add Prometheus metrics
   - Set up Grafana dashboards
   - Configure alerting

3. **CI/CD Pipeline**
   - GitHub Actions workflows
   - Automated testing
   - Deployment automation

4. **Additional Services**
   - API Gateway
   - Message queuing
   - File storage service

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests
5. Submit a pull request

## 📄 License

This project is licensed under the MIT License - see the LICENSE file for details.