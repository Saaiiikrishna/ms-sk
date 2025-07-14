#!/bin/bash

# Local Testing Script for Microservices
# This script sets up and tests the entire microservices stack locally

set -e

echo "🚀 Starting Local Microservices Testing Environment"
echo "=================================================="

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

# Function to wait for service to be ready
wait_for_service() {
    local service_name=$1
    local url=$2
    local max_attempts=${3:-30}
    local attempt=1

    print_status "Waiting for $service_name to be ready..."
    
    while [ $attempt -le $max_attempts ]; do
        if curl -f -s "$url" > /dev/null 2>&1; then
            print_success "$service_name is ready!"
            return 0
        fi
        
        echo -n "."
        sleep 2
        attempt=$((attempt + 1))
    done
    
    print_error "$service_name failed to start within expected time"
    return 1
}

# Function to check if Docker is running
check_docker() {
    if ! docker info > /dev/null 2>&1; then
        print_error "Docker is not running. Please start Docker and try again."
        exit 1
    fi
    print_success "Docker is running"
}

# Function to clean up previous containers
cleanup() {
    print_status "Cleaning up previous containers..."
    docker-compose -f docker-compose.local.yml down -v --remove-orphans 2>/dev/null || true
    print_success "Cleanup completed"
}

# Function to build services
build_services() {
    print_status "Building microservices..."
    
    # Build each service
    services=("zookeeper-admin" "eureka-server" "auth-service" "user-service" "api-gateway")
    
    for service in "${services[@]}"; do
        if [ -d "$service" ]; then
            print_status "Building $service..."
            cd "$service"
            if [ -f "Dockerfile" ]; then
                docker build -t "local/$service:latest" . || {
                    print_error "Failed to build $service"
                    exit 1
                }
                print_success "$service built successfully"
            else
                print_warning "No Dockerfile found for $service, skipping..."
            fi
            cd ..
        else
            print_warning "Directory $service not found, skipping..."
        fi
    done
}

# Function to start infrastructure services
start_infrastructure() {
    print_status "Starting infrastructure services..."
    
    # Start infrastructure services first
    docker-compose -f docker-compose.local.yml up -d zookeeper redis postgres-auth postgres-user zipkin
    
    # Wait for infrastructure to be ready
    wait_for_service "Zookeeper" "http://localhost:2181" 30
    wait_for_service "Redis" "redis://localhost:6379" 30
    wait_for_service "PostgreSQL (Auth)" "postgresql://localhost:5432" 30
    wait_for_service "PostgreSQL (User)" "postgresql://localhost:5433" 30
    wait_for_service "Zipkin" "http://localhost:9411/health" 30
    
    print_success "Infrastructure services are ready"
}

# Function to start application services
start_application_services() {
    print_status "Starting application services..."
    
    # Start Zookeeper Admin
    docker-compose -f docker-compose.local.yml up -d zookeeper-admin
    wait_for_service "Zookeeper Admin" "http://localhost:8084/health" 30
    
    # Setup Zookeeper configuration
    print_status "Setting up Zookeeper configuration..."
    chmod +x scripts/setup-zookeeper-config.sh
    ./scripts/setup-zookeeper-config.sh
    
    # Start Eureka Server
    docker-compose -f docker-compose.local.yml up -d eureka-server
    wait_for_service "Eureka Server" "http://localhost:8761/actuator/health" 60
    
    # Start Auth Service
    docker-compose -f docker-compose.local.yml up -d auth-service
    wait_for_service "Auth Service" "http://localhost:8081/actuator/health" 60
    
    # Start User Service
    docker-compose -f docker-compose.local.yml up -d user-service
    wait_for_service "User Service" "http://localhost:8082/actuator/health" 60
    
    # Start API Gateway
    docker-compose -f docker-compose.local.yml up -d api-gateway
    wait_for_service "API Gateway" "http://localhost:8080/actuator/health" 60
    
    print_success "All application services are ready"
}

