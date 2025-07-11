# Auth Service Production Deployment Guide

## Overview

This guide provides comprehensive instructions for deploying the Auth Service to production with proper security hardening and monitoring.

## Prerequisites

### Infrastructure Requirements

- **Kubernetes Cluster**: v1.20+
- **PostgreSQL Database**: v12+
- **Keycloak**: v19.0.3+
- **Apache Kafka**: v2.8+
- **Redis** (optional, for distributed rate limiting)
- **Load Balancer**: NGINX/HAProxy with SSL termination

### Security Requirements

- **TLS/SSL Certificates**: Valid certificates for HTTPS
- **Secrets Management**: Kubernetes Secrets or external vault (HashiCorp Vault, AWS Secrets Manager)
- **Network Policies**: Proper network segmentation
- **RBAC**: Role-based access control configured

## Pre-Deployment Checklist

### 1. Environment Variables

Ensure all required environment variables are configured:

```bash
# Database Configuration
DB_HOST=postgres.internal.domain
DB_PORT=5432
DB_NAME=authdb_prod
DB_USER=authuser_prod
DB_PASS=<strong-database-password>

# Keycloak Configuration
KEYCLOAK_URL=https://keycloak.internal.domain/auth
KEYCLOAK_SECRET=<keycloak-client-secret>

# JWT Configuration
JWT_SECRET=<256-bit-jwt-secret>

# Kafka Configuration
KAFKA_BROKER=kafka.internal.domain:9092

# Application Security
APP_SIMPLE_ENCRYPTION_SECRET_KEY=<32-byte-encryption-key>
APP_INTERNAL_API_SECRET_KEY=<strong-internal-api-key>

# CORS Configuration
APP_CORS_ALLOWED_ORIGINS=https://app.mysillydreams.com,https://admin.mysillydreams.com

# MFA Configuration
APP_MFA_ISSUER_NAME=MySillyDreamsPlatform

# Spring Profiles
SPRING_PROFILES_ACTIVE=prod
```

### 2. Database Setup

```sql
-- Create production database
CREATE DATABASE authdb_prod;

-- Create dedicated user
CREATE USER authuser_prod WITH PASSWORD '<strong-password>';

-- Grant necessary permissions
GRANT CONNECT ON DATABASE authdb_prod TO authuser_prod;
GRANT USAGE ON SCHEMA public TO authuser_prod;
GRANT CREATE ON SCHEMA public TO authuser_prod;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO authuser_prod;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO authuser_prod;

-- Set default privileges for future tables
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO authuser_prod;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO authuser_prod;
```

### 3. Keycloak Configuration

1. **Create Realm**: `MySillyDreams-Realm`
2. **Create Client**: `auth-service-client`
   - Client Protocol: `openid-connect`
   - Access Type: `confidential`
   - Service Accounts Enabled: `true`
   - Authorization Enabled: `true`

3. **Configure Client Roles**:
   - Create roles: `ROLE_ADMIN`, `ROLE_USER`, `ROLE_VENDOR`, `ROLE_DELIVERY`

4. **Service Account Permissions**:
   - Assign `manage-users` role from `realm-management` client

## Kubernetes Deployment

### 1. Namespace

```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: auth-service
  labels:
    name: auth-service
```

### 2. Secrets

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: auth-service-secrets
  namespace: auth-service
type: Opaque
data:
  db-password: <base64-encoded-password>
  keycloak-secret: <base64-encoded-secret>
  jwt-secret: <base64-encoded-jwt-secret>
  encryption-key: <base64-encoded-encryption-key>
  internal-api-key: <base64-encoded-internal-api-key>
```

### 3. ConfigMap

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: auth-service-config
  namespace: auth-service
data:
  DB_HOST: "postgres.internal.domain"
  DB_PORT: "5432"
  DB_NAME: "authdb_prod"
  DB_USER: "authuser_prod"
  KEYCLOAK_URL: "https://keycloak.internal.domain/auth"
  KAFKA_BROKER: "kafka.internal.domain:9092"
  APP_CORS_ALLOWED_ORIGINS: "https://app.mysillydreams.com,https://admin.mysillydreams.com"
  APP_MFA_ISSUER_NAME: "MySillyDreamsPlatform"
  SPRING_PROFILES_ACTIVE: "prod"
```

### 4. Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: auth-service
  namespace: auth-service
spec:
  replicas: 3
  selector:
    matchLabels:
      app: auth-service
  template:
    metadata:
      labels:
        app: auth-service
    spec:
      containers:
      - name: auth-service
        image: mysillydreams/auth-service:latest
        ports:
        - containerPort: 8080
        env:
        - name: DB_PASS
          valueFrom:
            secretKeyRef:
              name: auth-service-secrets
              key: db-password
        - name: KEYCLOAK_SECRET
          valueFrom:
            secretKeyRef:
              name: auth-service-secrets
              key: keycloak-secret
        - name: JWT_SECRET
          valueFrom:
            secretKeyRef:
              name: auth-service-secrets
              key: jwt-secret
        - name: APP_SIMPLE_ENCRYPTION_SECRET_KEY
          valueFrom:
            secretKeyRef:
              name: auth-service-secrets
              key: encryption-key
        - name: APP_INTERNAL_API_SECRET_KEY
          valueFrom:
            secretKeyRef:
              name: auth-service-secrets
              key: internal-api-key
        envFrom:
        - configMapRef:
            name: auth-service-config
        resources:
          requests:
            memory: "512Mi"
            cpu: "250m"
          limits:
            memory: "1Gi"
            cpu: "500m"
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 30
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
        securityContext:
          runAsNonRoot: true
          runAsUser: 1000
          allowPrivilegeEscalation: false
          readOnlyRootFilesystem: true
          capabilities:
            drop:
            - ALL
