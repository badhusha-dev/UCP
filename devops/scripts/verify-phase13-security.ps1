# ==============================================================================
# Phase 13: Enterprise Security Hardening & Secret Management Verification Script
# ==============================================================================

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "   Starting Phase 13: Security Hardening & Vault Verification   " -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan

$Passed = 0
$Failed = 0

function Assert-Condition {
    param (
        [string]$Description,
        [bool]$Condition
    )
    if ($Condition) {
        Write-Host " [PASS] $Description" -ForegroundColor Green
        $global:Passed++
    } else {
        Write-Host " [FAIL] $Description" -ForegroundColor Red
        $global:Failed++
    }
}

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
if (-not $ScriptDir) { $ScriptDir = $PSScriptRoot }
if (-not $ScriptDir) { $ScriptDir = "." }
$ProjectRoot = (Resolve-Path (Join-Path $ScriptDir "../..")).Path
$BackendDir = Join-Path $ProjectRoot "backend"
$K8sDir = Join-Path $ProjectRoot "kubernetes"
$DevopsDir = Join-Path $ProjectRoot "devops"

# ------------------------------------------------------------------------------
# 1. OWASP Dependency-Check & Vulnerability Scanning Configuration
# ------------------------------------------------------------------------------
Write-Host "`n--- 1. OWASP Dependency-Check & Audit Configuration ---" -ForegroundColor Yellow

$parentPomPath = Join-Path $BackendDir "pom.xml"
$parentPom = Get-Content $parentPomPath -Raw

Assert-Condition "Parent POM defines dependency-check-maven plugin" ($parentPom -match "dependency-check-maven")
Assert-Condition "Parent POM defines failBuildOnCVSS threshold (8.0)" ($parentPom -match "<failBuildOnCVSS>8\.0</failBuildOnCVSS>")
Assert-Condition "Parent POM configures suppressionFile" ($parentPom -match "<suppressionFile>.*owasp-suppressions\.xml</suppressionFile>")
Assert-Condition "Parent POM configures spring-cloud-vault-config in dependencyManagement" ($parentPom -match "spring-cloud-vault-config")

$suppressionPath = Join-Path $BackendDir "owasp-suppressions.xml"
$suppressionExists = Test-Path $suppressionPath
Assert-Condition "owasp-suppressions.xml exists in backend directory" $suppressionExists

if ($suppressionExists) {
    $suppressionContent = Get-Content $suppressionPath -Raw
    Assert-Condition "owasp-suppressions.xml contains suppressions root element" ($suppressionContent -match "<suppressions")
    Assert-Condition "owasp-suppressions.xml suppresses Lombok compile-time processor false positives" ($suppressionContent -match 'org\\\.projectlombok')
    Assert-Condition "owasp-suppressions.xml suppresses H2 in-memory test database false positives" ($suppressionContent -match 'com\\\.h2database')
    Assert-Condition "owasp-suppressions.xml suppresses WireMock test harness false positives" ($suppressionContent -match 'org\\\.wiremock')
}

# ------------------------------------------------------------------------------
# 2. HashiCorp Vault Configuration & Provisioning Scripts
# ------------------------------------------------------------------------------
Write-Host "`n--- 2. HashiCorp Vault Configuration & Provisioning Scripts ---" -ForegroundColor Yellow

$vaultConfigPath = Join-Path $DevopsDir "vault/config.hcl"
$vaultConfigExists = Test-Path $vaultConfigPath
Assert-Condition "vault/config.hcl exists" $vaultConfigExists

if ($vaultConfigExists) {
    $vaultHcl = Get-Content $vaultConfigPath -Raw
    Assert-Condition "vault/config.hcl configures TCP listener on 0.0.0.0:8200" ($vaultHcl -match "0\.0\.0\.0:8200")
    Assert-Condition "vault/config.hcl configures file storage backend" ($vaultHcl -match 'storage "file"')
}

$initVaultSh = Join-Path $DevopsDir "vault/init-vault.sh"
$initVaultPs1 = Join-Path $DevopsDir "vault/init-vault.ps1"
Assert-Condition "devops/vault/init-vault.sh provisioning script exists" (Test-Path $initVaultSh)
Assert-Condition "devops/vault/init-vault.ps1 provisioning script exists" (Test-Path $initVaultPs1)

