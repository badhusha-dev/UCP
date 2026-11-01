# ==============================================================================
# HashiCorp Vault Initialization & Secret Provisioning Script (PowerShell)
# ==============================================================================

param (
    [string]$VaultAddr = "http://localhost:8200",
    [string]$VaultToken = "root"
)

Write-Host "Connecting to HashiCorp Vault at $VaultAddr..." -ForegroundColor Cyan

$headers = @{
    "X-Vault-Token" = $VaultToken
    "Content-Type"  = "application/json"
}

# 1. Enable Key-Value Version 2 Engine at secret/
try {
    $mountPayload = @{ type = "kv"; options = @{ version = "2" } } | ConvertTo-Json
    $res = Invoke-RestMethod -Uri "$VaultAddr/v1/sys/mounts/secret" -Method Post -Headers $headers -Body $mountPayload
    Write-Host " [OK] KV v2 secret engine mounted at secret/" -ForegroundColor Green
} catch {
    Write-Host " [INFO] KV v2 secret engine already mounted at secret/" -ForegroundColor Gray
}

if (-not $env:JWT_SECRET -or -not $env:POSTGRES_PASSWORD) {
    throw "Set JWT_SECRET and POSTGRES_PASSWORD in the environment before seeding Vault."
}

# 2. Seed Shared Platform Secrets
$sharedSecrets = @{
    data = @{
        "jwt.secret"     = $env:JWT_SECRET
        "jwt.expiration" = "86400000"
        "logstash.host"  = "logstash"
        "logstash.port"  = "5000"
    }
} | ConvertTo-Json

try {
    Invoke-RestMethod -Uri "$VaultAddr/v1/secret/data/ecommerce-platform" -Method Post -Headers $headers -Body $sharedSecrets | Out-Null
    Invoke-RestMethod -Uri "$VaultAddr/v1/secret/data/application" -Method Post -Headers $headers -Body $sharedSecrets | Out-Null
    Write-Host " [OK] Seeded secret/data/ecommerce-platform and secret/data/application" -ForegroundColor Green
} catch {
    Write-Host " [WARN] Failed to write shared secrets ($($_.Exception.Message))" -ForegroundColor Yellow
}

# 3. Seed Microservice Database Credentials
$services = @("user-service", "product-service", "order-service", "notification-service")
foreach ($svc in $services) {
    $svcSecret = @{
        data = @{
            "spring.datasource.username" = "ecommerce_admin"
            "spring.datasource.password" = $env:POSTGRES_PASSWORD
        }
    } | ConvertTo-Json

    try {
        Invoke-RestMethod -Uri "$VaultAddr/v1/secret/data/$svc" -Method Post -Headers $headers -Body $svcSecret | Out-Null
        Write-Host " [OK] Seeded secret/data/$svc" -ForegroundColor Green
    } catch {
        Write-Host " [WARN] Failed to write secret for $svc ($($_.Exception.Message))" -ForegroundColor Yellow
    }
}

# 4. Create Read-Only Application Policy
$policy = @{
    policy = 'path "secret/data/*" { capabilities = ["read"] }'
} | ConvertTo-Json

try {
    Invoke-RestMethod -Uri "$VaultAddr/v1/sys/policies/acl/ecommerce-app" -Method Put -Headers $headers -Body $policy | Out-Null
    Write-Host " [OK] Created ACL policy ecommerce-app" -ForegroundColor Green
} catch {
    Write-Host " [WARN] Failed to configure ACL policy ($($_.Exception.Message))" -ForegroundColor Yellow
}

Write-Host "Vault provisioning complete!" -ForegroundColor Cyan
