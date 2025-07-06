#!/usr/bin/env bash
set -euo pipefail

# Install system packages
sudo apt-get update
sudo apt-get install -y openjdk-17-jdk maven

# Build all Maven modules without running tests
for module in auth-service catalog-service delivery-service inventory-api inventory-core order-api order-core payment-service pricing-engine user-service e2e-tests; do
  if [ -d "$module" ]; then
    echo "Building $module..."
    (cd "$module" && mvn -q package -DskipTests)
  fi
done

echo "Setup complete."
