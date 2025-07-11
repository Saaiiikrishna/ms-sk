# Auth Service Testing Guide

## Overview

This guide provides step-by-step instructions for building, testing, and validating the Auth Service in different environments.

## Prerequisites

### Option 1: Local Development (Recommended)
- **Java 17+**: OpenJDK or Oracle JDK
- **Maven 3.6+**: For building and dependency management
- **Docker Desktop**: For containerized testing
- **PowerShell**: For running test scripts (Windows)

### Option 2: Docker-Only Testing
- **Docker Desktop**: Only requirement for containerized testing
- **PowerShell**: For running test scripts (Windows)

## Quick Start

### 1. Automated Setup (Recommended)

Run the automated build and test script:

```powershell
# Navigate to auth-service directory
cd ms-sk/auth-service

# Install Java and Maven (if not already installed)
.\build-and-test.ps1 -InstallJava

# Build and test everything
.\build-and-test.ps1 -All
```

### 2. Manual Setup

#### Step 2.1: Install Dependencies

**Install Java 17:**
```powershell
# Using Chocolatey (recommended)
choco install openjdk17 -y

# Or download from: https://adoptium.net/
```

**Install Maven:**
```powershell
# Using Chocolatey
choco install maven -y

# Or download from: https://maven.apache.org/download.cgi
```

**Verify Installation:**
```powershell
java -version
mvn -version
```

#### Step 2.2: Build the Project

```powershell
# Navigate to auth-service directory
cd ms-sk/auth-service

# Clean and compile
mvn clean compile

# Run tests
mvn test

# Build JAR
mvn package -DskipTests
```

## Testing Strategies

### 1. Unit and Integration Tests

```powershell
# Run unit tests only
mvn test

# Run integration tests
mvn verify

# Run tests with coverage
mvn test jacoco:report
```

### 2. Docker-Based Testing

#### Option A: Build and Test with Docker

```powershell
# Build Docker image
docker build -t auth-service:latest .

# Run container
docker run -d -p 8080:8080 --name auth-service-test auth-service:latest

# Wait for startup (30-60 seconds)
Start-Sleep -Seconds 60

# Test endpoints
.\test-endpoints.ps1

# Stop and remove container
docker stop auth-service-test
docker rm auth-service-test
```

#### Option B: Full Stack with Docker Compose

```powershell
# Start all services (PostgreSQL, Keycloak, Kafka, Auth Service)
docker-compose -f docker-compose.test.yml up -d

# Wait for all services to start (2-3 minutes)
Start-Sleep -Seconds 180

# Check service health
docker-compose -f docker-compose.test.yml ps

# Test endpoints
.\test-endpoints.ps1

# View logs
docker-compose -f docker-compose.test.yml logs auth-service

# Stop all services
docker-compose -f docker-compose.test.yml down
```

### 3. Endpoint Testing

The `test-endpoints.ps1` script provides comprehensive endpoint testing:

```powershell
# Test with default settings
.\test-endpoints.ps1

# Test with custom base URL
.\test-endpoints.ps1 -BaseUrl "http://localhost:8080"

# Test with verbose output
.\test-endpoints.ps1 -Verbose

# Test with custom internal API key
.\test-endpoints.ps1 -InternalApiKey "your-custom-key"
```

## Test Scenarios Covered

### 1. Health and Monitoring
- ✅ Health check endpoint
- ✅ Service info endpoint
- ✅ Metrics endpoint (if enabled)

### 2. Authentication Endpoints
- ✅ Login with invalid credentials (expected failure)
- ✅ Login with empty credentials (expected failure)
- ✅ Token validation without token (expected failure)
- ✅ Token validation with invalid token (expected failure)
- ✅ Token refresh with invalid token (expected failure)

### 3. Security Features
- ✅ Rate limiting (5 attempts per 15 minutes)
- ✅ Security headers validation
- ✅ CORS configuration
- ✅ Input validation

### 4. MFA Endpoints
- ✅ MFA setup without authentication (expected failure)
- ✅ MFA verification without authentication (expected failure)

### 5. Internal API
- ✅ Internal endpoints without API key (expected failure)
- ✅ Internal endpoints with invalid API key (expected failure)
- ✅ Internal endpoints with valid API key (should work)

### 6. Documentation
- ✅ OpenAPI JSON documentation
- ✅ Swagger UI accessibility

