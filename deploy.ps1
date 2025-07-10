# MySillyDreams Platform Deployment Script
# This script builds Docker images and deploys to Kubernetes

param(
    [switch]$BuildImages = $false,
    [switch]$DeployOnly = $false,
    [switch]$Clean = $false
)

Write-Host "🚀 MySillyDreams Platform Deployment Script" -ForegroundColor Green

# Check if Docker is running
try {
    docker version | Out-Null
    Write-Host "✅ Docker is running" -ForegroundColor Green
} catch {
    Write-Host "❌ Docker is not running. Please start Docker Desktop." -ForegroundColor Red
    exit 1
}

# Check if Minikube is running
try {
    minikube status | Out-Null
    Write-Host "✅ Minikube is running" -ForegroundColor Green
} catch {
    Write-Host "❌ Minikube is not running. Starting Minikube..." -ForegroundColor Yellow
    minikube start
}

# Set Docker environment to use Minikube's Docker daemon
Write-Host "🔧 Setting Docker environment to Minikube..." -ForegroundColor Yellow
& minikube docker-env --shell powershell | Invoke-Expression

if ($Clean) {
    Write-Host "🧹 Cleaning up existing deployments..." -ForegroundColor Yellow
    kubectl delete namespace mysillydreams --ignore-not-found=true
    Write-Host "✅ Cleanup completed" -ForegroundColor Green
    if (-not $BuildImages -and -not $DeployOnly) {
        exit 0
    }
}

if ($BuildImages -or (-not $DeployOnly)) {
    Write-Host "🏗️ Building Docker images..." -ForegroundColor Yellow
    
    # Build Auth Service
    Write-Host "Building Auth Service..." -ForegroundColor Cyan
    Set-Location auth-service
    docker build -t mysillydreams/auth-service:latest .
    if ($LASTEXITCODE -ne 0) {
        Write-Host "❌ Failed to build Auth Service" -ForegroundColor Red
        exit 1
    }
    Set-Location ..
    
    # Build User Service
    Write-Host "Building User Service..." -ForegroundColor Cyan
    Set-Location user-service
    docker build -t mysillydreams/user-service:latest .
    if ($LASTEXITCODE -ne 0) {
        Write-Host "❌ Failed to build User Service" -ForegroundColor Red
        exit 1
    }
    Set-Location ..
    
    # Build Eureka Server
    Write-Host "Building Eureka Server..." -ForegroundColor Cyan
    Set-Location eureka-server
    docker build -t mysillydreams/eureka-server:latest .
    if ($LASTEXITCODE -ne 0) {
        Write-Host "❌ Failed to build Eureka Server" -ForegroundColor Red
        exit 1
    }
    Set-Location ..
    
    # Build Admin Server
    Write-Host "Building Admin Server..." -ForegroundColor Cyan
    Set-Location admin-server
    docker build -t mysillydreams/admin-server:latest .
    if ($LASTEXITCODE -ne 0) {
        Write-Host "❌ Failed to build Admin Server" -ForegroundColor Red
        exit 1
    }
    Set-Location ..
    
    Write-Host "✅ All Docker images built successfully" -ForegroundColor Green
}

if (-not $BuildImages -or $DeployOnly) {
    Write-Host "🚀 Deploying to Kubernetes..." -ForegroundColor Yellow
    
    # Create namespace and configmaps
    Write-Host "Creating namespace and configuration..." -ForegroundColor Cyan
    kubectl apply -f k8s/namespace.yaml
    kubectl apply -f k8s/configmap.yaml
    
    # Deploy infrastructure services
    Write-Host "Deploying infrastructure services..." -ForegroundColor Cyan
    kubectl apply -f k8s/postgres-auth.yaml
    kubectl apply -f k8s/postgres-user.yaml
    kubectl apply -f k8s/redis.yaml
    kubectl apply -f k8s/zipkin.yaml
    kubectl apply -f k8s/keycloak.yaml
    
    # Wait for databases and infrastructure to be ready
    Write-Host "Waiting for infrastructure services to be ready..." -ForegroundColor Cyan
    kubectl wait --for=condition=ready pod -l app=postgres-auth -n mysillydreams --timeout=300s
    kubectl wait --for=condition=ready pod -l app=postgres-user -n mysillydreams --timeout=300s
    kubectl wait --for=condition=ready pod -l app=postgres-keycloak -n mysillydreams --timeout=300s
    kubectl wait --for=condition=ready pod -l app=redis -n mysillydreams --timeout=300s
    kubectl wait --for=condition=ready pod -l app=zipkin -n mysillydreams --timeout=300s

    # Wait for Keycloak to be ready
    Write-Host "Waiting for Keycloak to be ready..." -ForegroundColor Cyan
    kubectl wait --for=condition=ready pod -l app=keycloak -n mysillydreams --timeout=600s
    
    # Deploy service discovery
    Write-Host "Deploying Eureka Server..." -ForegroundColor Cyan
    kubectl apply -f k8s/eureka-server.yaml
    kubectl wait --for=condition=ready pod -l app=eureka-server -n mysillydreams --timeout=300s
    
    # Deploy admin server
    Write-Host "Deploying Admin Server..." -ForegroundColor Cyan
    kubectl apply -f k8s/admin-server.yaml
    kubectl wait --for=condition=ready pod -l app=admin-server -n mysillydreams --timeout=300s
    
    # Deploy application services
    Write-Host "Deploying application services..." -ForegroundColor Cyan
    kubectl apply -f k8s/auth-service.yaml
    kubectl apply -f k8s/user-service.yaml
    
    Write-Host "✅ Deployment completed successfully" -ForegroundColor Green
}

# Display service URLs
Write-Host "`n🌐 Service URLs:" -ForegroundColor Green
$minikubeIp = minikube ip
Write-Host "Eureka Server: http://$minikubeIp`:30761" -ForegroundColor Cyan
Write-Host "Spring Boot Admin: http://$minikubeIp`:30080" -ForegroundColor Cyan
Write-Host "Keycloak Admin: http://$minikubeIp`:30180 (admin/admin123)" -ForegroundColor Cyan
Write-Host "Zipkin UI: http://$minikubeIp`:30411" -ForegroundColor Cyan
Write-Host "Auth Service: http://$minikubeIp`:30081" -ForegroundColor Cyan
Write-Host "User Service: http://$minikubeIp`:30082" -ForegroundColor Cyan

Write-Host "`n📊 Check deployment status:" -ForegroundColor Green
Write-Host "kubectl get pods -n mysillydreams" -ForegroundColor Cyan
Write-Host "kubectl get services -n mysillydreams" -ForegroundColor Cyan

Write-Host "`n🎉 Deployment script completed!" -ForegroundColor Green
