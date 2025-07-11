#!/bin/bash

# Local development stop script for MySillyDreams Platform
# This script stops all infrastructure services

set -e

echo "🛑 Stopping MySillyDreams Platform Local Development Environment"
echo "================================================================"

# Check if docker-compose is available
if ! command -v docker-compose &> /dev/null; then
    echo "❌ docker-compose is not installed."
    exit 1
fi

# Stop services
echo ""
echo "📦 Stopping infrastructure services..."
docker-compose -f docker-compose-local.yml down

echo ""
echo "✅ All services stopped!"
echo ""
echo "💡 Options:"
echo "   To remove volumes (clean slate): docker-compose -f docker-compose-local.yml down -v"
echo "   To restart services: ./scripts/start-local-dev.sh"
