# ==============================================================================
# Enterprise Platform - Production Release Packaging Script (v2.0.0-PROD)
# ==============================================================================

[CmdletBinding()]
param(
    [string]$ProjectRoot = "D:\practical\ecommerce-platform",
    [string]$OutputDir = "D:\practical",
    [string]$ReleaseVersion = "v2.0.0-prod"
)

$ErrorActionPreference = "Stop"

Write-Host "==============================================================================" -ForegroundColor Cyan
Write-Host "  PACKAGING ENTERPRISE PRODUCTION RELEASE BUNDLE ($ReleaseVersion)" -ForegroundColor Cyan
Write-Host "==============================================================================" -ForegroundColor Cyan

$zipPath = Join-Path $OutputDir "ecommerce-platform-$ReleaseVersion.zip"
if (Test-Path $zipPath) {
    Remove-Item $zipPath -Force
}

$tempStaging = Join-Path $env:TEMP "ecommerce-staging-$((Get-Date).Ticks)"
New-Item -ItemType Directory -Path $tempStaging -Force | Out-Null

Write-Host "[1/4] Staging production release files..." -ForegroundColor Yellow

# Copy files excluding heavy build artifacts (target, node_modules, dist)
$excludePatterns = @("*\node_modules\*", "*\target\*", "*\.git\*")

robocopy $ProjectRoot $tempStaging /E /XD node_modules target .git /XF *.log *.tmp /NFL /NDL /NJH /NJS /nc /ns /np | Out-Null

Write-Host "[2/4] Injecting master blueprint PDF and release notes..." -ForegroundColor Yellow
$blueprintSource = "D:\practical\Universal_Commerce_Business_Blueprint.pdf"
if (Test-Path $blueprintSource) {
    Copy-Item $blueprintSource -Destination (Join-Path $tempStaging "Universal_Commerce_Business_Blueprint.pdf") -Force
}

Write-Host "[3/4] Compressing production release archive..." -ForegroundColor Yellow
Compress-Archive -Path "$tempStaging\*" -DestinationPath $zipPath -CompressionLevel Optimal

Write-Host "[4/4] Generating release checksums..." -ForegroundColor Yellow
$hash = Get-FileHash -Path $zipPath -Algorithm SHA256
$hashFile = "$zipPath.sha256"
"$($hash.Hash)  $(Split-Path $zipPath -Leaf)" | Out-File -FilePath $hashFile -Encoding ascii

# Cleanup staging
Remove-Item $tempStaging -Recurse -Force

$item = Get-Item $zipPath
$sizeMb = [Math]::Round($item.Length / 1MB, 2)

Write-Host "`n==============================================================================" -ForegroundColor Green
Write-Host "  PRODUCTION RELEASE BUNDLE CREATED SUCCESSFULLY" -ForegroundColor Green
Write-Host "==============================================================================" -ForegroundColor Green
Write-Host " Archive:  $($item.FullName)" -ForegroundColor Cyan
Write-Host " Size:     $sizeMb MB" -ForegroundColor Cyan
Write-Host " SHA-256:  $($hash.Hash)" -ForegroundColor Cyan
Write-Host " Checksum: $hashFile" -ForegroundColor Cyan
Write-Host "==============================================================================" -ForegroundColor Green
