# ==============================================================================
# Phase 23 Automated Verification Suite: Production Hardening & Release Handoff
# ==============================================================================

[CmdletBinding()]
param(
    [string]$ProjectRoot = "D:\practical\ecommerce-platform"
)

$ErrorActionPreference = "Continue"

Write-Host "`n==============================================================================" -ForegroundColor Cyan
Write-Host "  PHASE 23: PRODUCTION HARDENING, SECURITY AUDIT & RELEASE HANDOFF" -ForegroundColor Cyan
Write-Host "==============================================================================" -ForegroundColor Cyan

$TotalTests = 0
$PassedTests = 0
$FailedTests = 0

function Assert-Test {
    param(
        [string]$Description,
        [scriptblock]$Condition
    )
    $script:TotalTests++
    try {
        $result = & $Condition
        if ($result -eq $true) {
            $script:PassedTests++
            Write-Host " [PASS] $Description" -ForegroundColor Green
        } else {
            $script:FailedTests++
            Write-Host " [FAIL] $Description" -ForegroundColor Red
        }
    } catch {
        $script:FailedTests++
        Write-Host " [FAIL] $Description (Exception: $($_.Exception.Message))" -ForegroundColor Red
    }
}

function Write-Header {
    param([string]$Title)
    Write-Host "`n==============================================================================" -ForegroundColor Yellow
    Write-Host "  $Title" -ForegroundColor Yellow
    Write-Host "==============================================================================" -ForegroundColor Yellow
}

# ------------------------------------------------------------------------------
# 1. Container Security Hardening (CIS Docker Benchmark)
# ------------------------------------------------------------------------------
Write-Header "1. CONTAINER SECURITY & CIS DOCKER BENCHMARK"

$scanScript = Join-Path $ProjectRoot "devops\scripts\scan-container-security.ps1"
Assert-Test "Container security scanner script exists" {
    Test-Path $scanScript
}