if (Test-Path $initVaultPs1) {
    $vaultScript = Get-Content $initVaultPs1 -Raw
    Assert-Condition "Vault provisioning script enables KV-v2 engine at secret/" ($vaultScript -match "sys/mounts/secret")
    Assert-Condition "Vault provisioning script populates shared application secrets" ($vaultScript -match "secret/data/application")
    Assert-Condition "Vault provisioning script populates user-service secrets" ($vaultScript -match "user-service")
    Assert-Condition "Vault provisioning script populates product-service secrets" ($vaultScript -match "product-service")
    Assert-Condition "Vault provisioning script populates order-service secrets" ($vaultScript -match "order-service")
    Assert-Condition "Vault provisioning script populates notification-service secrets" ($vaultScript -match "notification-service")
    Assert-Condition "Vault provisioning script creates ecommerce-app policy" ($vaultScript -match "ecommerce-app")
}

# ------------------------------------------------------------------------------
# 3. Docker Compose & Kubernetes Vault Integration
# ------------------------------------------------------------------------------
Write-Host "`n--- 3. Container & Kubernetes Secret Engine Deployment ---" -ForegroundColor Yellow

$dockerComposePath = Join-Path $ProjectRoot "docker-compose.yml"
$dockerCompose = Get-Content $dockerComposePath -Raw
Assert-Condition "docker-compose.yml defines vault service" ($dockerCompose -match "vault:\s+image:\s+hashicorp/vault")
Assert-Condition "docker-compose.yml exposes port 8200" ($dockerCompose -match "8200:8200")
Assert-Condition "docker-compose.yml adds IPC_LOCK capability" ($dockerCompose -match "IPC_LOCK")

$k8sVaultPath = Join-Path $K8sDir "security/vault.yml"
$k8sVaultExists = Test-Path $k8sVaultPath
Assert-Condition "kubernetes/security/vault.yml manifest exists" $k8sVaultExists

if ($k8sVaultExists) {
    $k8sVault = Get-Content $k8sVaultPath -Raw
    Assert-Condition "kubernetes/security/vault.yml defines vault-config ConfigMap" ($k8sVault -match "kind:\s*ConfigMap[\s\S]*name:\s*vault-config")
    Assert-Condition "kubernetes/security/vault.yml defines vault Deployment" ($k8sVault -match "kind:\s*Deployment[\s\S]*name:\s*vault")
    Assert-Condition "kubernetes/security/vault.yml configures IPC_LOCK securityContext" ($k8sVault -match "IPC_LOCK")
    Assert-Condition "kubernetes/security/vault.yml defines liveness and readiness health probes" ($k8sVault -match "/v1/sys/health")
    Assert-Condition "kubernetes/security/vault.yml defines vault-service ClusterIP on 8200" ($k8sVault -match "kind:\s*Service[\s\S]*name:\s*vault-service")
}

# ------------------------------------------------------------------------------
# 4. Zero-Trust NetworkPolicies & Microsegmentation
# ------------------------------------------------------------------------------
Write-Host "`n--- 4. Zero-Trust NetworkPolicies & Microsegmentation ---" -ForegroundColor Yellow

$netPolPath = Join-Path $K8sDir "security/network-policies.yml"
$netPolExists = Test-Path $netPolPath
Assert-Condition "kubernetes/security/network-policies.yml manifest exists" $netPolExists

if ($netPolExists) {
    $netPol = Get-Content $netPolPath -Raw
    Assert-Condition "NetworkPolicy defines default-deny-ingress" ($netPol -match "name:\s*default-deny-ingress")
    Assert-Condition "NetworkPolicy allows ingress to API Gateway (8080)" ($netPol -match "name:\s*allow-ingress-to-gateway")
    Assert-Condition "NetworkPolicy allows API Gateway to downstream services (8081-8084)" ($netPol -match "name:\s*allow-gateway-to-services")
    Assert-Condition "NetworkPolicy permits inter-service calls (order-service -> product-service:8082)" ($netPol -match "name:\s*allow-inter-service")
    Assert-Condition "NetworkPolicy restricts database access to services (5432, 6379)" ($netPol -match "name:\s*allow-databases")
    Assert-Condition "NetworkPolicy restricts Kafka access (9092, 2181)" ($netPol -match "name:\s*allow-messaging")
    Assert-Condition "NetworkPolicy permits discovery, config and vault access (8761, 8888, 8200)" ($netPol -match "name:\s*allow-platform-infrastructure")
    Assert-Condition "NetworkPolicy permits Prometheus actuator scraping" ($netPol -match "name:\s*allow-observability")
    Assert-Condition "NetworkPolicy permits microfrontend UI traffic (3000-3004)" ($netPol -match "name:\s*allow-frontend")
}

