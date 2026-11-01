# ==============================================================================
# Phase 13: Enterprise PKI & Mutual TLS (mTLS) Certificate Generator
# ==============================================================================

[CmdletBinding()]
param (
    [string]$OutputDir = "",
    [string]$KeystorePassword = "changeit",
    [string]$KeyAlias = "ecommerce",
    [int]$ValidityDays = 365,
    [string]$DName = "CN=ecommerce-platform, OU=Engineering, O=Enterprise, L=SanFrancisco, ST=CA, C=US"
)

$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
if (-not $ScriptDir) { $ScriptDir = $PSScriptRoot }
if (-not $ScriptDir) { $ScriptDir = "." }

if (-not $OutputDir) {
    $OutputDir = Join-Path (Split-Path -Parent $ScriptDir) "certs"
}

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "   Generating Enterprise PKI, mTLS Keystores & Truststores       " -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan

if (-not (Test-Path $OutputDir)) {
    New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null
    Write-Host "[INFO] Created directory: $OutputDir" -ForegroundColor Gray
}

# Resolve keytool executable
$keytoolPath = $null
$keytoolCmd = Get-Command "keytool" -ErrorAction SilentlyContinue
if ($keytoolCmd) {
    $keytoolPath = $keytoolCmd.Source
} elseif ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME "bin\keytool.exe"))) {
    $keytoolPath = (Join-Path $env:JAVA_HOME "bin\keytool.exe")
}

if (-not $keytoolPath) {
    Write-Error "[ERROR] keytool executable not found in PATH or JAVA_HOME."
    exit 1
}

Write-Host "[INFO] Using keytool: $keytoolPath" -ForegroundColor Gray

$keystorePath = Join-Path $OutputDir "keystore.p12"
$truststorePath = Join-Path $OutputDir "truststore.p12"
$certPath = Join-Path $OutputDir "ecommerce.crt"

# Clean up older artifacts if present
if (Test-Path $keystorePath) { Remove-Item $keystorePath -Force }
if (Test-Path $truststorePath) { Remove-Item $truststorePath -Force }
if (Test-Path $certPath) { Remove-Item $certPath -Force }

Write-Host "[1/3] Generating PKCS12 Server Keystore ($keystorePath)..." -ForegroundColor Yellow
& $keytoolPath -genkeypair `
    -alias $KeyAlias `
    -keyalg RSA `
    -keysize 2048 `
    -sigalg SHA256withRSA `
    -validity $ValidityDays `
    -keystore $keystorePath `
    -storepass $KeystorePassword `
    -keypass $KeystorePassword `
    -storetype PKCS12 `
    -dname $DName `
    -ext "SAN=dns:localhost,dns:api-gateway,dns:user-service,dns:product-service,dns:order-service,dns:notification-service,ip:127.0.0.1"

if (-not (Test-Path $keystorePath)) {
    Write-Error "[ERROR] Failed to generate $keystorePath"
    exit 1
}
Write-Host "  -> Keystore successfully generated." -ForegroundColor Green

Write-Host "[2/3] Exporting Public Certificate ($certPath)..." -ForegroundColor Yellow
& $keytoolPath -exportcert `
    -alias $KeyAlias `
    -keystore $keystorePath `
    -storepass $KeystorePassword `
    -storetype PKCS12 `
    -file $certPath `
    -rfc

if (-not (Test-Path $certPath)) {
    Write-Error "[ERROR] Failed to export public certificate."
    exit 1
}
Write-Host "  -> Public certificate successfully exported." -ForegroundColor Green

Write-Host "[3/3] Creating PKCS12 Client Truststore ($truststorePath)..." -ForegroundColor Yellow
& $keytoolPath -importcert `
    -noprompt `
    -alias "$KeyAlias-ca" `
    -file $certPath `
    -keystore $truststorePath `
    -storepass $KeystorePassword `
    -storetype PKCS12

if (-not (Test-Path $truststorePath)) {
    Write-Error "[ERROR] Failed to generate $truststorePath"
    exit 1
}
Write-Host "  -> Truststore successfully generated." -ForegroundColor Green

# Optional: Generate PEM format if openssl is available
$opensslCmd = Get-Command "openssl" -ErrorAction SilentlyContinue
if ($opensslCmd) {
    Write-Host "[INFO] OpenSSL detected. Exporting PEM certificates and keys..." -ForegroundColor Gray
    $pemKeyPath = Join-Path $OutputDir "server.key"
    $pemCertPath = Join-Path $OutputDir "server.pem"
    
    & $opensslCmd.Source pkcs12 -in $keystorePath -nodes -nocerts -out $pemKeyPath -passin "pass:$KeystorePassword"
    & $opensslCmd.Source pkcs12 -in $keystorePath -nokeys -out $pemCertPath -passin "pass:$KeystorePassword"
    Write-Host "  -> Exported server.key and server.pem for NGINX/Vault ingress." -ForegroundColor Green
}

Write-Host "`n=================================================================" -ForegroundColor Cyan
Write-Host "   mTLS & PKI Certificates Ready in: $OutputDir" -ForegroundColor Green
Write-Host "=================================================================" -ForegroundColor Cyan
