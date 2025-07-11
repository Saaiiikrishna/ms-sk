#!/usr/bin/env bash

set -euo pipefail

# Install system packages only if needed and apt-get is available
if ! command -v mvn >/dev/null || ! command -v javac >/dev/null; then
  if command -v apt-get >/dev/null; then
    if [ "$(id -u)" -ne 0 ] && command -v sudo >/dev/null; then
      sudo apt-get update
      sudo apt-get install -y openjdk-17-jdk maven
    else
      apt-get update
      apt-get install -y openjdk-17-jdk maven
    fi
  fi
fi

# Build all Maven modules without running tests
modules="auth-service catalog-service delivery-service inventory-api inventory-core order-api order-core payment-service pricing-engine user-service vendor-service e2e-tests"

for module in $modules; do
  if [ -d "$module" ]; then
    echo "Building $module..."
    if ! (cd "$module" && mvn -q package -DskipTests); then
      echo "Warning: failed to build $module" >&2
    fi
  fi
done

echo "Setup complete."
