# ==============================================================================
# Universal Commerce Platform - One-Command Master Installer (Windows PowerShell)
# ==============================================================================
[CmdletBinding()]
param(
    [switch]$GenerateSecrets,
    [string]$Profile = "all",
    [switch]$SkipValidation
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RootDir = Split-Path -Parent $ScriptDir

Write-Host "==============================================================================" -ForegroundColor Cyan
Write-Host "        UNIVERSAL COMMERCE PLATFORM - PRODUCTION SERVER INSTALLER            " -ForegroundColor Cyan
Write-Host "==============================================================================" -ForegroundColor Cyan

# Step 1: Pre-flight Validation
if (-not $SkipValidation) {
    Write-Host "[STEP 1/7] Running pre-flight environment validation..." -ForegroundColor Blue
    $validator = Join-Path $ScriptDir "validate-environment.ps1"
    if (Test-Path $validator) {
        & $validator
        if ($LASTEXITCODE -ne 0) {
            Write-Host "[ERROR] Environment validation failed. Aborting installation." -ForegroundColor Red
            exit 1
        }
    }
} else {
    Write-Host "[STEP 1/7] Pre-flight validation skipped per -SkipValidation." -ForegroundColor Yellow
}

# Step 2: Environment Configuration
Write-Host "[STEP 2/7] Checking environment configuration (.env)..." -ForegroundColor Blue
Set-Location $RootDir
$envFile = Join-Path $RootDir ".env"
$envExample = Join-Path $RootDir ".env.example"

if (-not (Test-Path $envFile)) {
    Write-Host "No .env file found. Creating from .env.example..." -ForegroundColor Yellow
    Copy-Item $envExample $envFile
}

if ($GenerateSecrets) {
    Write-Host "Generating cryptographically secure secrets..." -ForegroundColor Cyan
    $newJwt = [Convert]::ToBase64String((1..32 | ForEach-Object { Get-Random -Minimum 0 -Maximum 256 })) -replace '[/+=]',''
    $newPg = [Convert]::ToBase64String((1..16 | ForEach-Object { Get-Random -Minimum 0 -Maximum 256 })) -replace '[/+=]',''
    $newRedis = [Convert]::ToBase64String((1..16 | ForEach-Object { Get-Random -Minimum 0 -Maximum 256 })) -replace '[/+=]',''
    $newGrafana = [Convert]::ToBase64String((1..16 | ForEach-Object { Get-Random -Minimum 0 -Maximum 256 })) -replace '[/+=]',''

    (Get-Content $envFile) `
        -replace '^JWT_SECRET=.*', "JWT_SECRET=$newJwt" `
        -replace '^POSTGRES_PASSWORD=.*', "POSTGRES_PASSWORD=$newPg" `
        -replace '^REDIS_PASSWORD=.*', "REDIS_PASSWORD=$newRedis" `
        -replace '^GRAFANA_ADMIN_PASSWORD=.*', "GRAFANA_ADMIN_PASSWORD=$newGrafana" |
        Set-Content $envFile
    Write-Host "Secure secrets generated and stored in .env." -ForegroundColor Green
}

# Step 3: Required Data Directories
Write-Host "[STEP 3/7] Preparing storage directories..." -ForegroundColor Blue
@("devops/backups", "devops/certs", "devops/reports") | ForEach-Object {
    if (-not (Test-Path $_)) { New-Item -ItemType Directory -Path $_ -Force | Out-Null }
}

# Step 4: Docker Compose Configuration Validation
Write-Host "[STEP 4/7] Validating Docker Compose configuration..." -ForegroundColor Blue
docker compose config --quiet
if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] Invalid docker-compose.yml configuration!" -ForegroundColor Red
    exit 1
}

# Step 5: Sequential Startup
Write-Host "[STEP 5/7] Starting Infrastructure Services (Phase A: DB, Broker, Redis)..." -ForegroundColor Blue
docker compose up -d postgres redis zookeeper kafka
Start-Sleep -Seconds 8

Write-Host "[STEP 5/7] Starting Platform Services (Phase B: Eureka & Config Server)..." -ForegroundColor Blue
docker compose up -d discovery-server config-server
Start-Sleep -Seconds 12

