# ==============================================================================
# Phase 14: Chaos Engineering, Resilience & Disaster Recovery Verification Script
# ==============================================================================

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "   Starting Phase 14: Chaos & Disaster Recovery Verification     " -ForegroundColor Cyan
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
$K8sDir = Join-Path $ProjectRoot "kubernetes"
$DevopsDir = Join-Path $ProjectRoot "devops"

# ------------------------------------------------------------------------------
# 1. Chaos Mesh & Fault Injection Manifests
# ------------------------------------------------------------------------------
Write-Host "`n--- 1. Chaos Mesh & Fault Injection Manifests ---" -ForegroundColor Yellow

$chaosDir = Join-Path $K8sDir "chaos"
Assert-Condition "kubernetes/chaos directory exists" (Test-Path $chaosDir)

$netLatencyFile = Join-Path $chaosDir "network-latency-product-service.yml"
Assert-Condition "network-latency-product-service.yml exists" (Test-Path $netLatencyFile)
if (Test-Path $netLatencyFile) {
    $netLatency = Get-Content $netLatencyFile -Raw
    Assert-Condition "network-latency defines NetworkChaos kind" ($netLatency -match "kind:\s*NetworkChaos")
    Assert-Condition "network-latency targets product-service" ($netLatency -match "app:\s*product-service")
    Assert-Condition "network-latency defines 2000ms delay" ($netLatency -match "latency:\s*[`"']*2000ms[`"']*")
}

$podKillFile = Join-Path $chaosDir "pod-kill-order-service.yml"
Assert-Condition "pod-kill-order-service.yml exists" (Test-Path $podKillFile)
if (Test-Path $podKillFile) {
    $podKill = Get-Content $podKillFile -Raw
    Assert-Condition "pod-kill defines PodChaos kind" ($podKill -match "kind:\s*PodChaos")
    Assert-Condition "pod-kill targets order-service" ($podKill -match "app:\s*order-service")
    Assert-Condition "pod-kill action is pod-kill" ($podKill -match "action:\s*pod-kill")
}

$netPartitionFile = Join-Path $chaosDir "network-partition-db.yml"
Assert-Condition "network-partition-db.yml exists" (Test-Path $netPartitionFile)
if (Test-Path $netPartitionFile) {
    $netPart = Get-Content $netPartitionFile -Raw
    Assert-Condition "network-partition defines NetworkChaos partition" ($netPart -match "action:\s*partition")
    Assert-Condition "network-partition targets postgres" ($netPart -match "app:\s*postgres")
}

$kafkaLatencyFile = Join-Path $chaosDir "kafka-latency.yml"
Assert-Condition "kafka-latency.yml exists" (Test-Path $kafkaLatencyFile)
if (Test-Path $kafkaLatencyFile) {
    $kafkaLat = Get-Content $kafkaLatencyFile -Raw
    Assert-Condition "kafka-latency targets kafka broker" ($kafkaLat -match "app:\s*kafka")
    Assert-Condition "kafka-latency injects packet loss and latency" ($kafkaLat -match "loss:" -and $kafkaLat -match "latency:")
}

$chaosKustomize = Join-Path $chaosDir "kustomization.yml"
Assert-Condition "kubernetes/chaos/kustomization.yml exists" (Test-Path $chaosKustomize)

# ------------------------------------------------------------------------------
# 2. Kubernetes Backup CronJob & PVC Manifests
# ------------------------------------------------------------------------------
Write-Host "`n--- 2. Kubernetes Disaster Recovery & Backup CronJob ---" -ForegroundColor Yellow

$backupJobFile = Join-Path $K8sDir "database/backup-cronjob.yml"
Assert-Condition "kubernetes/database/backup-cronjob.yml exists" (Test-Path $backupJobFile)

if (Test-Path $backupJobFile) {
    $backupJob = Get-Content $backupJobFile -Raw
    Assert-Condition "backup manifest defines postgres-backup-pvc" ($backupJob -match "name:\s*postgres-backup-pvc")
    Assert-Condition "backup manifest defines postgres-backup-job CronJob" ($backupJob -match "kind:\s*CronJob[\s\S]*name:\s*postgres-backup-job")
    Assert-Condition "backup CronJob runs on scheduled cadence" ($backupJob -match "schedule:\s*[`"']*0 2 \* \* \*[`"']*")
    Assert-Condition "backup CronJob executes pg_dump for all databases" ($backupJob -match "for DB in user_db product_db order_db notification_db")
    Assert-Condition "backup CronJob calculates sha256sum" ($backupJob -match "sha256sum")
    Assert-Condition "backup CronJob enforces 7-day retention cleanup" ($backupJob -match "find /backups.*-mtime \+7")
}