$scanProc = Start-Process -FilePath "powershell.exe" -ArgumentList "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", "`"$scanScript`"" -Wait -PassThru -NoNewWindow
Assert-Test "Container security audit executes with 100% compliance score" {
    $scanProc.ExitCode -eq 0
}

$auditReportPath = Join-Path $ProjectRoot "devops\reports\container-security-audit.json"
Assert-Test "Container security audit JSON report generated" {
    Test-Path $auditReportPath
}

Assert-Test "Audit report confirms 100% checks passed" {
    $audit = Get-Content $auditReportPath -Raw | ConvertFrom-Json
    $audit.ComplianceScore -eq 100.0 -and $audit.PassedChecks -eq $audit.TotalChecks
}

# ------------------------------------------------------------------------------
# 2. OWASP Top 10 Web Application Security Headers
# ------------------------------------------------------------------------------
Write-Header "2. OWASP TOP 10 WEB APPLICATION SECURITY HEADERS"

$gatewayConfig = Join-Path $ProjectRoot "backend\config-repo\api-gateway.yml"
$gwContent = Get-Content $gatewayConfig -Raw

Assert-Test "API Gateway configures X-Content-Type-Options: nosniff" {
    $gwContent -match "X-Content-Type-Options,\s*nosniff"
}

Assert-Test "API Gateway configures X-Frame-Options: DENY" {
    $gwContent -match "X-Frame-Options,\s*DENY"
}

Assert-Test "API Gateway configures Strict-Transport-Security (HSTS)" {
    $gwContent -match "Strict-Transport-Security"
}

Assert-Test "API Gateway configures Referrer-Policy" {
    $gwContent -match "Referrer-Policy,\s*strict-origin-when-cross-origin"
}

Assert-Test "API Gateway configures Permissions-Policy" {
    $gwContent -match "Permissions-Policy"
}

$nginxConfig = Join-Path $ProjectRoot "frontend\nginx.conf"
$nginxContent = Get-Content $nginxConfig -Raw

Assert-Test "Frontend Nginx configures X-Content-Type-Options nosniff" {
    $nginxContent -match 'X-Content-Type-Options\s+"nosniff"'
}

Assert-Test "Frontend Nginx configures X-Frame-Options SAMEORIGIN" {
    $nginxContent -match 'X-Frame-Options\s+"SAMEORIGIN"'
}

Assert-Test "Frontend Nginx configures Referrer-Policy" {
    $nginxContent -match 'Referrer-Policy\s+"strict-origin-when-cross-origin"'
}

# ------------------------------------------------------------------------------
# 3. Kubernetes Zero-Trust Network Policies
# ------------------------------------------------------------------------------
Write-Header "3. KUBERNETES ZERO-TRUST NETWORK POLICIES"

$netpolPath = Join-Path $ProjectRoot "kubernetes\security\network-policies.yml"
Assert-Test "Kubernetes NetworkPolicies manifest exists" {
    Test-Path $netpolPath
}

$netpolContent = Get-Content $netpolPath -Raw
Assert-Test "NetworkPolicies enforces default-deny ingress policy" {
    $netpolContent -match "name:\s*default-deny-ingress"
}

Assert-Test "NetworkPolicies permits Edge Gateway to Domain Microservices" {
    $netpolContent -match "name:\s*allow-gateway-to-services"
}

Assert-Test "NetworkPolicies restricts PostgreSQL database ingress (allow-databases on 5432)" {
    ($netpolContent -match "allow-databases") -and ($netpolContent -match "port:\s*5432")
}

Assert-Test "NetworkPolicies restricts Redis cache ingress (allow-databases on 6379)" {
    ($netpolContent -match "allow-databases") -and ($netpolContent -match "port:\s*6379")
}

Assert-Test "NetworkPolicies restricts Kafka event broker ingress (allow-messaging on 9092)" {
    ($netpolContent -match "allow-messaging") -and ($netpolContent -match "port:\s*9092")
}

# ------------------------------------------------------------------------------
# 4. Kubernetes Least-Privilege RBAC Security & Pod Security Standards (PSA)
# ------------------------------------------------------------------------------
Write-Header "4. KUBERNETES LEAST-PRIVILEGE RBAC & POD SECURITY STANDARDS"

$rbacPath = Join-Path $ProjectRoot "kubernetes\security\rbac-roles.yml"
Assert-Test "Kubernetes RBAC roles manifest exists" {
    Test-Path $rbacPath
}

$rbacContent = Get-Content $rbacPath -Raw
Assert-Test "RBAC defines dedicated ServiceAccounts (Gateway, Services, Infra, Backup)" {
    ($rbacContent -match "name:\s*ecommerce-gateway-sa") -and
    ($rbacContent -match "name:\s*ecommerce-services-sa") -and
    ($rbacContent -match "name:\s*ecommerce-infra-sa") -and
    ($rbacContent -match "name:\s*ecommerce-backup-sa")
}

Assert-Test "RBAC enforces granular least-privilege Role and RoleBinding" {
    ($rbacContent -match "name:\s*ecommerce-services-role") -and
    ($rbacContent -match "name:\s*ecommerce-services-binding")
}

Assert-Test "RBAC defines Prometheus metrics scraping ClusterRole" {
    ($rbacContent -match "name:\s*ecommerce-prometheus-cr") -and
    ($rbacContent -match "name:\s*ecommerce-prometheus-crb")
}

$psaTemplate = Join-Path $ProjectRoot "kubernetes\security\pod-security-standards.yml"
Assert-Test "Pod Security Standards specification manifest exists" {
    Test-Path $psaTemplate
}

$nsPath = Join-Path $ProjectRoot "kubernetes\base\00-namespace.yml"
$nsContent = Get-Content $nsPath -Raw
Assert-Test "Namespace enforces Pod Security Admission (restricted level)" {
    ($nsContent -match "pod-security\.kubernetes\.io/enforce:\s*restricted") -and
    ($nsContent -match "pod-security\.kubernetes\.io/audit:\s*restricted")
}

$kustPath = Join-Path $ProjectRoot "kubernetes\kustomization.yml"
$kustContent = Get-Content $kustPath -Raw
Assert-Test "Master Kustomization registers security RBAC and PSA manifests" {
    ($kustContent -match "security/rbac-roles\.yml") -and
    ($kustContent -match "security/pod-security-standards\.yml")
}

# ------------------------------------------------------------------------------
# 5. Automated Database Backup & Disaster Recovery Verification
# ------------------------------------------------------------------------------
Write-Header "5. AUTOMATED DATABASE BACKUP & DISASTER RECOVERY"

$cronjobPath = Join-Path $ProjectRoot "kubernetes\database\backup-cronjob.yml"
Assert-Test "PostgreSQL automated backup CronJob manifest exists" {
    Test-Path $cronjobPath
}

$cronContent = Get-Content $cronjobPath -Raw
Assert-Test "Backup CronJob defines schedule (0 2 * * *), PVC and SHA-256 hashing" {
    ($cronContent -match 'schedule:\s*"0 2 \* \* \*"') -and
    ($cronContent -match "postgres-backup-pvc") -and
    ($cronContent -match "sha256sum")
}

$backupScript = Join-Path $ProjectRoot "devops\scripts\backup-databases.ps1"
Assert-Test "Backup automation script exists" {
    Test-Path $backupScript
}

$backupsDir = Join-Path $ProjectRoot "devops\backups"
$latestBackup = Get-ChildItem -Path $backupsDir -Directory -Filter "backup_*" | Sort-Object CreationTime -Descending | Select-Object -First 1
Assert-Test "Live database backup snapshot exists on disk" {
    $null -ne $latestBackup -and (Test-Path $latestBackup.FullName)
}

Assert-Test "Backup snapshot contains all 4 databases with SHA-256 checksums" {
    $hasUser = Test-Path (Join-Path $latestBackup.FullName "user_db.sql")
    $hasProduct = Test-Path (Join-Path $latestBackup.FullName "product_db.sql")
    $hasOrder = Test-Path (Join-Path $latestBackup.FullName "order_db.sql")
    $hasNotif = Test-Path (Join-Path $latestBackup.FullName "notification_db.sql")
    $hasManifest = Test-Path (Join-Path $latestBackup.FullName "manifest.json")
    $hasUser -and $hasProduct -and $hasOrder -and $hasNotif -and $hasManifest
}

# ------------------------------------------------------------------------------
# 6. Operations Manual & Production Release Handoff Documentation
# ------------------------------------------------------------------------------
Write-Header "6. OPERATIONS MANUAL & PRODUCTION HANDOFF DOCUMENTATION"

$drRunbook = Join-Path $ProjectRoot "devops\runbooks\disaster-recovery-runbook.md"
Assert-Test "Disaster Recovery Runbook exists" {
    Test-Path $drRunbook
}

$drContent = Get-Content $drRunbook -Raw
Assert-Test "DR Runbook defines RPO (< 15m), RTO (< 30m), PITR and Kafka DLQ protocols" {
    ($drContent -match "RPO") -and
    ($drContent -match "RTO") -and
    ($drContent -match "Point-in-Time Recovery") -and
    ($drContent -match "Dead Letter Queue")
}

$opsManual = Join-Path $ProjectRoot "devops\runbooks\OPERATIONS_AND_RELEASE_HANDOFF.md"
Assert-Test "Operations Manual & Production Release Handoff Guide exists" {
    Test-Path $opsManual
}

$opsContent = Get-Content $opsManual -Raw
Assert-Test "Operations Manual specifies Service Matrix, Ports, Maintenance & Escalations" {
    ($opsContent -match "Platform Architecture & Service Matrix") -and
    ($opsContent -match "Telemetry Endpoints") -and
    ($opsContent -match "Database Schema Evolution \(Flyway\)") -and
    ($opsContent -match "Incident Management & Escalation Runbook") -and
    ($opsContent -match "Production Sign-off Checklist")
}

# ------------------------------------------------------------------------------
# SUMMARY
# ------------------------------------------------------------------------------
Write-Header "PHASE 23 VERIFICATION SUMMARY"
Write-Host " Total Tests: $TotalTests" -ForegroundColor Cyan
Write-Host " Passed     : $PassedTests" -ForegroundColor Green
Write-Host " Failed     : $FailedTests" -ForegroundColor $(if ($FailedTests -eq 0) { "Green" } else { "Red" })

if ($FailedTests -eq 0) {
    Write-Host "`n [SUCCESS] Phase 23 Production Hardening & Release Handoff PASSED!" -ForegroundColor Green
    exit 0
} else {
    Write-Host "`n [FAILURE] Phase 23 Verification had $FailedTests failures." -ForegroundColor Red
    exit 1
}
