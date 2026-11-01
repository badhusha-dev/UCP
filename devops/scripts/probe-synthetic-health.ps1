# ==============================================================================
# Universal Commerce Platform - Synthetic Health Prober
# Phase 18: Production Observability, Alerting & SLO Automation
# ==============================================================================

param (
    [string]$ReportPath = "devops/reports/synthetic-probe-latest.json",
    [int]$TimeoutSeconds = 5
)

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
if (-not $ScriptDir) { $ScriptDir = $PSScriptRoot }
$ProjectRoot = (Resolve-Path (Join-Path $ScriptDir "../..")).Path

$FullReportPath = Join-Path $ProjectRoot $ReportPath
$ReportDir = Split-Path -Parent $FullReportPath
if (-not (Test-Path $ReportDir)) {
    New-Item -ItemType Directory -Path $ReportDir -Force | Out-Null
}

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "  Universal Commerce - Synthetic Health and SLO Edge Prober      " -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan
$nowStr = (Get-Date).ToUniversalTime().ToString("yyyy-MM-dd HH:mm:ss")
Write-Host " Timestamp: $nowStr UTC"
Write-Host " Report:    $FullReportPath"
Write-Host ""

$Targets = @(
    @{ Name = "Discovery Server";       Category = "Platform";        Url = "http://localhost:8761/";                      ExpectedStatus = 200 },
    @{ Name = "Config Server";          Category = "Platform";        Url = "http://localhost:8888/actuator/health";       ExpectedStatus = 200 },
    @{ Name = "API Gateway Core";       Category = "Gateway";         Url = "http://localhost:8080/actuator/health";       ExpectedStatus = 200 },
    @{ Name = "API Gateway Route";      Category = "Gateway Route";   Url = "http://localhost:8080/product-service/v3/api-docs"; ExpectedStatus = 200 },
    @{ Name = "User Microservice";      Category = "Domain Service";  Url = "http://localhost:8081/actuator/health";       ExpectedStatus = 200 },
    @{ Name = "Product Microservice";   Category = "Domain Service";  Url = "http://localhost:8082/actuator/health";       ExpectedStatus = 200 },
    @{ Name = "Order Microservice";     Category = "Domain Service";  Url = "http://localhost:8083/actuator/health";       ExpectedStatus = 200 },
    @{ Name = "Notification Service";   Category = "Domain Service";  Url = "http://localhost:8084/actuator/health";       ExpectedStatus = 200 },
    @{ Name = "Prometheus Server";      Category = "Observability";   Url = "http://localhost:9090/-/healthy";             ExpectedStatus = 200 },
    @{ Name = "Alertmanager";           Category = "Observability";   Url = "http://localhost:9093/-/healthy";             ExpectedStatus = 200 },
    @{ Name = "Grafana Dashboards";     Category = "Observability";   Url = "http://localhost:3030/api/health";            ExpectedStatus = 200 },
    @{ Name = "Shell App MFE";          Category = "Storefront MFE";  Url = "http://localhost:3000/";                      ExpectedStatus = 200 },
    @{ Name = "Product MFE";            Category = "Storefront MFE";  Url = "http://localhost:3001/";                      ExpectedStatus = 200 },
    @{ Name = "Order MFE";              Category = "Storefront MFE";  Url = "http://localhost:3002/";                      ExpectedStatus = 200 },
    @{ Name = "User MFE";               Category = "Storefront MFE";  Url = "http://localhost:3003/";                      ExpectedStatus = 200 }
)

$Results = @()
$PassedCount = 0
$FailedCount = 0
$TotalLatencyMs = 0