$masterKustomize = Join-Path $K8sDir "kustomization.yml"
$masterKustContent = Get-Content $masterKustomize -Raw
Assert-Condition "master kustomization.yml includes database/backup-cronjob.yml" ($masterKustContent -match "database/backup-cronjob\.yml")

# ------------------------------------------------------------------------------
# 3. Disaster Recovery Automation Scripts
# ------------------------------------------------------------------------------
Write-Host "`n--- 3. Disaster Recovery Automation Scripts ---" -ForegroundColor Yellow

$backupPs1 = Join-Path $DevopsDir "scripts/backup-databases.ps1"
$restorePs1 = Join-Path $DevopsDir "scripts/restore-databases.ps1"
$backupSh = Join-Path $DevopsDir "scripts/backup-databases.sh"
$restoreSh = Join-Path $DevopsDir "scripts/restore-databases.sh"
$simulatePs1 = Join-Path $DevopsDir "scripts/simulate-chaos.ps1"

Assert-Condition "backup-databases.ps1 exists" (Test-Path $backupPs1)
Assert-Condition "restore-databases.ps1 exists" (Test-Path $restorePs1)
Assert-Condition "backup-databases.sh exists" (Test-Path $backupSh)
Assert-Condition "restore-databases.sh exists" (Test-Path $restoreSh)
Assert-Condition "simulate-chaos.ps1 exists" (Test-Path $simulatePs1)

# ------------------------------------------------------------------------------
# 4. Snapshot Integrity & Checksum Verification
# ------------------------------------------------------------------------------
Write-Host "`n--- 4. Snapshot Integrity & Checksum Verification ---" -ForegroundColor Yellow

$backupsRoot = Join-Path $DevopsDir "backups"
Assert-Condition "devops/backups directory exists" (Test-Path $backupsRoot)

$latestSnapshot = Get-ChildItem -Path $backupsRoot -Directory -Filter "backup_*" | Sort-Object CreationTime -Descending | Select-Object -First 1
Assert-Condition "At least one valid backup snapshot exists" ($latestSnapshot -ne $null)

if ($latestSnapshot) {
    Write-Host " [INFO] Inspecting latest snapshot: $($latestSnapshot.Name)" -ForegroundColor Gray
    
    $manifestPath = Join-Path $latestSnapshot.FullName "manifest.json"
    Assert-Condition "Snapshot contains manifest.json" (Test-Path $manifestPath)

    foreach ($db in @("user_db", "product_db", "order_db", "notification_db")) {
        $sqlPath = Join-Path $latestSnapshot.FullName "$db.sql"
        $hashPath = Join-Path $latestSnapshot.FullName "$db.sha256"
        
        Assert-Condition "Snapshot contains $db.sql" (Test-Path $sqlPath)
        Assert-Condition "Snapshot contains $db.sha256" (Test-Path $hashPath)

        if ((Test-Path $sqlPath) -and (Test-Path $hashPath)) {
            $expected = (Get-Content $hashPath -Raw).Trim().Split(" ")[0]
            $actual = (Get-FileHash -Path $sqlPath -Algorithm SHA256).Hash
            Assert-Condition "SHA-256 integrity verified for $db.sql" ($expected -eq $actual)
        }
    }
}

# ------------------------------------------------------------------------------
# 5. Live Chaos & Resilience Telemetry Verification
# ------------------------------------------------------------------------------
Write-Host "`n--- 5. Live Chaos & Resilience Telemetry Simulation ---" -ForegroundColor Yellow

$chaosOutput = & powershell -ExecutionPolicy Bypass -File $simulatePs1
$chaosSuccess = ($LASTEXITCODE -eq 0)
Assert-Condition "simulate-chaos.ps1 executed successfully with zero failures" $chaosSuccess

# ------------------------------------------------------------------------------
# Final Summary
# ------------------------------------------------------------------------------
Write-Host "`n=================================================================" -ForegroundColor Cyan
Write-Host "   Phase 14 Chaos & Disaster Recovery Verification Summary       " -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host " Total Passed Assertions: $Passed" -ForegroundColor Green
Write-Host " Total Failed Assertions: $Failed" -ForegroundColor $(if ($Failed -eq 0) { "Green" } else { "Red" })
Write-Host "=================================================================`n" -ForegroundColor Cyan

if ($Failed -gt 0) {
    exit 1
} else {
    exit 0
}
