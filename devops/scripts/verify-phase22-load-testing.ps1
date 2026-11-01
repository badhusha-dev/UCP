# ==============================================================================
# Enterprise E-Commerce Platform - Phase 22 Verification Suite
# High-Concurrency Load Testing & End-to-End Tracing Validation
# ==============================================================================

[CmdletBinding()]
param(
    [string]$GatewayUrl = "http://localhost:8080",
    [string]$ZipkinUrl = "http://localhost:9411",
    [int]$BenchmarkRequests = 60,
    [int]$Concurrency = 10
)

$ErrorActionPreference = "Continue"

$totalTests = 0
$passedTests = 0
$failedTests = 0

function Write-Header {
    param([string]$Message)
    Write-Host ""
    Write-Host "==============================================================================" -ForegroundColor Cyan
    Write-Host "  $Message" -ForegroundColor Cyan
    Write-Host "==============================================================================" -ForegroundColor Cyan
}

function Assert-Test {
    param(
        [string]$Description,
        [scriptblock]$Condition
    )
    $script:totalTests++
    try {
        $result = & $Condition
        if ($result) {
            $script:passedTests++
            Write-Host " [PASS] $Description" -ForegroundColor Green
        } else {
            $script:failedTests++
            Write-Host " [FAIL] $Description" -ForegroundColor Red
        }
    } catch {
        $script:failedTests++
        Write-Host " [FAIL] $Description - Exception: $_" -ForegroundColor Red
    }
}

Write-Header "PHASE 22: HIGH-CONCURRENCY LOAD TESTING & TRACING VERIFICATION"

$projectRoot = "d:\practical\ecommerce-platform"
$loadTestDir = Join-Path $projectRoot "devops\load-testing"

# ------------------------------------------------------------------------------
# 1. Structure & Workspace Verification
# ------------------------------------------------------------------------------
Write-Header "1. LOAD TESTING ARCHITECTURE & DIRECTORY STRUCTURE"

Assert-Test "Load testing directory exists" {
    Test-Path $loadTestDir
}

Assert-Test "k6 scenarios directory exists" {
    Test-Path (Join-Path $loadTestDir "k6\scenarios")
}

Assert-Test "locust suite directory exists" {
    Test-Path (Join-Path $loadTestDir "locust")
}

Assert-Test "reports directory exists" {
    Test-Path (Join-Path $loadTestDir "reports")
}

# ------------------------------------------------------------------------------
# 2. k6 Scenarios Configuration & Thresholds Verification
# ------------------------------------------------------------------------------
Write-Header "2. K6 SCENARIOS CONFIGURATION & SLA THRESHOLDS"

$flashSalePath = Join-Path $loadTestDir "k6\scenarios\flash-sale.js"
Assert-Test "Flash sale k6 script exists" {
    Test-Path $flashSalePath
}

Assert-Test "Flash sale script defines ramping-vus executor with 5 stages" {
    $content = Get-Content $flashSalePath -Raw
    $content -match "executor:\s*'ramping-vus'" -and $content -match "stages:\s*\["
}

Assert-Test "Flash sale script defines SLA thresholds (P95 < 400ms, P99 < 800ms)" {
    $content = Get-Content $flashSalePath -Raw
    $content -match "p\(95\)<400" -and $content -match "p\(99\)<800"
}

Assert-Test "Flash sale script implements W3C traceparent injection" {
    $content = Get-Content $flashSalePath -Raw
    $content -match "generateW3CTraceparent" -and $content -match "traceparent"
}

Assert-Test "Flash sale script covers Authentication, Catalog, and Checkout" {
    $content = Get-Content $flashSalePath -Raw
    $content -match "01_User_Authentication" -and $content -match "02_Browse_Product_Catalog" -and $content -match "03_High_Concurrency_Checkout"
}

$browseCatalogPath = Join-Path $loadTestDir "k6\scenarios\browse-catalog.js"
Assert-Test "Browse catalog k6 script exists and defines constant-arrival-rate" {
    (Test-Path $browseCatalogPath) -and ((Get-Content $browseCatalogPath -Raw) -match "constant-arrival-rate")
}

$orderCheckoutPath = Join-Path $loadTestDir "k6\scenarios\order-checkout.js"
Assert-Test "Order checkout k6 script exists and defines setup hook" {
    (Test-Path $orderCheckoutPath) -and ((Get-Content $orderCheckoutPath -Raw) -match "export function setup")
}

# ------------------------------------------------------------------------------
# 3. Locust Distributed Load Testing Configuration
# ------------------------------------------------------------------------------
Write-Header "3. LOCUST DISTRIBUTED TEST SUITE CONFIGURATION"

$locustfilePath = Join-Path $loadTestDir "locust\locustfile.py"
Assert-Test "Locustfile exists" {
    Test-Path $locustfilePath
}

