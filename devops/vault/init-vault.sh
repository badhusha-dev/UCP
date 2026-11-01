#!/bin/sh
# ==============================================================================
# HashiCorp Vault Initialization & Secret Provisioning Script (POSIX)
# ==============================================================================

set -e

VAULT_ADDR=${VAULT_ADDR:-"http://localhost:8200"}
VAULT_TOKEN=${VAULT_TOKEN:-"root"}

echo "Waiting for HashiCorp Vault to become accessible at $VAULT_ADDR..."
until curl -s "$VAULT_ADDR/v1/sys/health" > /dev/null 2>&1; do
    echo "Vault is starting up, retrying in 2s..."
    sleep 2
done

echo "Vault is ready. Authenticating with root token..."

# 1. Enable Key-Value Version 2 Engine at secret/
curl -s --header "X-Vault-Token: $VAULT_TOKEN" \
     --request POST \
     --data '{"type": "kv", "options": {"version": "2"}}' \
     "$VAULT_ADDR/v1/sys/mounts/secret" || echo "KV engine already mounted"

# 2. Seed Shared Platform Secrets (JWT secret, Logstash configuration)
echo "Writing shared platform secrets..."
curl -s --header "X-Vault-Token: $VAULT_TOKEN" \
     --request POST \
     --data '{
       "data": {
         "jwt.secret": "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970",
         "jwt.expiration": "86400000",
         "logstash.host": "logstash",
         "logstash.port": "5000"
       }
     }' \
     "$VAULT_ADDR/v1/secret/data/ecommerce-platform"

curl -s --header "X-Vault-Token: $VAULT_TOKEN" \
     --request POST \
     --data '{
       "data": {
         "jwt.secret": "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970",
         "jwt.expiration": "86400000",
         "logstash.host": "logstash",
         "logstash.port": "5000"
       }
     }' \
     "$VAULT_ADDR/v1/secret/data/application"

# 3. Seed Microservice Database Credentials
echo "Writing service database credentials..."
for service in user-service product-service order-service notification-service; do
    curl -s --header "X-Vault-Token: $VAULT_TOKEN" \
         --request POST \
         --data '{
           "data": {
             "spring.datasource.username": "ecommerce_admin",
             "spring.datasource.password": "admin_password"
           }
         }' \
         "$VAULT_ADDR/v1/secret/data/$service"
done

# 4. Create Read-Only Application Policy
echo "Creating ecommerce-app policy..."
curl -s --header "X-Vault-Token: $VAULT_TOKEN" \
     --request PUT \
     --data '{
       "policy": "path \"secret/data/*\" { capabilities = [\"read\"] }"
     }' \
     "$VAULT_ADDR/v1/sys/policies/acl/ecommerce-app"

echo "Vault initialization completed successfully."
