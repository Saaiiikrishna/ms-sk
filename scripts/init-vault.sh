#!/bin/bash

# Initialize Vault for production use
# This script configures Vault with the necessary secrets engines and policies

echo "Initializing Vault for production use..."

# Set Vault address and token
export VAULT_ADDR="http://vault:8200"
export VAULT_TOKEN="root-token"

# Wait for Vault to be ready
echo "Waiting for Vault to be ready..."
until vault status > /dev/null 2>&1; do
    echo "Vault not ready, waiting..."
    sleep 2
done

echo "Vault is ready!"

# Enable transit secrets engine for encryption
echo "Enabling transit secrets engine..."
vault secrets enable transit

# Create encryption key for user service
echo "Creating encryption key for user service..."
vault write -f transit/keys/user-service-key

# Enable KV secrets engine v2
echo "Enabling KV secrets engine..."
vault secrets enable -path=secret kv-v2

# Store database credentials
echo "Storing database credentials..."
vault kv put secret/user-service \
    db-username=postgres \
    db-password=postgres123 \
    aws-access-key=dummy-access-key \
    aws-secret-key=dummy-secret-key

# Create policy for user service
echo "Creating policy for user service..."
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

echo "Vault initialization completed successfully!"
echo "Transit key created: user-service-key"
echo "KV secrets stored at: secret/user-service"
echo "Policy created: user-service-policy"