# Function to run tests
run_tests() {
    print_status "Running integration tests..."
    
    # Test API Gateway health
    print_status "Testing API Gateway..."
    if curl -f "http://localhost:8080/actuator/health" > /dev/null 2>&1; then
        print_success "API Gateway health check passed"
    else
        print_error "API Gateway health check failed"
        return 1
    fi
    
    # Test Auth Service through API Gateway
    print_status "Testing Auth Service through API Gateway..."
    auth_response=$(curl -s -w "%{http_code}" -o /dev/null "http://localhost:8080/api/auth/health" || echo "000")
    if [ "$auth_response" = "200" ]; then
        print_success "Auth Service accessible through API Gateway"
    else
        print_warning "Auth Service not accessible through API Gateway (HTTP $auth_response)"
    fi
    
    # Test User Service through API Gateway
    print_status "Testing User Service through API Gateway..."
    user_response=$(curl -s -w "%{http_code}" -o /dev/null "http://localhost:8080/api/users/health" || echo "000")
    if [ "$user_response" = "200" ]; then
        print_success "User Service accessible through API Gateway"
    else
        print_warning "User Service not accessible through API Gateway (HTTP $user_response)"
    fi
    
    # Test Eureka registration
    print_status "Checking Eureka service registration..."
    eureka_apps=$(curl -s "http://localhost:8761/eureka/apps" | grep -o "AUTH-SERVICE\|USER-SERVICE\|API-GATEWAY" | wc -l)
    if [ "$eureka_apps" -ge 3 ]; then
        print_success "Services are registered with Eureka"
    else
        print_warning "Not all services are registered with Eureka"
    fi
    
    print_success "Integration tests completed"
}

# Function to show service status
show_status() {
    print_status "Service Status:"
    echo "=============="
    echo "🔧 Infrastructure Services:"
    echo "   - Zookeeper:        http://localhost:2181"
    echo "   - Zookeeper Admin:  http://localhost:8084"
    echo "   - Redis:            redis://localhost:6379"
    echo "   - PostgreSQL Auth:  postgresql://localhost:5432"
    echo "   - PostgreSQL User:  postgresql://localhost:5433"
    echo "   - Zipkin:           http://localhost:9411"
    echo ""
    echo "🚀 Application Services:"
    echo "   - Eureka Server:    http://localhost:8761"
    echo "   - Auth Service:     http://localhost:8081"
    echo "   - User Service:     http://localhost:8082"
    echo "   - API Gateway:      http://localhost:8080"
    echo ""
    echo "📊 Monitoring & Admin:"
    echo "   - Eureka Dashboard: http://localhost:8761"
    echo "   - Zipkin UI:        http://localhost:9411"
    echo "   - Zookeeper Admin:  http://localhost:8084"
    echo ""
    echo "🧪 Test Endpoints:"
    echo "   - API Gateway Health: http://localhost:8080/actuator/health"
    echo "   - Auth via Gateway:   http://localhost:8080/api/auth/health"
    echo "   - Users via Gateway:  http://localhost:8080/api/users/health"
}

# Main execution
main() {
    case "${1:-start}" in
        "start")
            check_docker
            cleanup
            build_services
            start_infrastructure
            start_application_services
            run_tests
            show_status
            print_success "🎉 Local testing environment is ready!"
            ;;
        "stop")
            print_status "Stopping all services..."
            docker-compose -f docker-compose.local.yml down -v
            print_success "All services stopped"
            ;;
        "restart")
            $0 stop
            sleep 5
            $0 start
            ;;
        "status")
            show_status
            ;;
        "logs")
            service=${2:-}
            if [ -n "$service" ]; then
                docker-compose -f docker-compose.local.yml logs -f "$service"
            else
                docker-compose -f docker-compose.local.yml logs -f
            fi
            ;;
        "test")
            run_tests
            ;;
        *)
            echo "Usage: $0 {start|stop|restart|status|logs [service]|test}"
            echo ""
            echo "Commands:"
            echo "  start    - Start all services and run tests"
            echo "  stop     - Stop all services"
            echo "  restart  - Restart all services"
            echo "  status   - Show service URLs and status"
            echo "  logs     - Show logs (optionally for specific service)"
            echo "  test     - Run integration tests"
            exit 1
            ;;
    esac
}

# Run main function with all arguments
main "$@"
