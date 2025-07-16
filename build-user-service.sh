#!/bin/bash

# Build User Service Docker Image Script
# This script builds the user-service Docker image with the fixed configuration

set -e

echo "=========================================="
echo "Building User Service Docker Image"
echo "=========================================="

# Configuration
SERVICE_NAME="user-service"
IMAGE_TAG="msd-dev-v1.0"
DOCKER_IMAGE="saaiiikrishna/$SERVICE_NAME:$IMAGE_TAG"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Step 1: Check if we're in the right directory
if [ ! -d "user-service" ]; then
    print_error "user-service directory not found. Please run this script from the project root."
    exit 1
fi

# Step 2: Clean and build the project
print_status "Cleaning and building user-service..."
cd user-service

# Clean previous builds
if [ -d "target" ]; then
    rm -rf target
fi

# Build the project
print_status "Running Maven build..."
mvn clean package -DskipTests

if [ $? -ne 0 ]; then
    print_error "Maven build failed!"
    exit 1
fi

print_success "Maven build completed successfully!"

# Step 3: Check if JAR file exists
JAR_FILE=$(find target -name "*.jar" -not -name "*-sources.jar" | head -1)
if [ -z "$JAR_FILE" ]; then
    print_error "JAR file not found in target directory!"
    exit 1
fi

print_success "JAR file found: $JAR_FILE"

# Step 4: Build Docker image
print_status "Building Docker image: $DOCKER_IMAGE"

# Create Dockerfile if it doesn't exist
if [ ! -f "Dockerfile" ]; then
    print_status "Creating Dockerfile..."
    cat > Dockerfile << 'EOF'
FROM openjdk:17-jre-slim

# Set working directory
WORKDIR /app

# Copy the JAR file
COPY target/*.jar app.jar

# Create non-root user
RUN groupadd -r appuser && useradd -r -g appuser appuser
RUN chown -R appuser:appuser /app
USER appuser

# Expose port
EXPOSE 8082

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8082/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
EOF
    print_success "Dockerfile created!"
fi

# Build the Docker image
docker build -t $DOCKER_IMAGE .

if [ $? -ne 0 ]; then
    print_error "Docker build failed!"
    exit 1
fi

print_success "Docker image built successfully: $DOCKER_IMAGE"

# Step 5: Load image into minikube (if using minikube)
if command -v minikube >/dev/null 2>&1; then
    print_status "Loading image into minikube..."
    minikube image load $DOCKER_IMAGE
    
    if [ $? -eq 0 ]; then
        print_success "Image loaded into minikube successfully!"
    else
        print_warning "Failed to load image into minikube. You may need to do this manually."
    fi
else
    print_warning "Minikube not found. If you're using minikube, load the image manually with:"
    echo "minikube image load $DOCKER_IMAGE"
fi

# Step 6: Verify image
print_status "Verifying Docker image..."
docker images | grep $SERVICE_NAME

# Go back to project root
cd ..

echo ""
echo "=========================================="
echo "Build Summary"
echo "=========================================="
echo "Service: $SERVICE_NAME"
echo "Image: $DOCKER_IMAGE"
echo "JAR File: $JAR_FILE"
echo ""
print_success "User service Docker image build completed successfully!"
print_status "You can now deploy the service using: kubectl apply -f k8s/07-user-service-production.yaml"
