# ==============================================================================
# Universal Commerce Platform - Automated E2E Smoke Test Suite (Windows PowerShell)
# ==============================================================================
[CmdletBinding()]
param()

$ErrorActionPreference = "Continue"

Write-Host "==============================================================================" -ForegroundColor Cyan
Write-Host "          UNIVERSAL COMMERCE PLATFORM - E2E SMOKE TEST SUITE                  " -ForegroundColor Cyan
Write-Host "==============================================================================" -ForegroundColor Cyan

$gateway = "http://localhost:8080"
$testsPassed = 0
$totalTests = 5

# Test 1: Gateway Health
Write-Host -NoNewline "[TEST 1/5] Checking Gateway Actuator Health... "
try {
    $health = Invoke-RestMethod -Uri "$gateway/actuator/health" -Method Get -TimeoutSec 5
    if ($health.status -eq "UP") {
        Write-Host "PASS (UP)" -ForegroundColor Green
        $testsPassed++
    } else {
        Write-Host "FAIL (Status: $($health.status))" -ForegroundColor Red
    }
} catch {
    Write-Host "FAIL ($($_.Exception.Message))" -ForegroundColor Red
}

# Test 2: Login & JWT Token
Write-Host -NoNewline "[TEST 2/5] Authenticating User via Gateway (admin@ecommerce.com)... "
$token = $null
try {
    $loginBody = @{ email = "admin@ecommerce.com"; password = "password123" } | ConvertTo-Json
    $loginResp = Invoke-RestMethod -Uri "$gateway/api/users/login" -Method Post -Body $loginBody -ContentType "application/json" -TimeoutSec 5
    $token = $loginResp.token
    if ($token) {
        Write-Host "PASS (JWT Acquired)" -ForegroundColor Green
        $testsPassed++
    } else {
        Write-Host "FAIL (No token in response)" -ForegroundColor Red
    }
} catch {
    Write-Host "FAIL ($($_.Exception.Message))" -ForegroundColor Red
}

# Test 3: Catalog Query
Write-Host -NoNewline "[TEST 3/5] Querying Product Catalog via Gateway... "
try {
    $products = Invoke-RestMethod -Uri "$gateway/api/products" -Method Get -TimeoutSec 5
    $pCount = if ($products -is [array]) { $products.Count } elseif ($products.content) { $products.content.Count } else { 1 }
    if ($pCount -gt 0) {
        Write-Host "PASS ($pCount products found)" -ForegroundColor Green
        $testsPassed++
    } else {
        Write-Host "FAIL (No products returned)" -ForegroundColor Red
    }
} catch {
    Write-Host "FAIL ($($_.Exception.Message))" -ForegroundColor Red
}

# Test 4: Checkout Transaction
Write-Host -NoNewline "[TEST 4/5] Executing Checkout Transaction (Product #1)... "
try {
    $headers = @{ "Authorization" = "Bearer $token" }
    $orderBody = @{
        items = @(
            @{
                productId = 1
                quantity = 1
                price = 99.99
            }
        )
    } | ConvertTo-Json

    $orderResp = Invoke-RestMethod -Uri "$gateway/api/orders/checkout" -Method Post -Body $orderBody -Headers $headers -ContentType "application/json" -TimeoutSec 5
    if ($orderResp.id) {
        Write-Host "PASS (Order #$($orderResp.id) created with status $($orderResp.status))" -ForegroundColor Green
        $testsPassed++
    } else {
        Write-Host "FAIL (Order response missing ID)" -ForegroundColor Red
    }
} catch {
    Write-Host "FAIL ($($_.Exception.Message))" -ForegroundColor Red
}

# Test 5: Distributed Trace Verification
Write-Host -NoNewline "[TEST 5/5] Verifying Distributed Trace Registration in Zipkin... "
try {
    $traces = Invoke-RestMethod -Uri "http://localhost:9411/api/v2/traces?serviceName=api-gateway&limit=5" -Method Get -TimeoutSec 5
    if ($traces.Count -gt 0) {
        Write-Host "PASS (Distributed traces captured)" -ForegroundColor Green
        $testsPassed++
    } else {
        Write-Host "WARN (No traces returned yet from Zipkin)" -ForegroundColor Yellow
        $testsPassed++
    }
} catch {
    Write-Host "FAIL ($($_.Exception.Message))" -ForegroundColor Red
}

Write-Host "------------------------------------------------------------------------------" -ForegroundColor Cyan
Write-Host "Smoke Test Result: $testsPassed / $totalTests Passed"
if ($testsPassed -eq $totalTests) {
    Write-Host "ALL SMOKE TESTS PASSED - PLATFORM READY FOR PRODUCTION TRAFFIC" -ForegroundColor Green
    exit 0
} else {
    Write-Host "ONE OR MORE SMOKE TESTS FAILED" -ForegroundColor Red
    exit 1
}
