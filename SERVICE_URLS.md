# Service URLs - Microservices Platform

## ⚠️ **IMPORTANT: Minikube Service Access**
Since you're using minikube with Docker driver on Windows, services are accessible through tunnels.
**You need to run the minikube service commands to get the actual URLs:**

```bash
# Get service URLs (run these commands to get current tunnel URLs):
minikube service eureka-nodeport -n mysillydreams --url
minikube service auth-nodeport -n mysillydreams --url  
minikube service user-nodeport -n mysillydreams --url
minikube service keycloak-nodeport -n mysillydreams --url
minikube service admin-nodeport -n mysillydreams --url
minikube service vault-nodeport -n mysillydreams --url
minikube service zipkin-nodeport -n mysillydreams --url
```

## ✅ **Current Working URLs** (as of last check):

### Eureka Service Discovery
- **URL**: http://127.0.0.1:53417 *(tunnel URL - may change)*
- **Description**: Service registry and discovery
- **Status**: ✅ Working

### Auth Service  
- **URL**: http://127.0.0.1:53476 *(tunnel URL - may change)*
- **Description**: Authentication and authorization service
- **Health Check**: http://127.0.0.1:53476/actuator/health
- **API Documentation**: http://127.0.0.1:53476/swagger-ui.html
- **Status**: ✅ Working

### User Service
- **URL**: http://127.0.0.1:53511 *(tunnel URL - may change)*
- **Description**: User management service with Vault encryption
- **Health Check**: http://127.0.0.1:53511/actuator/health
- **API Documentation**: http://127.0.0.1:53511/swagger-ui.html
- **Status**: ✅ Working

## Infrastructure Services

### Keycloak (Identity Provider)
- **URL**: http://127.0.0.1:53550 *(tunnel URL - may change)*
- **Description**: Identity and access management
- **Admin Console**: http://127.0.0.1:53550/admin
- **Health Check**: http://127.0.0.1:53550/health
- **Default Credentials**: admin/admin123
- **Status**: ✅ Working

### Spring Boot Admin
- **URL**: http://127.0.0.1:53581 *(tunnel URL - may change)*
- **Description**: Application monitoring and management
- **Features**: Health checks, metrics, logs, environment info
- **Status**: ✅ Working

### Vault (Secrets Management)
- **URL**: http://127.0.0.1:53636 *(tunnel URL - may change)*
- **Description**: Secrets and encryption management
- **Health Check**: http://127.0.0.1:53636/v1/sys/health
- **Status**: ✅ Working

### Zipkin (Distributed Tracing)
- **URL**: http://127.0.0.1:53737 *(tunnel URL - may change)*
- **Description**: Distributed tracing system
- **Features**: Request tracing, performance monitoring
- **Status**: ✅ Working

## 🔧 **How to Access Services**

1. **Keep terminals open**: Each minikube service command opens a tunnel that must stay running
2. **URLs change**: Tunnel URLs change each time you restart the tunnels
3. **Multiple terminals**: You can run multiple service tunnels simultaneously
4. **Browser access**: Open the tunnel URLs directly in your browser

## 🎯 **Quick Test Commands**

```bash
# Test all services health
curl http://127.0.0.1:53476/actuator/health  # Auth Service
curl http://127.0.0.1:53511/actuator/health  # User Service  
curl http://127.0.0.1:53550/health           # Keycloak
curl http://127.0.0.1:53636/v1/sys/health    # Vault
```

## 📊 **System Status: ALL SERVICES WORKING** ✅

- ✅ Keycloak: Fixed and running (modern version 23.0.7)
- ✅ User Service: Fixed Vault integration, running with encryption
- ✅ Auth Service: Working perfectly
- ✅ Vault: Production-ready with transit encryption
- ✅ All infrastructure services: Operational
- ✅ Service discovery: All services registered with Eureka
- ✅ Network connectivity: Resolved via minikube tunnels