```

### 5. Service

```yaml
apiVersion: v1
kind: Service
metadata:
  name: auth-service
  namespace: auth-service
spec:
  selector:
    app: auth-service
  ports:
  - port: 80
    targetPort: 8080
    protocol: TCP
  type: ClusterIP
```

### 6. Ingress

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: auth-service-ingress
  namespace: auth-service
  annotations:
    nginx.ingress.kubernetes.io/ssl-redirect: "true"
    nginx.ingress.kubernetes.io/force-ssl-redirect: "true"
    nginx.ingress.kubernetes.io/rate-limit: "100"
    nginx.ingress.kubernetes.io/rate-limit-window: "1m"
spec:
  tls:
  - hosts:
    - auth.mysillydreams.com
    secretName: auth-service-tls
  rules:
  - host: auth.mysillydreams.com
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: auth-service
            port:
              number: 80
```

## Security Hardening

### 1. Network Policies

```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: auth-service-network-policy
  namespace: auth-service
spec:
  podSelector:
    matchLabels:
      app: auth-service
  policyTypes:
  - Ingress
  - Egress
  ingress:
  - from:
    - namespaceSelector:
        matchLabels:
          name: ingress-nginx
    ports:
    - protocol: TCP
      port: 8080
  egress:
  - to:
    - namespaceSelector:
        matchLabels:
          name: database
    ports:
    - protocol: TCP
      port: 5432
  - to:
    - namespaceSelector:
        matchLabels:
          name: keycloak
    ports:
    - protocol: TCP
      port: 8080
  - to:
    - namespaceSelector:
        matchLabels:
          name: kafka
    ports:
    - protocol: TCP
      port: 9092
```

### 2. Pod Security Policy

```yaml
apiVersion: policy/v1beta1
kind: PodSecurityPolicy
metadata:
  name: auth-service-psp
spec:
  privileged: false
  allowPrivilegeEscalation: false
  requiredDropCapabilities:
    - ALL
  volumes:
    - 'configMap'
    - 'emptyDir'
    - 'projected'
    - 'secret'
    - 'downwardAPI'
    - 'persistentVolumeClaim'
  runAsUser:
    rule: 'MustRunAsNonRoot'
  seLinux:
    rule: 'RunAsAny'
  fsGroup:
    rule: 'RunAsAny'
```

## Monitoring and Observability

### 1. Prometheus Monitoring

Add Prometheus annotations to the deployment:

```yaml
metadata:
  annotations:
    prometheus.io/scrape: "true"
    prometheus.io/port: "8080"
    prometheus.io/path: "/actuator/prometheus"
```

### 2. Logging Configuration

Configure centralized logging with ELK stack or similar:

```yaml
spec:
  template:
    spec:
      containers:
      - name: auth-service
        env:
        - name: LOGGING_LEVEL_ROOT
          value: "WARN"
        - name: LOGGING_LEVEL_COM_MYSILLYDREAMS_AUTH
          value: "INFO"
```

### 3. Health Checks

Configure comprehensive health checks:

```yaml
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8080
  initialDelaySeconds: 60
  periodSeconds: 30
  timeoutSeconds: 5
  failureThreshold: 3

readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8080
  initialDelaySeconds: 30
  periodSeconds: 10
  timeoutSeconds: 5
  failureThreshold: 3
```

## Post-Deployment Verification

### 1. Health Check

```bash
curl -k https://auth.mysillydreams.com/actuator/health
```

### 2. API Functionality

```bash
# Test login endpoint
curl -X POST https://auth.mysillydreams.com/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","password":"testpass"}'
```

### 3. Security Headers

```bash
curl -I https://auth.mysillydreams.com/actuator/health
```

Verify presence of security headers:
- `Strict-Transport-Security`
- `X-Content-Type-Options`
- `X-Frame-Options`
- `Content-Security-Policy`

## Backup and Recovery

### 1. Database Backup

```bash
# Daily backup script
pg_dump -h postgres.internal.domain -U authuser_prod authdb_prod > auth_backup_$(date +%Y%m%d).sql
```

### 2. Configuration Backup

```bash
# Backup Kubernetes configurations
kubectl get all,secrets,configmaps -n auth-service -o yaml > auth-service-backup.yaml
```

## Troubleshooting

### Common Issues

1. **Database Connection Issues**
   - Check database credentials
   - Verify network connectivity
   - Check database server status

2. **Keycloak Integration Issues**
   - Verify Keycloak URL and credentials
   - Check client configuration
   - Validate service account permissions

3. **JWT Token Issues**
   - Verify JWT secret configuration
   - Check token expiration settings
   - Validate token signing algorithm

### Logs Analysis

```bash
# View application logs
kubectl logs -f deployment/auth-service -n auth-service

# View specific pod logs
kubectl logs -f <pod-name> -n auth-service
```

## Maintenance

### 1. Regular Updates

- Monitor security advisories
- Update dependencies regularly
- Test updates in staging environment

### 2. Secret Rotation

- Rotate JWT secrets quarterly
- Rotate database passwords annually
- Rotate API keys as needed

### 3. Performance Monitoring

- Monitor response times
- Track error rates
- Monitor resource usage

## Support

For production support issues:
1. Check application logs
2. Verify infrastructure status
3. Contact the development team
4. Escalate to platform team if needed
