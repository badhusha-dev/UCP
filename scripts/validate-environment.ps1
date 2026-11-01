# ==============================================================================
# Universal Commerce Platform - Environment Validation Script (Windows PowerShell)
# ==============================================================================
[CmdletBinding()]
param()

$ErrorActionPreference = "Continue"

Write-Host "==============================================================================" -ForegroundColor Cyan
Write-Host "    UNIVERSAL COMMERCE PLATFORM - ENVIRONMENT VALIDATION (PRE-INSTALL)       " -ForegroundColor Cyan
Write-Host "==============================================================================" -ForegroundColor Cyan

$ErrorsCount = 0
$WarningsCount = 0

# 1. Operating System
Write-Host -NoNewline "[CHECK] Operating System: "
$os = Get-CimInstance Win32_OperatingSystem
Write-Host "$($os.Caption) ($($os.OSArchitecture)) ... " -NoNewline
Write-Host "PASS" -ForegroundColor Green

# 2. CPU Cores
Write-Host -NoNewline "[CHECK] CPU Logical Cores: "
$cores = (Get-CimInstance Win32_ComputerSystem).NumberOfLogicalProcessors
Write-Host "$cores ... " -NoNewline
if ($cores -ge 4) {
    Write-Host "PASS" -ForegroundColor Green
} elseif ($cores -ge 2) {
    Write-Host "WARN (2+ met, 4+ recommended)" -ForegroundColor Yellow
    $WarningsCount++
} else {
    Write-Host "FAIL (At least 2 cores required)" -ForegroundColor Red
    $ErrorsCount++
}

# 3. RAM Check
Write-Host -NoNewline "[CHECK] System RAM: "
$totalRamGb = [math]::Round((Get-CimInstance Win32_PhysicalMemory | Measure-Object -Property Capacity -Sum).Sum / 1GB, 1)
Write-Host "$totalRamGb GB ... " -NoNewline
if ($totalRamGb -ge 14.5) {
    Write-Host "PASS" -ForegroundColor Green
} elseif ($totalRamGb -ge 7.5) {
    Write-Host "WARN (8GB minimum met, 16GB recommended for full ELK observability)" -ForegroundColor Yellow
    $WarningsCount++
} else {
    Write-Host "FAIL (Minimum 8 GB RAM required)" -ForegroundColor Red
    $ErrorsCount++
}

# 4. Free Disk Space Check
Write-Host -NoNewline "[CHECK] Free Disk Space (Current Drive): "
$currentDrive = (Get-Location).Drive.Name
$drive = Get-CimInstance Win32_LogicalDisk | Where-Object { $_.DeviceID -eq "$($currentDrive):" }
$freeGb = [math]::Round($drive.FreeSpace / 1GB, 1)
Write-Host "$freeGb GB ... " -NoNewline
if ($freeGb -ge 25) {
    Write-Host "PASS" -ForegroundColor Green
} elseif ($freeGb -ge 15) {
    Write-Host "WARN (15GB minimum met, 25GB+ recommended)" -ForegroundColor Yellow
    $WarningsCount++
} else {
    Write-Host "FAIL (At least 15 GB free disk required)" -ForegroundColor Red
    $ErrorsCount++
}

# 5. Docker Engine
Write-Host -NoNewline "[CHECK] Docker Engine Installation: "
try {
    $dockerVer = docker --version
    Write-Host "$dockerVer ... PASS" -ForegroundColor Green
} catch {
    Write-Host "FAIL (Docker not detected in PATH)" -ForegroundColor Red
    $ErrorsCount++
}

# 6. Docker Compose
Write-Host -NoNewline "[CHECK] Docker Compose (v2): "
try {
    $composeVer = docker compose version
    Write-Host "$composeVer ... PASS" -ForegroundColor Green
} catch {
    Write-Host "FAIL ('docker compose' plugin missing)" -ForegroundColor Red
    $ErrorsCount++
}

# 7. Port Conflicts
Write-Host -NoNewline "[CHECK] Port Availability: "
$ports = @(3000, 3001, 3002, 3003, 8080, 8081, 8082, 8083, 8084, 8761, 8888, 5432, 6379, 9092, 2181, 8090, 9090, 3030, 9093, 9411, 9200, 5000, 5601)
$activePorts = Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue | Select-Object -ExpandProperty LocalPort -Unique
$conflicts = @()

foreach ($p in $ports) {
    if ($activePorts -contains $p) {
        # Check if already running via Docker
        $dockerUsing = docker ps --format '{{.Ports}}' 2>$null | Select-String ":$p->"
        if (-not $dockerUsing) {
            $conflicts += $p
        }
    }
}

if ($conflicts.Count -eq 0) {
    Write-Host "PASS (All 23 platform ports available)" -ForegroundColor Green
} else {
    Write-Host "WARN (External ports currently bound: $($conflicts -join ', '))" -ForegroundColor Yellow
    $WarningsCount++
}

Write-Host "------------------------------------------------------------------------------" -ForegroundColor Cyan
if ($ErrorsCount -eq 0) {
    Write-Host "[SUCCESS] Environment validation passed! ($WarningsCount warning(s))" -ForegroundColor Green
    Write-Host "System is ready for Universal Commerce Platform installation." -ForegroundColor Green
    exit 0
} else {
    Write-Host "[ERROR] Environment validation failed with $ErrorsCount critical error(s)." -ForegroundColor Red
    Write-Host "Please resolve the above prerequisites before running installation." -ForegroundColor Red
    exit 1
}
