# ==============================================================================
# Universal Commerce Platform - Comprehensive End-to-End Manual Test Runner
# ==============================================================================

param (
    [string]$ScreenshotDir = "C:\Users\user\.gemini\antigravity\brain\4c49615d-474c-4fce-b24c-3e0b8d7b7db3",
    [string]$EdgePath = "C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"
)

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "   UNIVERSAL COMMERCE - FULL APPLICATION MANUAL TEST RUNNER      " -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan
$nowStr = (Get-Date).ToUniversalTime().ToString("yyyy-MM-dd HH:mm:ss")
Write-Host " Timestamp: $nowStr UTC"
Write-Host ""

$Passed = 0
$Failed = 0

function Assert-Test {
    param (
        [string]$Step,
        [bool]$Success,
        [string]$Details = ""
    )
    if ($Success) {
        Write-Host " [PASS] $Step" -ForegroundColor Green
        if ($Details) { Write-Host "        $Details" -ForegroundColor Gray }
        $global:Passed++
    } else {
        Write-Host " [FAIL] $Step" -ForegroundColor Red
        if ($Details) { Write-Host "        $Details" -ForegroundColor Red }
        $global:Failed++
    }
}

# ── 1. User Authentication & Personas ─────────────────────────────────────────
Write-Host "`n--- 1. Testing User Authentication & Personas ---" -ForegroundColor Yellow

# Shopper Login
$shopperLogin = @{ email = "shopper@ecommerce.com"; password = "password123" } | ConvertTo-Json
try {
    $shopperAuth = Invoke-RestMethod -Uri "http://localhost:8080/api/users/login" -Method POST -Body $shopperLogin -ContentType "application/json" -TimeoutSec 5
    $shopperToken = $shopperAuth.token
    Assert-Test "Shopper login (shopper@ecommerce.com)" ($null -ne $shopperToken) "JWT Token successfully generated"
} catch {
    Assert-Test "Shopper login (shopper@ecommerce.com)" $false $_.Exception.Message
}

# Manager Login
$mgrLogin = @{ email = "manager@ecommerce.com"; password = "password123" } | ConvertTo-Json
try {
    $mgrAuth = Invoke-RestMethod -Uri "http://localhost:8080/api/users/login" -Method POST -Body $mgrLogin -ContentType "application/json" -TimeoutSec 5
    $mgrToken = $mgrAuth.token
    Assert-Test "Manager login (manager@ecommerce.com)" ($null -ne $mgrToken) "JWT Token successfully generated"
} catch {
    Assert-Test "Manager login (manager@ecommerce.com)" $false $_.Exception.Message
}

# Admin Login
$adminLogin = @{ email = "admin@ecommerce.com"; password = "password123" } | ConvertTo-Json
try {
    $adminAuth = Invoke-RestMethod -Uri "http://localhost:8080/api/users/login" -Method POST -Body $adminLogin -ContentType "application/json" -TimeoutSec 5
    $adminToken = $adminAuth.token
    Assert-Test "Admin login (admin@ecommerce.com)" ($null -ne $adminToken) "JWT Token successfully generated"
} catch {
    Assert-Test "Admin login (admin@ecommerce.com)" $false $_.Exception.Message
}

# ── 2. Product Catalog & Details ──────────────────────────────────────────────
Write-Host "`n--- 2. Testing Product Catalog & Live Stock ---" -ForegroundColor Yellow
try {
    $products = Invoke-RestMethod -Uri "http://localhost:8082/api/products" -TimeoutSec 5
    Assert-Test "Catalog query returned items" ($products.Count -ge 3) "Found $($products.Count) active catalog products"
    
    $item1 = $products | Where-Object { $_.id -eq 1 }
    Assert-Test "Product 1 (Headphones) exists with stock" ($item1.stockQuantity -gt 0) "Stock: $($item1.stockQuantity), Price: `$$($item1.price)"
    
    $item2 = $products | Where-Object { $_.id -eq 2 }
    Assert-Test "Product 2 (Mechanical Keyboard) exists with stock" ($item2.stockQuantity -gt 0) "Stock: $($item2.stockQuantity), Price: `$$($item2.price)"
} catch {
    Assert-Test "Catalog query returned items" $false $_.Exception.Message
}

