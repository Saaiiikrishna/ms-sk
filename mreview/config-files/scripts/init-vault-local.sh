#!/bin/bash

# Local Vault initialization script for MySillyDreams Platform
# This script sets up Vault for local development

set -e

echo "Starting Vault initialization for local development..."

# Set Vault address
export VAULT_ADDR=http://127.0.0.1:8200
export VAULT_TOKEN=root-token

# Wait for Vault to be ready
echo "Waiting for Vault to be ready..."
until vault status > /dev/null 2>&1; do
    echo "Vault is not ready yet, waiting..."
    sleep 2
done

echo "Vault is ready!"

# Enable transit secrets engine for encryption
echo "Enabling transit secrets engine..."
vault secrets enable transit || echo "Transit already enabled"

# Create encryption keys for services
echo "Creating encryption keys..."
vault write -f transit/keys/user-service-key || echo "User service key already exists"
vault write -f transit/keys/auth-service-key || echo "Auth service key already exists"

# Enable KV secrets engine v2
echo "Enabling KV secrets engine..."
vault secrets enable -path=secret kv-v2 || echo "KV already enabled"

# Store database credentials for user service
echo "Storing user service secrets..."
vault kv put secret/user-service \
    db-username=useruser \
    db-password=userpass123 \
    aws-access-key=local-access-key \
    aws-secret-key=local-secret-key \
    redis-host=redis \
    redis-port=6379

# Store database credentials for auth service
echo "Storing auth service secrets..."
vault kv put secret/auth-service \
    db-username=authuser \
    db-password=authpass123 \
    jwt-secret=LocalJwtSecretKeyForDevelopmentMinimum256BitsLong123456789! \
    keycloak-admin-username=admin \
    keycloak-admin-password=admin123 \
    internal-api-secret=LocalInternalApiSecretKeyForDevelopment123456789!

# Store Keycloak configuration
echo "Storing Keycloak configuration..."
vault kv put secret/keycloak \
    admin-username=admin \
    admin-password=admin123 \
    realm=mysillydreams \
    auth-server-url=http://keycloak:8080

# Create policies for services
echo "Creating policies..."

# User service policy
vault policy write user-service-policy - <<EOF
# Allow access to KV secrets
path "secret/data/user-service" {
  capabilities = ["read"]
}

# Allow transit encryption/decryption
path "transit/encrypt/user-service-key" {
  capabilities = ["update"]
}

path "transit/decrypt/user-service-key" {
  capabilities = ["update"]
}

# Allow reading transit key info
path "transit/keys/user-service-key" {
  capabilities = ["read"]
}
EOF

# Auth service policy
vault policy write auth-service-policy - <<EOF
# Allow access to KV secrets
path "secret/data/auth-service" {
  capabilities = ["read"]
}

path "secret/data/keycloak" {
  capabilities = ["read"]
}

# Allow transit encryption/decryption
path "transit/encrypt/auth-service-key" {
  capabilities = ["update"]
}

path "transit/decrypt/auth-service-key" {
  capabilities = ["update"]
}

# Allow reading transit key info
path "transit/keys/auth-service-key" {
  capabilities = ["read"]
}
EOF

# Create tokens for services (for local development)
echo "Creating service tokens..."
USER_SERVICE_TOKEN=$(vault write -field=token auth/token/create policies=user-service-policy ttl=24h)
AUTH_SERVICE_TOKEN=$(vault write -field=token auth/token/create policies=auth-service-policy ttl=24h)

echo "Vault initialization completed successfully!"
echo ""
echo "=== Local Development Configuration ==="
echo "Vault Address: http://localhost:8200"
echo "Root Token: root-token"
echo "User Service Token: $USER_SERVICE_TOKEN"
echo "Auth Service Token: $AUTH_SERVICE_TOKEN"
echo ""
echo "=== Service Secrets Created ==="
echo "- secret/user-service (DB credentials, AWS keys, Redis config)"
echo "- secret/auth-service (DB credentials, JWT secret, Keycloak config)"
echo "- secret/keycloak (Admin credentials, realm config)"
echo ""
echo "=== Transit Keys Created ==="
echo "- user-service-key (for PII encryption)"
echo "- auth-service-key (for sensitive data encryption)"
echo ""
echo "=== Policies Created ==="
echo "- user-service-policy"
echo "- auth-service-policy"
echo ""
echo "You can now start your microservices with the local profile!"
