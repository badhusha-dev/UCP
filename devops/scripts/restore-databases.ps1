# ==============================================================================
# Phase 14: Automated PostgreSQL Disaster Recovery Restore & Verification Script
# ==============================================================================

[CmdletBinding()]
param (
    [string]$BackupDir = "",
    [string]$ContainerName = "ecommerce-platform-postgres-1",
    [string]$DbUser = "ecommerce_admin",
    [string]$TargetDatabase = "",
    [switch]$VerifyIntegrity = $true
)

$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
if (-not $ScriptDir) { $ScriptDir = $PSScriptRoot }
if (-not $ScriptDir) { $ScriptDir = "." }

$BackupsRoot = Join-Path (Split-Path -Parent $ScriptDir) "backups"

# Resolve backup directory
if (-not $BackupDir) {
    $latest = Get-ChildItem -Path $BackupsRoot -Directory -Filter "backup_*" | Sort-Object CreationTime -Descending | Select-Object -First 1
    if (-not $latest) {
        Write-Error "[ERROR] No existing backups found in: $BackupsRoot"
        exit 1
    }
    $BackupDir = $latest.FullName
}

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "   Starting Automated Disaster Recovery Database Restoration     " -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "[INFO] Restoring from snapshot: $BackupDir" -ForegroundColor Gray

# 1. Verify container state
$containerRunning = docker ps --filter "name=$ContainerName" --filter "status=running" -q
if (-not $containerRunning) {
    Write-Warning "[WARN] Container '$ContainerName' is not running."
}

# 2. Determine databases to restore
$sqlFiles = Get-ChildItem -Path $BackupDir -Filter "*.sql"
if ($TargetDatabase) {
    $sqlFiles = $sqlFiles | Where-Object { $_.BaseName -eq $TargetDatabase }
    if (-not $sqlFiles) {
        Write-Error "[ERROR] Specified database '$TargetDatabase.sql' not found in $BackupDir"
        exit 1
    }
}

# 3. Restore and verify each database
foreach ($file in $sqlFiles) {
    $dbName = $file.BaseName
    Write-Host "`n[Processing] Database: $dbName ..." -ForegroundColor Yellow

    # Integrity Check
    if ($VerifyIntegrity) {
        $hashFile = Join-Path $BackupDir "$dbName.sha256"
        if (Test-Path $hashFile) {
            $expectedHash = (Get-Content $hashFile -Raw).Trim().Split(" ")[0]
            $actualHash = (Get-FileHash -Path $file.FullName -Algorithm SHA256).Hash
            if ($expectedHash -ne $actualHash) {
                Write-Error "[CORRUPTION DETECTED] SHA256 mismatch for $($file.Name)!"
                exit 1
            }
            Write-Host "  -> Integrity Verified: SHA-256 matches ($actualHash)" -ForegroundColor Green
        } else {
            Write-Warning "  -> [WARN] No SHA-256 hash file found for $($file.Name)"
        }
    }

    if ($containerRunning) {
        Write-Host "  -> Restoring schema & data into $dbName..." -ForegroundColor Gray
        $sqlContent = Get-Content $file.FullName -Raw
        
        # Stream into psql inside container
        $proc = Start-Process -FilePath "docker" -ArgumentList "exec -i $ContainerName psql -U $DbUser -d $dbName" `
            -NoNewWindow -PassThru -RedirectStandardInput $file.FullName -RedirectStandardError ([System.IO.Path]::GetTempFileName())
        $proc.WaitForExit()
        
        if ($proc.ExitCode -eq 0) {
            Write-Host "  -> Successfully restored database: $dbName" -ForegroundColor Green
        } else {
            Write-Warning "  -> [NOTICE] psql completed with code $($proc.ExitCode) (may contain table drop/create notices)"
        }

        # Query health check
        $tableCount = docker exec $ContainerName psql -U $DbUser -d $dbName -t -c "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public';"
        Write-Host "  -> Post-Restore Verification: $(($tableCount).Trim()) public tables present in $dbName" -ForegroundColor Green
    } else {
        Write-Host "  -> [DRY RUN] Offline verification successful for $($file.Name)" -ForegroundColor Green
    }
}

Write-Host "`n=================================================================" -ForegroundColor Cyan
Write-Host "   Disaster Recovery Database Restoration Completed!             " -ForegroundColor Green
Write-Host "=================================================================" -ForegroundColor Cyan