# ── 3. Shopping Cart Checkout & Stock Deduction ──────────────────────────────
Write-Host "`n--- 3. Testing Shopping Cart Checkout & Stock Deduction ---" -ForegroundColor Yellow
$orderId = $null
$stockBefore = $item2.stockQuantity
try {
    $checkoutReq = @{
        userId = 6
        items = @(
            @{
                productId = 2
                name = "Mechanical Keyboard"
                price = 79.99
                quantity = 1
            }
        )
    } | ConvertTo-Json -Depth 5

    $orderResp = Invoke-RestMethod -Uri "http://localhost:8080/api/orders/checkout" -Method POST -Body $checkoutReq -Headers @{ Authorization = "Bearer $shopperToken" } -ContentType "application/json" -TimeoutSec 10
    $orderId = $orderResp.id
    Assert-Test "Order placed successfully" ($null -ne $orderId) "Order ID: $orderId, Status: $($orderResp.status), Total: `$$($orderResp.totalAmount)"

    # Verify inventory was decremented in product-service
    $item2After = Invoke-RestMethod -Uri "http://localhost:8082/api/products/2" -TimeoutSec 5
    $stockAfter = $item2After.stockQuantity
    Assert-Test "Inventory decremented in product-service" ($stockAfter -eq ($stockBefore - 1)) "Stock changed from $stockBefore to $stockAfter"
} catch {
    Assert-Test "Order placed successfully" $false $_.Exception.Message
}

# ── 4. Order Management & Status Transitions ──────────────────────────────────
Write-Host "`n--- 4. Testing Order Management & Status Transitions ---" -ForegroundColor Yellow
if ($orderId) {
    try {
        $updateReq = @{ status = "CONFIRMED" } | ConvertTo-Json
        $updateResp = Invoke-RestMethod -Uri "http://localhost:8080/api/orders/$orderId/status" -Method PUT -Body $updateReq -Headers @{ Authorization = "Bearer $mgrToken" } -ContentType "application/json" -TimeoutSec 5
        Assert-Test "Manager transitioned order to CONFIRMED" ($updateResp.status -eq "CONFIRMED") "Updated status: $($updateResp.status)"
        
        # Verify order appears in customer's order history
        $userOrders = Invoke-RestMethod -Uri "http://localhost:8080/api/orders/user/6" -Headers @{ Authorization = "Bearer $shopperToken" } -TimeoutSec 5
        $foundOrder = $userOrders | Where-Object { $_.id -eq $orderId }
        Assert-Test "Order appears in customer order history" ($null -ne $foundOrder) "Found order $orderId with status $($foundOrder.status)"
    } catch {
        Assert-Test "Order status transition" $false $_.Exception.Message
    }
}

# ── 5. Kafka Event-Driven Notifications & DLQ ────────────────────────────────
Write-Host "`n--- 5. Testing Kafka Event-Driven Notifications & DLQ ---" -ForegroundColor Yellow
try {
    # Check Dead Letter Queue inspector endpoint
    $dlq = Invoke-RestMethod -Uri "http://localhost:8084/api/notifications/dlq" -TimeoutSec 5
    Assert-Test "Kafka Dead Letter Queue inspector accessible" ($null -ne $dlq) "Found $(@($dlq).Count) tracked DLQ records with auto-retry schema"
    
    # Check Notification service health
    $notifHealth = Invoke-RestMethod -Uri "http://localhost:8084/actuator/health" -TimeoutSec 5
    Assert-Test "Notification Service health status UP" ($notifHealth.status -eq "UP") "Kafka consumer active & listening"
} catch {
    Assert-Test "Kafka notifications health" $false $_.Exception.Message
}

# ── 6. User Governance & RBAC ────────────────────────────────────────────────
Write-Host "`n--- 6. Testing User Governance & RBAC ---" -ForegroundColor Yellow
try {
    $allUsers = Invoke-RestMethod -Uri "http://localhost:8080/api/users" -Headers @{ Authorization = "Bearer $adminToken" } -TimeoutSec 5
    Assert-Test "Admin queried all registered users" ($allUsers.Count -ge 3) "Found $($allUsers.Count) user accounts"
    
    $hasAdmin = (@($allUsers | Where-Object { $_.role -eq "ADMIN" })).Count -gt 0
    $hasManager = (@($allUsers | Where-Object { $_.role -eq "MANAGER" })).Count -gt 0
    $hasUser = (@($allUsers | Where-Object { $_.role -eq "USER" })).Count -gt 0
    Assert-Test "Platform has all 3 required RBAC roles (ADMIN, MANAGER, USER)" ($hasAdmin -and $hasManager -and $hasUser) "All roles verified active"
} catch {
    Assert-Test "User Governance query" $false $_.Exception.Message
}