## Expected Test Results

### Successful Test Run
```
=== TEST RESULTS SUMMARY ===
Total Tests: 15
Passed: 15
Failed: 0

🎉 All tests passed! The Auth Service is working correctly.
```

### Common Issues and Solutions

#### Issue: Java/Maven Not Found
```
Error: Java and Maven are required for building.
```
**Solution:** Run `.\build-and-test.ps1 -InstallJava`

#### Issue: Docker Not Running
```
Error: Docker is not running. Please start Docker Desktop.
```
**Solution:** Start Docker Desktop and wait for it to fully initialize

#### Issue: Port Already in Use
```
Error: Port 8080 is already in use
```
**Solution:** 
```powershell
# Find and kill process using port 8080
netstat -ano | findstr :8080
taskkill /PID <process-id> /F
```

#### Issue: Database Connection Failed
```
Error: Connection to database failed
```
**Solution:** Ensure PostgreSQL is running and accessible:
```powershell
# Check Docker containers
docker ps

# Check logs
docker-compose -f docker-compose.test.yml logs postgres
```

## Performance Testing

### Load Testing with curl

```powershell
# Test health endpoint performance
for ($i=1; $i -le 100; $i++) {
    $response = Invoke-RestMethod -Uri "http://localhost:8080/actuator/health"
    Write-Host "Request $i - Status: $($response.status)"
}
```

### Memory and CPU Monitoring

```powershell
# Monitor Docker container resources
docker stats auth-service

# Monitor Java process (if running locally)
jps -v
```

## Integration with External Services

### Keycloak Setup (for Full Integration Testing)

1. **Access Keycloak Admin Console:**
   - URL: http://localhost:8081
   - Username: admin
   - Password: admin123

2. **Create Realm:**
   - Name: MySillyDreams-Realm

3. **Create Client:**
   - Client ID: auth-service-client
   - Client Protocol: openid-connect
   - Access Type: confidential

4. **Configure Client:**
   - Service Accounts Enabled: true
   - Authorization Enabled: true

### Kafka Testing

```powershell
# Connect to Kafka container
docker exec -it auth-kafka bash

# List topics
kafka-topics --bootstrap-server localhost:9092 --list

# Create test topic
kafka-topics --bootstrap-server localhost:9092 --create --topic auth.events --partitions 1 --replication-factor 1

# Consume messages
kafka-console-consumer --bootstrap-server localhost:9092 --topic auth.events --from-beginning
```

## Continuous Integration

### GitHub Actions Example

```yaml
name: Auth Service CI

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    
    steps:
    - uses: actions/checkout@v3
    
    - name: Set up JDK 17
      uses: actions/setup-java@v3
      with:
        java-version: '17'
        distribution: 'temurin'
    
    - name: Cache Maven dependencies
      uses: actions/cache@v3
      with:
        path: ~/.m2
        key: ${{ runner.os }}-m2-${{ hashFiles('**/pom.xml') }}
    
    - name: Run tests
      run: mvn clean verify
    
    - name: Build Docker image
      run: docker build -t auth-service:${{ github.sha }} .
```

## Troubleshooting

### Debug Mode

Enable debug logging by setting environment variables:

```powershell
$env:LOGGING_LEVEL_COM_MYSILLYDREAMS_AUTH = "DEBUG"
$env:LOGGING_LEVEL_ROOT = "DEBUG"
```

### Common Log Locations

- **Local Development:** Console output
- **Docker:** `docker logs auth-service`
- **Docker Compose:** `docker-compose logs auth-service`

### Health Check URLs

- **Health:** http://localhost:8080/actuator/health
- **Info:** http://localhost:8080/actuator/info
- **Metrics:** http://localhost:8080/actuator/metrics
- **OpenAPI:** http://localhost:8080/v3/api-docs
- **Swagger UI:** http://localhost:8080/swagger-ui.html

## Next Steps

1. **Run the automated tests** using the provided scripts
2. **Verify all endpoints** are working correctly
3. **Check security features** are properly implemented
4. **Test with real Keycloak integration** if needed
5. **Deploy to staging environment** for further testing
6. **Set up monitoring and alerting** for production

## Support

For issues or questions:
1. Check the logs for error messages
2. Verify all dependencies are properly installed
3. Ensure all required environment variables are set
4. Consult the API documentation for endpoint specifications
5. Contact the development team for additional support