foreach ($target in $Targets) {
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $statusCode = 0
    $isSuccess = $false
    $errorMessage = ""

    try {
        $req = [System.Net.WebRequest]::Create($target.Url)
        $req.Timeout = $TimeoutSeconds * 1000
        $req.Method = "GET"
        
        $resp = $req.GetResponse()
        $statusCode = [int]$resp.StatusCode
        $resp.Close()

        if ($statusCode -eq $target.ExpectedStatus) {
            $isSuccess = $true
        } else {
            $errorMessage = "Expected HTTP " + $target.ExpectedStatus + " but got " + $statusCode
        }
    } catch [System.Net.WebException] {
        if ($_.Exception.Response) {
            $statusCode = [int]$_.Exception.Response.StatusCode
            if ($statusCode -eq $target.ExpectedStatus) {
                $isSuccess = $true
            } else {
                $errorMessage = "HTTP Error " + $statusCode
            }
        } else {
            $errorMessage = $_.Exception.Message
        }
    } catch {
        $errorMessage = $_.Exception.Message
    } finally {
        $sw.Stop()
    }

    $latencyMs = [math]::Round($sw.Elapsed.TotalMilliseconds, 1)
    $TotalLatencyMs += $latencyMs

    $n = $target.Name
    $c = $target.Category
    if ($isSuccess) {
        $PassedCount++
        $line = " [UP]   " + $n.PadRight(22) + " | " + $c.PadRight(14) + " | HTTP " + $statusCode + " | " + ("$latencyMs ms").PadLeft(10)
        Write-Host $line -ForegroundColor Green
    } else {
        $FailedCount++
        $line = " [DOWN] " + $n.PadRight(22) + " | " + $c.PadRight(14) + " | HTTP " + $statusCode + " | " + ("$latencyMs ms").PadLeft(10) + " | Error: " + $errorMessage
        Write-Host $line -ForegroundColor Red
    }

    $Results += [PSCustomObject]@{
        Name           = $target.Name
        Category       = $target.Category
        Url            = $target.Url
        ExpectedStatus = $target.ExpectedStatus
        ActualStatus   = $statusCode
        Healthy        = $isSuccess
        LatencyMs      = $latencyMs
        Error          = $errorMessage
    }
}

$TotalTargets = $Targets.Count
$AvailabilityPercent = [math]::Round(($PassedCount / $TotalTargets) * 100, 2)
$AvgLatencyMs = [math]::Round($TotalLatencyMs / $TotalTargets, 1)
$SloTarget = 99.0
$SloCompliant = ($AvailabilityPercent -ge $SloTarget)

Write-Host ""
Write-Host "-----------------------------------------------------------------" -ForegroundColor Cyan
Write-Host " Synthetic Health Summary:" -ForegroundColor Yellow
Write-Host "   Total Endpoints:      $TotalTargets"
Write-Host "   Healthy Endpoints:    $PassedCount" -ForegroundColor Green
if ($FailedCount -eq 0) {
    Write-Host "   Failed Endpoints:     0" -ForegroundColor Green
} else {
    Write-Host "   Failed Endpoints:     $FailedCount" -ForegroundColor Red
}
Write-Host "   Availability:         $AvailabilityPercent%" -ForegroundColor Green
Write-Host "   Avg Latency:          $AvgLatencyMs ms"
Write-Host "   SLO Target:           >= $SloTarget%"
if ($SloCompliant) {
    Write-Host "   SLO Status:           COMPLIANT" -ForegroundColor Green
} else {
    Write-Host "   SLO Status:           VIOLATION" -ForegroundColor Red
}
Write-Host "-----------------------------------------------------------------" -ForegroundColor Cyan
Write-Host ""

$ReportData = [PSCustomObject]@{
    Timestamp           = (Get-Date -Format 'o')
    TotalProbed         = $TotalTargets
    HealthyCount        = $PassedCount
    FailedCount         = $FailedCount
    AvailabilityPercent = $AvailabilityPercent
    AvgLatencyMs        = $AvgLatencyMs
    SloTargetPercent    = $SloTarget
    SloCompliant        = $SloCompliant
    Probes              = $Results
}

$ReportData | ConvertTo-Json -Depth 5 | Set-Content -Path $FullReportPath -Encoding UTF8
Write-Host " Detailed report written to: $FullReportPath" -ForegroundColor Cyan

if (-not $SloCompliant) {
    exit 1
}
exit 0
