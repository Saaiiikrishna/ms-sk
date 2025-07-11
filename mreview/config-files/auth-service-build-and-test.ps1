# Auth Service Build and Test Script for Windows
# This script helps set up the environment and build the auth service

param(
    [switch]$InstallJava,
    [switch]$Build,
    [switch]$Test,
    [switch]$Docker,
    [switch]$All
)

$ErrorActionPreference = "Stop"

Write-Host "=== Auth Service Build and Test Script ===" -ForegroundColor Green

# Function to check if a command exists
function Test-Command($cmdname) {
    return [bool](Get-Command -Name $cmdname -ErrorAction SilentlyContinue)
}

# Function to install Java and Maven using Chocolatey
function Install-Java {
    Write-Host "Installing Java 17 and Maven using Chocolatey..." -ForegroundColor Yellow
    
    # Check if Chocolatey is installed
    if (-not (Test-Command "choco")) {
        Write-Host "Chocolatey is not installed. Installing Chocolatey first..." -ForegroundColor Yellow
        Set-ExecutionPolicy Bypass -Scope Process -Force
        [System.Net.ServicePointManager]::SecurityProtocol = [System.Net.ServicePointManager]::SecurityProtocol -bor 3072
        iex ((New-Object System.Net.WebClient).DownloadString('https://community.chocolatey.org/install.ps1'))
        
        # Refresh environment variables
        $env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")
    }
    
    Write-Host "Installing OpenJDK 17..." -ForegroundColor Cyan
    choco install openjdk17 -y
    
    Write-Host "Installing Maven..." -ForegroundColor Cyan
    choco install maven -y
    
    # Refresh environment variables
    $env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")
    
    Write-Host "Java and Maven installation completed!" -ForegroundColor Green
}

# Function to verify environment
function Test-Environment {
    Write-Host "Checking environment..." -ForegroundColor Yellow
    
    $javaInstalled = Test-Command "java"
    $mavenInstalled = Test-Command "mvn"
    $dockerInstalled = Test-Command "docker"
    
    Write-Host "Java installed: $javaInstalled" -ForegroundColor $(if($javaInstalled) {"Green"} else {"Red"})
    Write-Host "Maven installed: $mavenInstalled" -ForegroundColor $(if($mavenInstalled) {"Green"} else {"Red"})
    Write-Host "Docker installed: $dockerInstalled" -ForegroundColor $(if($dockerInstalled) {"Green"} else {"Red"})
    
    if ($javaInstalled) {
        $javaVersion = java -version 2>&1 | Select-String "version"
        Write-Host "Java version: $javaVersion" -ForegroundColor Cyan
    }
    
    if ($mavenInstalled) {
        $mavenVersion = mvn -version 2>&1 | Select-String "Apache Maven"
        Write-Host "Maven version: $mavenVersion" -ForegroundColor Cyan
    }
    
    return @{
        Java = $javaInstalled
        Maven = $mavenInstalled
        Docker = $dockerInstalled
    }
}

# Function to build the project
function Build-Project {
    Write-Host "Building Auth Service..." -ForegroundColor Yellow
    
    if (-not (Test-Path "pom.xml")) {
        throw "pom.xml not found. Please run this script from the auth-service directory."
    }
    
    Write-Host "Running Maven clean compile..." -ForegroundColor Cyan
    mvn clean compile
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "Build successful!" -ForegroundColor Green
    } else {
        throw "Build failed with exit code $LASTEXITCODE"
    }
}

# Function to run tests
function Run-Tests {
    Write-Host "Running tests..." -ForegroundColor Yellow
    
    Write-Host "Running Maven test..." -ForegroundColor Cyan
    mvn test
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "All tests passed!" -ForegroundColor Green
    } else {
        Write-Host "Some tests failed. Check the output above for details." -ForegroundColor Red
        throw "Tests failed with exit code $LASTEXITCODE"
    }
}

# Function to build Docker image
function Build-Docker {
    Write-Host "Building Docker image..." -ForegroundColor Yellow
    
    if (-not (Test-Path "Dockerfile")) {
        throw "Dockerfile not found. Please run this script from the auth-service directory."
    }
    
    Write-Host "Running Maven package..." -ForegroundColor Cyan
    mvn package -DskipTests
    
    if ($LASTEXITCODE -ne 0) {
        throw "Maven package failed with exit code $LASTEXITCODE"
    }
    
    Write-Host "Building Docker image..." -ForegroundColor Cyan
    docker build -t auth-service:latest .
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "Docker image built successfully!" -ForegroundColor Green
    } else {
        throw "Docker build failed with exit code $LASTEXITCODE"
    }
}

# Function to test Docker image
function Test-Docker {
    Write-Host "Testing Docker image..." -ForegroundColor Yellow
    
    Write-Host "Starting Docker container..." -ForegroundColor Cyan
    $containerId = docker run -d -p 8080:8080 --name auth-service-test auth-service:latest
    
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to start Docker container"
    }
    
    Write-Host "Waiting for service to start..." -ForegroundColor Cyan
    Start-Sleep -Seconds 30
    
    try {
        Write-Host "Testing health endpoint..." -ForegroundColor Cyan
        $response = Invoke-WebRequest -Uri "http://localhost:8080/actuator/health" -UseBasicParsing -TimeoutSec 10
        
        if ($response.StatusCode -eq 200) {
            Write-Host "Health check passed!" -ForegroundColor Green
        } else {
            Write-Host "Health check failed with status: $($response.StatusCode)" -ForegroundColor Red
        }
    } catch {
        Write-Host "Health check failed: $($_.Exception.Message)" -ForegroundColor Red
    } finally {
        Write-Host "Stopping and removing test container..." -ForegroundColor Cyan
        docker stop auth-service-test | Out-Null
        docker rm auth-service-test | Out-Null
    }
}

# Main execution logic
try {
    $env = Test-Environment
    
    if ($InstallJava -or $All) {
        if (-not $env.Java -or -not $env.Maven) {
            Install-Java
            $env = Test-Environment
        } else {
            Write-Host "Java and Maven already installed." -ForegroundColor Green
        }
    }
    
    if ($Build -or $All) {
        if (-not $env.Java -or -not $env.Maven) {
            throw "Java and Maven are required for building. Use -InstallJava flag to install them."
        }
        Build-Project
    }
    
    if ($Test -or $All) {
        if (-not $env.Java -or -not $env.Maven) {
            throw "Java and Maven are required for testing. Use -InstallJava flag to install them."
        }
        Run-Tests
    }
    
    if ($Docker -or $All) {
        if (-not $env.Docker) {
            throw "Docker is required for Docker operations. Please install Docker Desktop."
        }
        Build-Docker
        Test-Docker
    }
    
    if (-not ($InstallJava -or $Build -or $Test -or $Docker -or $All)) {
        Write-Host @"
Usage: .\build-and-test.ps1 [options]

Options:
  -InstallJava    Install Java 17 and Maven using Chocolatey
  -Build          Build the project using Maven
  -Test           Run unit and integration tests
  -Docker         Build and test Docker image
  -All            Run all operations (install, build, test, docker)

Examples:
  .\build-and-test.ps1 -InstallJava
  .\build-and-test.ps1 -Build
  .\build-and-test.ps1 -All
"@ -ForegroundColor Cyan
    }
    
} catch {
    Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

Write-Host "=== Script completed successfully ===" -ForegroundColor Green