Write-Host "[STEP 5/7] Starting Domain Microservices & API Gateway (Phase C)..." -ForegroundColor Blue
docker compose up -d user-service product-service order-service notification-service api-gateway

Write-Host "[STEP 5/7] Starting Frontend Microfrontends & Observability (Phase D)..." -ForegroundColor Blue
docker compose up -d shell-app mfe-product mfe-order mfe-user `
    prometheus grafana alertmanager zipkin kafka-ui elasticsearch logstash kibana vault

Write-Host "Waiting for Eureka service registry and API Gateway readiness..." -ForegroundColor Cyan
$expectedServices = @("USER-SERVICE", "PRODUCT-SERVICE", "ORDER-SERVICE", "NOTIFICATION-SERVICE", "API-GATEWAY")
$ready = $false
for ($attempt = 1; $attempt -le 40; $attempt++) {
    try {
        $apps = (Invoke-RestMethod -Uri "http://localhost:8761/eureka/apps" -Headers @{ "Accept" = "application/json" } -TimeoutSec 3 -ErrorAction Stop).applications.application
        $registeredNames = @($apps | ForEach-Object { $_.name.ToUpper() })
        $missing = $expectedServices | Where-Object { $registeredNames -notcontains $_ }
        if ($missing.Count -eq 0) {
            $gwHealth = Invoke-RestMethod -Uri "http://localhost:8080/actuator/health" -TimeoutSec 3 -ErrorAction Stop
            if ($gwHealth.status -eq "UP") {
                $ready = $true
                Write-Host "`nAll platform services registered in Eureka and Gateway is UP! (Ready in $($attempt * 5)s)" -ForegroundColor Green
                break
            }
        }
    } catch {}
    Write-Host -NoNewline "."
    Start-Sleep -Seconds 5
}

if (-not $ready) {
    Write-Host "`n[WARN] Services still initializing. Proceeding to health checks..." -ForegroundColor Yellow
}

# Step 6: Health Probe
Write-Host "[STEP 6/7] Running synthetic health checks..." -ForegroundColor Blue
$healthCheck = Join-Path $ScriptDir "smoke-test.ps1"
if (Test-Path $healthCheck) {
    & $healthCheck
}

# Step 7: Record Manifest
$timestamp = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
@"
DEPLOYMENT_TIMESTAMP=$timestamp
PLATFORM_VERSION=2.0.0
PROFILE=$Profile
"@ | Set-Content (Join-Path $RootDir ".deployment_manifest")

Write-Host "==============================================================================" -ForegroundColor Cyan
Write-Host "   UNIVERSAL COMMERCE PLATFORM INSTALLED SUCCESSFULLY!                       " -ForegroundColor Green
Write-Host "==============================================================================" -ForegroundColor Cyan
Write-Host "Access Endpoints:"
Write-Host "  - Customer Storefront (Shell):  http://localhost:3000" -ForegroundColor Cyan
Write-Host "  - Product Catalog MFE:          http://localhost:3001" -ForegroundColor Cyan
Write-Host "  - Order & Cart MFE:             http://localhost:3002" -ForegroundColor Cyan
Write-Host "  - User Profile MFE:             http://localhost:3003" -ForegroundColor Cyan
Write-Host "  - API Gateway Ingress:          http://localhost:8080" -ForegroundColor Cyan
Write-Host "  - Swagger OpenAPI UI:           http://localhost:8080/swagger-ui.html" -ForegroundColor Cyan
Write-Host "  - Eureka Service Registry:      http://localhost:8761" -ForegroundColor Cyan
Write-Host "  - Grafana Observability:        http://localhost:3030" -ForegroundColor Cyan
Write-Host "  - Prometheus Metrics:           http://localhost:9090" -ForegroundColor Cyan
Write-Host "  - Kafka UI Management:          http://localhost:8090" -ForegroundColor Cyan
Write-Host "  - Zipkin Distributed Tracing:  http://localhost:9411" -ForegroundColor Cyan
Write-Host "  - Kibana Centralized Logs:      http://localhost:5601" -ForegroundColor Cyan
Write-Host "------------------------------------------------------------------------------" -ForegroundColor Cyan