$kustomizePath = Join-Path $K8sDir "kustomization.yml"
$kustomize = Get-Content $kustomizePath -Raw
Assert-Condition "kustomization.yml includes security/vault.yml" ($kustomize -match "security/vault\.yml")
Assert-Condition "kustomization.yml includes security/network-policies.yml" ($kustomize -match "security/network-policies\.yml")

# ------------------------------------------------------------------------------
# 5. Production SSL/TLS, mTLS & PKI Certificates
# ------------------------------------------------------------------------------
Write-Host "`n--- 5. Production SSL/TLS, mTLS & PKI Certificates ---" -ForegroundColor Yellow

$prodProfilePath = Join-Path $BackendDir "config-repo/application-prod.yml"
$prodProfileExists = Test-Path $prodProfilePath
Assert-Condition "backend/config-repo/application-prod.yml exists" $prodProfileExists

if ($prodProfileExists) {
    $prodProfile = Get-Content $prodProfilePath -Raw
    Assert-Condition "application-prod.yml enables server.ssl" ($prodProfile -match "server:\s+ssl:\s+enabled:")
    Assert-Condition "application-prod.yml enforces TLSv1.3 and TLSv1.2" ($prodProfile -match "enabled-protocols:.*TLSv1\.3")
    Assert-Condition "application-prod.yml configures modern AEAD ciphers" ($prodProfile -match "TLS_AES_128_GCM_SHA256")
    Assert-Condition "application-prod.yml configures PKCS12 keystore" ($prodProfile -match "key-store-type:\s*PKCS12")
    Assert-Condition "application-prod.yml configures PKCS12 truststore" ($prodProfile -match "trust-store-type:\s*PKCS12")
    Assert-Condition "application-prod.yml configures client-auth for mTLS" ($prodProfile -match "client-auth:")
    Assert-Condition "application-prod.yml configures secure HTTP cookies" ($prodProfile -match "http-only:\s*true")
    Assert-Condition "application-prod.yml configures Spring Cloud Vault KV secret engine" ($prodProfile -match "spring:\s+cloud:\s+vault:")
}

$generateCertsPath = Join-Path $DevopsDir "scripts/generate-certs.ps1"
Assert-Condition "devops/scripts/generate-certs.ps1 exists" (Test-Path $generateCertsPath)

$certsDir = Join-Path $DevopsDir "certs"
Assert-Condition "Generated keystore.p12 exists" (Test-Path (Join-Path $certsDir "keystore.p12"))
Assert-Condition "Generated truststore.p12 exists" (Test-Path (Join-Path $certsDir "truststore.p12"))
Assert-Condition "Generated public certificate ecommerce.crt exists" (Test-Path (Join-Path $certsDir "ecommerce.crt"))

# ------------------------------------------------------------------------------
# 6. CI/CD Pipeline Automated Security Audit
# ------------------------------------------------------------------------------
Write-Host "`n--- 6. CI/CD Automated Security Auditing Workflow ---" -ForegroundColor Yellow

$ciWorkflowPath = Join-Path $ProjectRoot ".github/workflows/ci-backend.yml"
$ciWorkflow = Get-Content $ciWorkflowPath -Raw

Assert-Condition "CI workflow defines owasp-dependency-scan job" ($ciWorkflow -match "owasp-dependency-scan:")
Assert-Condition "CI workflow runs dependency-check goal" ($ciWorkflow -match "dependency-check-maven:check")
Assert-Condition "CI workflow archives OWASP vulnerability reports" ($ciWorkflow -match "owasp-vulnerability-reports")
Assert-Condition "CI workflow docker-publish job requires owasp-dependency-scan gate" ($ciWorkflow -match "needs:\s*\[.*owasp-dependency-scan.*\]")

# ------------------------------------------------------------------------------
# Final Summary
# ------------------------------------------------------------------------------
Write-Host "`n=================================================================" -ForegroundColor Cyan
Write-Host "   Phase 13 Security Hardening Verification Summary             " -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host " Total Passed Assertions: $Passed" -ForegroundColor Green
Write-Host " Total Failed Assertions: $Failed" -ForegroundColor $(if ($Failed -eq 0) { "Green" } else { "Red" })
Write-Host "=================================================================`n" -ForegroundColor Cyan

if ($Failed -gt 0) {
    exit 1
} else {
    exit 0
}