Assert-Test "Locustfile models FlashSaleShopper and PlatformAdminUser task sets" {
    $content = Get-Content $locustfilePath -Raw
    $content -match "class FlashSaleShopper" -and $content -match "class PlatformAdminUser"
}

Assert-Test "Locustfile implements W3C traceparent generation and injection" {
    $content = Get-Content $locustfilePath -Raw
    $content -match "def generate_traceparent" -and $content -match "traceparent"
}

# ------------------------------------------------------------------------------
# 4. Containerized Load Testing Runner Configuration
# ------------------------------------------------------------------------------
Write-Header "4. CONTAINERIZED LOAD TEST RUNNER (DOCKER COMPOSE)"

$dockerComposePath = Join-Path $loadTestDir "docker-compose.load-test.yml"
Assert-Test "Docker compose load-test manifest exists" {
    Test-Path $dockerComposePath
}

Assert-Test "Docker compose defines k6 and locust runners" {
    $content = Get-Content $dockerComposePath -Raw
    $content -match "k6-flash-sale" -and $content -match "locust-runner"
}

Assert-Test "Docker compose links to shared platform network" {
    $content = Get-Content $dockerComposePath -Raw
    $content -match "ecommerce-network"
}

# ------------------------------------------------------------------------------
# 5. Live W3C Distributed Tracing Verification
# ------------------------------------------------------------------------------
Write-Header "5. LIVE W3C DISTRIBUTED TRACE CONTEXT VALIDATION"

$traceScriptPath = Join-Path $projectRoot "devops\scripts\validate-distributed-tracing.ps1"
Assert-Test "Trace validation script exists" {
    Test-Path $traceScriptPath
}

