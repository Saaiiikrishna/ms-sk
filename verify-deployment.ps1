# MySillyDreams Platform Deployment Verification Script
# This script verifies that all services are running correctly

param(
    [switch]$Kubernetes = $false,
    [switch]$Local = $false
)

Write-Host "🔍 MySillyDreams Platform Deployment Verification" -ForegroundColor Green

if ($Kubernetes) {
    Write-Host "📊 Checking Kubernetes deployment..." -ForegroundColor Yellow
    
    # Check if namespace exists
    $namespace = kubectl get namespace mysillydreams --ignore-not-found=true
    if (-not $namespace) {
        Write-Host "❌ Namespace 'mysillydreams' not found" -ForegroundColor Red
        exit 1
    }
    Write-Host "✅ Namespace 'mysillydreams' exists" -ForegroundColor Green
    
    # Check all pods
    Write-Host "`n📦 Checking pod status..." -ForegroundColor Cyan
    $pods = kubectl get pods -n mysillydreams -o json | ConvertFrom-Json
    
    foreach ($pod in $pods.items) {
        $name = $pod.metadata.name
        $status = $pod.status.phase
        $ready = $pod.status.containerStatuses[0].ready
        
        if ($status -eq "Running" -and $ready) {
            Write-Host "✅ $name is running and ready" -ForegroundColor Green
        } else {
            Write-Host "❌ $name is not ready (Status: $status, Ready: $ready)" -ForegroundColor Red
        }
    }
    
    # Check services
    Write-Host "`n🌐 Checking service endpoints..." -ForegroundColor Cyan
    $minikubeIp = minikube ip
    
    $services = @(
        @{Name="Eureka Server"; Port=30761; Path="/"},
        @{Name="Spring Boot Admin"; Port=30080; Path="/"},
        @{Name="Keycloak"; Port=30180; Path="/"},
        @{Name="Zipkin"; Port=30411; Path="/"},
        @{Name="Auth Service"; Port=30081; Path="/actuator/health"},
        @{Name="User Service"; Port=30082; Path="/actuator/health"}
    )
    
    foreach ($service in $services) {
        $url = "http://$minikubeIp`:$($service.Port)$($service.Path)"
        try {
            $response = Invoke-WebRequest -Uri $url -TimeoutSec 10 -UseBasicParsing
            if ($response.StatusCode -eq 200) {
                Write-Host "✅ $($service.Name) is accessible at $url" -ForegroundColor Green
            } else {
                Write-Host "⚠️ $($service.Name) returned status $($response.StatusCode)" -ForegroundColor Yellow
            }
        } catch {
            Write-Host "❌ $($service.Name) is not accessible at $url" -ForegroundColor Red
        }
    }
    
    Write-Host "`n📋 Service URLs:" -ForegroundColor Green
    Write-Host "Eureka Server: http://$minikubeIp`:30761" -ForegroundColor Cyan
    Write-Host "Spring Boot Admin: http://$minikubeIp`:30080" -ForegroundColor Cyan
    Write-Host "Keycloak Admin: http://$minikubeIp`:30180 (admin/admin123)" -ForegroundColor Cyan
    Write-Host "Zipkin UI: http://$minikubeIp`:30411" -ForegroundColor Cyan
    Write-Host "Auth Service: http://$minikubeIp`:30081" -ForegroundColor Cyan
    Write-Host "User Service: http://$minikubeIp`:30082" -ForegroundColor Cyan
}

if ($Local) {
    Write-Host "🏠 Checking local deployment..." -ForegroundColor Yellow
    
    $services = @(
        @{Name="Eureka Server"; Port=8761; Path="/"},
        @{Name="Spring Boot Admin"; Port=8080; Path="/"},
        @{Name="Keycloak"; Port=8180; Path="/"},
        @{Name="Zipkin"; Port=9411; Path="/"},
        @{Name="Auth Service"; Port=8081; Path="/actuator/health"},
        @{Name="User Service"; Port=8082; Path="/actuator/health"},
        @{Name="Redis"; Port=6379; Path=""},
        @{Name="PostgreSQL Auth"; Port=5432; Path=""},
        @{Name="PostgreSQL User"; Port=5433; Path=""}
    )
    
    foreach ($service in $services) {
        if ($service.Path -eq "") {
            # For databases, just check if port is listening
            $connection = Test-NetConnection -ComputerName localhost -Port $service.Port -WarningAction SilentlyContinue
            if ($connection.TcpTestSucceeded) {
                Write-Host "✅ $($service.Name) is listening on port $($service.Port)" -ForegroundColor Green
            } else {
                Write-Host "❌ $($service.Name) is not accessible on port $($service.Port)" -ForegroundColor Red
            }
        } else {
            $url = "http://localhost:$($service.Port)$($service.Path)"
            try {
                $response = Invoke-WebRequest -Uri $url -TimeoutSec 10 -UseBasicParsing
                if ($response.StatusCode -eq 200) {
                    Write-Host "✅ $($service.Name) is accessible at $url" -ForegroundColor Green
                } else {
                    Write-Host "⚠️ $($service.Name) returned status $($response.StatusCode)" -ForegroundColor Yellow
                }
            } catch {
                Write-Host "❌ $($service.Name) is not accessible at $url" -ForegroundColor Red
            }
        }
    }
    
    Write-Host "`n📋 Local Service URLs:" -ForegroundColor Green
    Write-Host "Eureka Server: http://localhost:8761" -ForegroundColor Cyan
    Write-Host "Spring Boot Admin: http://localhost:8080" -ForegroundColor Cyan
    Write-Host "Keycloak Admin: http://localhost:8180 (admin/admin123)" -ForegroundColor Cyan
    Write-Host "Zipkin UI: http://localhost:9411" -ForegroundColor Cyan
    Write-Host "Auth Service: http://localhost:8081" -ForegroundColor Cyan
    Write-Host "User Service: http://localhost:8082" -ForegroundColor Cyan
}

if (-not $Kubernetes -and -not $Local) {
    Write-Host "Please specify either -Kubernetes or -Local flag" -ForegroundColor Red
    Write-Host "Usage: .\verify-deployment.ps1 -Kubernetes" -ForegroundColor Yellow
    Write-Host "       .\verify-deployment.ps1 -Local" -ForegroundColor Yellow
    exit 1
}

Write-Host "`n🎉 Verification completed!" -ForegroundColor Green