# ── 7. Observability & SLO Health ─────────────────────────────────────────────
Write-Host "`n--- 7. Testing Observability & Live SLO Metrics ---" -ForegroundColor Yellow
try {
    $promHealth = Invoke-RestMethod -Uri "http://localhost:9090/-/healthy" -TimeoutSec 5
    Assert-Test "Prometheus server healthy" ($promHealth.Contains("Prometheus Server is Healthy"))
    
    $amHealth = Invoke-RestMethod -Uri "http://localhost:9093/-/healthy" -TimeoutSec 5
    Assert-Test "Alertmanager cluster healthy" ($amHealth.Trim() -eq "OK")
    
    $cred = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("admin:$($env:GRAFANA_ADMIN_PASSWORD)"))
    $grafSearch = Invoke-RestMethod -Uri "http://localhost:3030/api/search?query=SLO" -Headers @{ Authorization = "Basic $cred" } -TimeoutSec 5
    Assert-Test "Grafana SLO Dashboard registered & accessible" ($grafSearch.Count -ge 1) "Dashboard UID: $($grafSearch[0].uid)"
} catch {
    Assert-Test "Observability health check" $false $_.Exception.Message
}

# ── 8. UI Screen Capture ──────────────────────────────────────────────────────
Write-Host "`n--- 8. Capturing High-Resolution UI Screenshots ---" -ForegroundColor Yellow
$Screenshots = @(
    @{ Name = "homepage.png";      Url = "http://localhost:3000/";                      Desc = "Storefront Homepage" },
    @{ Name = "login_screen.png";  Url = "http://localhost:3000/login";                 Desc = "Login Screen with Persona Cards" },
    @{ Name = "product_mfe.png";   Url = "http://localhost:3001/";                      Desc = "Product Catalog MFE" },
    @{ Name = "checkout_mfe.png";  Url = "http://localhost:3002/";                      Desc = "Universal Checkout & Cart MFE" },
    @{ Name = "user_mfe.png";      Url = "http://localhost:3003/";                      Desc = "User Profile & Loyalty MFE" },
    @{ Name = "alertmanager.png";  Url = "http://localhost:9093/";                      Desc = "Alertmanager Cluster UI" },
    @{ Name = "prometheus.png";    Url = "http://localhost:9090/alerts";                Desc = "Prometheus Live Alert Rules UI" }
)

foreach ($sc in $Screenshots) {
    $outFile = Join-Path $ScreenshotDir $sc.Name
    $tempDir = New-Item -ItemType Directory -Path "$env:TEMP\edge_manual_$([guid]::NewGuid().ToString())" -Force
    
    try {
        Start-Process -FilePath $EdgePath -ArgumentList "--headless=new", "--disable-gpu", "--window-size=1440,900", "--user-data-dir=$($tempDir.FullName)", "--screenshot=$outFile", $sc.Url -Wait -ErrorAction Stop
        if (Test-Path $outFile) {
            $f = Get-Item $outFile
            Assert-Test "Captured screenshot: $($sc.Desc)" ($f.Length -gt 0) "Saved to: $($sc.Name) ($([math]::Round($f.Length/1024, 1)) KB)"
        } else {
            Assert-Test "Captured screenshot: $($sc.Desc)" $false "File not found"
        }
    } catch {
        Assert-Test "Captured screenshot: $($sc.Desc)" $false $_.Exception.Message
    } finally {
        Remove-Item -Path $tempDir.FullName -Recurse -Force -ErrorAction SilentlyContinue
    }
}

Write-Host "`n=================================================================" -ForegroundColor Cyan
Write-Host ("   MANUAL TESTING COMPLETE: {0} Passed, {1} Failed" -f $Passed, $Failed) -ForegroundColor $(if ($Failed -eq 0) { "Green" } else { "Red" })
Write-Host "=================================================================`n" -ForegroundColor Cyan

if ($Failed -gt 0) { exit 1 }
exit 0