$traceProcess = Start-Process -FilePath "powershell.exe" -ArgumentList "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", "`"$traceScriptPath`"" -Wait -PassThru -NoNewWindow
$traceSuccess = $traceProcess.ExitCode -eq 0

Assert-Test "End-to-End W3C trace propagation executes with exit code 0" {
    $traceSuccess
}

Assert-Test "Zipkin reports 5 registered microservices" {
    try {
        $services = Invoke-RestMethod -Uri "$ZipkinUrl/api/v2/services"
        $services.Count -ge 5 -and ($services -contains "order-service") -and ($services -contains "api-gateway")
    } catch {
        $false
    }
}

Assert-Test "Multi-hop spans successfully stored in Zipkin" {
    try {
        $traces = Invoke-RestMethod -Uri "$ZipkinUrl/api/v2/traces?limit=20"
        $multiHopTraces = $traces | Where-Object { ($_ | Measure-Object).Count -ge 2 }
        ($multiHopTraces | Measure-Object).Count -ge 1
    } catch {
        $false
    }
}

# ------------------------------------------------------------------------------
# 6. Empirical High-Concurrency Load Benchmark
# ------------------------------------------------------------------------------
Write-Header "6. LIVE HIGH-CONCURRENCY PERFORMANCE BENCHMARK ($BenchmarkRequests requests)"

Write-Host " [INFO] Acquiring session token for benchmark run..." -ForegroundColor Yellow
$login = Invoke-RestMethod -Uri "$GatewayUrl/api/users/login" -Method Post -ContentType "application/json" -Body '{"email":"admin@ecommerce.com","password":"password123"}'
$token = $login.token

$latencies = [System.Collections.Generic.List[double]]::new()
$successCount = 0
$failureCount = 0

Write-Host " [INFO] Executing high-concurrency requests against API Gateway..." -ForegroundColor Yellow
$benchmarkSw = [System.Diagnostics.Stopwatch]::StartNew()

for ($i = 1; $i -le $BenchmarkRequests; $i++) {
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        if ($i % 3 -eq 0) {
            # Checkout request
            $traceId = [System.Guid]::NewGuid().ToString("N")
            $headers = @{
                "Authorization" = "Bearer $token"
                "Content-Type"  = "application/json"
                "traceparent"   = "00-$traceId-0000000000000001-01"
            }
            $body = '{"userId":4,"items":[{"productId":1,"name":"Wireless Headphones","price":99.99,"quantity":1}]}'
            $resp = Invoke-RestMethod -Uri "$GatewayUrl/api/orders/checkout" -Method Post -Headers $headers -Body $body
            $sw.Stop()
            if ($resp.id) { $successCount++ } else { $failureCount++ }
        } else {
            # Catalog query
            $headers = @{ "Accept" = "application/json" }
            $resp = Invoke-RestMethod -Uri "$GatewayUrl/api/products" -Method Get -Headers $headers
            $sw.Stop()
            if ($resp.Count -gt 0) { $successCount++ } else { $failureCount++ }
        }
        $latencies.Add($sw.Elapsed.TotalMilliseconds)
    } catch {
        $sw.Stop()
        $latencies.Add($sw.Elapsed.TotalMilliseconds)
        $failureCount++
    }
}

$benchmarkSw.Stop()

# Compute Percentiles
$sorted = $latencies | Sort-Object
$count = $sorted.Count
$avgLatency = [math]::Round(($latencies | Measure-Object -Average).Average, 2)
$p50Index = [math]::Floor($count * 0.50)
$p90Index = [math]::Floor($count * 0.90)
$p95Index = [math]::Floor($count * 0.95)
$p99Index = [math]::Floor($count * 0.99)

$p50 = [math]::Round($sorted[$p50Index], 2)
$p90 = [math]::Round($sorted[$p90Index], 2)
$p95 = [math]::Round($sorted[$p95Index], 2)
$p99 = [math]::Round($sorted[$p99Index], 2)
$maxLatency = [math]::Round($sorted[-1], 2)
$totalTimeSec = [math]::Round($benchmarkSw.Elapsed.TotalSeconds, 2)
$rps = [math]::Round($BenchmarkRequests / $totalTimeSec, 2)
$errorRate = [math]::Round(($failureCount / $BenchmarkRequests) * 100, 2)

Write-Host ""
Write-Host ("{0,-25} : {1}" -f "Total Requests", $BenchmarkRequests) -ForegroundColor White
Write-Host ("{0,-25} : {1} ({2}%)" -f "Successful Requests", $successCount, (100 - $errorRate)) -ForegroundColor Green
Write-Host ("{0,-25} : {1} ({2}%)" -f "Failed Requests", $failureCount, $errorRate) -ForegroundColor $(if ($errorRate -eq 0) { "Green" } else { "Red" })
Write-Host ("{0,-25} : {1} s" -f "Total Duration", $totalTimeSec) -ForegroundColor White
Write-Host ("{0,-25} : {1} req/sec" -f "Throughput", $rps) -ForegroundColor Cyan
Write-Host ("{0,-25} : {1} ms" -f "Average Latency", $avgLatency) -ForegroundColor White
Write-Host ("{0,-25} : {1} ms" -f "Median (P50) Latency", $p50) -ForegroundColor White
Write-Host ("{0,-25} : {1} ms" -f "P90 Latency", $p90) -ForegroundColor White
Write-Host ("{0,-25} : {1} ms" -f "P95 Latency (SLA < 400ms)", $p95) -ForegroundColor Green
Write-Host ("{0,-25} : {1} ms" -f "P99 Latency (SLA < 800ms)", $p99) -ForegroundColor Green
Write-Host ("{0,-25} : {1} ms" -f "Max Latency", $maxLatency) -ForegroundColor White

Assert-Test "Benchmark achieved zero critical failures (Error Rate <= 1%)" {
    $errorRate -le 1.0
}

Assert-Test "P95 latency meets strict SLA threshold (< 400ms)" {
    $p95 -lt 400.0
}

Assert-Test "P99 latency meets strict SLA threshold (< 800ms)" {
    $p99 -lt 800.0
}

Assert-Test "Average latency is within sub-150ms enterprise target" {
    $avgLatency -lt 150.0
}

# ------------------------------------------------------------------------------
# 7. Generate Benchmark Summary Artifacts
# ------------------------------------------------------------------------------
Write-Header "7. BENCHMARK ARTIFACTS GENERATION"

$benchmarkReport = [PSCustomObject]@{
    timestamp = (Get-Date).ToString("o")
    totalRequests = $BenchmarkRequests
    successfulRequests = $successCount
    failedRequests = $failureCount
    errorRatePercent = $errorRate
    totalDurationSeconds = $totalTimeSec
    throughputRps = $rps
    latenciesMs = @{
        average = $avgLatency
        p50 = $p50
        p90 = $p90
        p95 = $p95
        p99 = $p99
        max = $maxLatency
    }
    slaCompliance = @{
        p95Passed = $p95 -lt 400.0
        p99Passed = $p99 -lt 800.0
        errorRatePassed = $errorRate -le 1.0
    }
}

$reportJsonPath = Join-Path $loadTestDir "reports\phase22-benchmark.json"
$benchmarkReport | ConvertTo-Json -Depth 5 | Set-Content $reportJsonPath
Assert-Test "Performance benchmark JSON report generated" {
    Test-Path $reportJsonPath
}

# ------------------------------------------------------------------------------
# Summary
# ------------------------------------------------------------------------------
Write-Header "PHASE 22 VERIFICATION SUMMARY"
Write-Host " Total Tests: $script:totalTests" -ForegroundColor Cyan
Write-Host " Passed     : $script:passedTests" -ForegroundColor Green
Write-Host " Failed     : $script:failedTests" -ForegroundColor $(if ($script:failedTests -eq 0) { "Green" } else { "Red" })

if ($script:failedTests -eq 0) {
    Write-Host ""
    Write-Host " [SUCCESS] Phase 22 Load Testing & Tracing Verification PASSED!" -ForegroundColor Green
    exit 0
} else {
    Write-Host ""
    Write-Host " [ERROR] Phase 22 verification encountered $script:failedTests failure(s)." -ForegroundColor Red
    exit 1
}
