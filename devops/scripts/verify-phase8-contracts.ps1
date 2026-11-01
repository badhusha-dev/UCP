# ==============================================================================
# Phase 8: OpenAPI 3 & Swagger UI Contract Verification Script
# ==============================================================================

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "   Starting Phase 8: OpenAPI 3 & Contract Validation Tests       " -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan

$GatewayBase = "http://localhost:8080"
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

# 1. Check Swagger UI on API Gateway
Write-Host "`n--- 1. Testing API Gateway Swagger UI Entry Point ---" -ForegroundColor Yellow
try {
    $uiResponse = Invoke-WebRequest -Uri "$GatewayBase/swagger-ui.html" -MaximumRedirection 5 -UseBasicParsing -TimeoutSec 10
    Assert-Condition "Gateway Swagger UI page loads (HTTP $($uiResponse.StatusCode))" ($uiResponse.StatusCode -eq 200)
    Assert-Condition "Gateway Swagger UI contains SwaggerUIBundle HTML" ($uiResponse.Content.Contains("swagger-ui") -or $uiResponse.Content.Contains("Swagger UI"))
} catch {
    Assert-Condition "Gateway Swagger UI reachable ($($_.Exception.Message))" $false
}

# 2. Check Swagger Config on API Gateway (Aggregated URLs)
Write-Host "`n--- 2. Testing Gateway Aggregated OpenAPI URLs Config ---" -ForegroundColor Yellow
try {
    $configResponse = Invoke-RestMethod -Uri "$GatewayBase/v3/api-docs/swagger-config" -TimeoutSec 10
    $urls = $configResponse.urls
    Assert-Condition "Swagger config contains multiple service URLs" ($urls.Count -ge 5)
    
    $hasUser = ($urls | Where-Object { $_.url -like "*/user-service/v3/api-docs*" }) -ne $null
    $hasProduct = ($urls | Where-Object { $_.url -like "*/product-service/v3/api-docs*" }) -ne $null
    $hasOrder = ($urls | Where-Object { $_.url -like "*/order-service/v3/api-docs*" }) -ne $null
    $hasNotification = ($urls | Where-Object { $_.url -like "*/notification-service/v3/api-docs*" }) -ne $null
    $hasGateway = ($urls | Where-Object { $_.url -eq "/v3/api-docs" }) -ne $null

    Assert-Condition "Swagger Config contains User Service url (/user-service/v3/api-docs)" $hasUser
    Assert-Condition "Swagger Config contains Product Service url (/product-service/v3/api-docs)" $hasProduct
    Assert-Condition "Swagger Config contains Order Service url (/order-service/v3/api-docs)" $hasOrder
    Assert-Condition "Swagger Config contains Notification Service url (/notification-service/v3/api-docs)" $hasNotification
    Assert-Condition "Swagger Config contains Gateway url (/v3/api-docs)" $hasGateway
} catch {
    Assert-Condition "Swagger Config endpoint reachable ($($_.Exception.Message))" $false
}

# 3. Contract Testing: User Service OpenAPI Contract (via Gateway Route)
Write-Host "`n--- 3. Testing User Service OpenAPI Contract ---" -ForegroundColor Yellow
try {
    $userDocs = Invoke-RestMethod -Uri "$GatewayBase/user-service/v3/api-docs" -TimeoutSec 10
    Assert-Condition "User Service OpenAPI 3.x spec loaded" ($userDocs.openapi -like "3.*")
    Assert-Condition "User Service Title is 'User Service API'" ($userDocs.info.title -eq "User Service API")
    Assert-Condition "User Service defines /api/users/login path" ($userDocs.paths."/api/users/login" -ne $null)
    Assert-Condition "User Service defines /api/users path" ($userDocs.paths."/api/users" -ne $null)
    Assert-Condition "User Service defines /api/users/{id} path" ($userDocs.paths."/api/users/{id}" -ne $null)
    Assert-Condition "User Service defines bearerAuth JWT security scheme" ($userDocs.components.securitySchemes.bearerAuth -ne $null)
    Assert-Condition "User Service User schema exists with required fields" ($userDocs.components.schemas.User.properties.email -ne $null -and $userDocs.components.schemas.User.properties.role -ne $null)
} catch {
    Assert-Condition "User Service OpenAPI contract valid ($($_.Exception.Message))" $false
}

# 4. Contract Testing: Product Service OpenAPI Contract (via Gateway Route)
Write-Host "`n--- 4. Testing Product Service OpenAPI Contract ---" -ForegroundColor Yellow
try {
    $productDocs = Invoke-RestMethod -Uri "$GatewayBase/product-service/v3/api-docs" -TimeoutSec 10
    Assert-Condition "Product Service OpenAPI 3.x spec loaded" ($productDocs.openapi -like "3.*")
    Assert-Condition "Product Service Title is 'Product Service API'" ($productDocs.info.title -eq "Product Service API")
    Assert-Condition "Product Service defines /api/products path" ($productDocs.paths."/api/products" -ne $null)
    Assert-Condition "Product Service defines /api/products/{id} path" ($productDocs.paths."/api/products/{id}" -ne $null)
    Assert-Condition "Product Service defines /api/products/{id}/inventory path" ($productDocs.paths."/api/products/{id}/inventory" -ne $null)
    Assert-Condition "Product Service defines /api/products/system-orders path" ($productDocs.paths."/api/products/system-orders" -ne $null)
    Assert-Condition "Product Service defines bearerAuth JWT security scheme" ($productDocs.components.securitySchemes.bearerAuth -ne $null)
    Assert-Condition "Product Service Product schema contains stockQuantity" ($productDocs.components.schemas.Product.properties.stockQuantity -ne $null)
} catch {
    Assert-Condition "Product Service OpenAPI contract valid ($($_.Exception.Message))" $false
}

# 5. Contract Testing: Order Service OpenAPI Contract (via Gateway Route)
Write-Host "`n--- 5. Testing Order Service OpenAPI Contract ---" -ForegroundColor Yellow
try {
    $orderDocs = Invoke-RestMethod -Uri "$GatewayBase/order-service/v3/api-docs" -TimeoutSec 10
    Assert-Condition "Order Service OpenAPI 3.x spec loaded" ($orderDocs.openapi -like "3.*")
    Assert-Condition "Order Service Title is 'Order Service API'" ($orderDocs.info.title -eq "Order Service API")
    Assert-Condition "Order Service defines /api/orders/checkout path" ($orderDocs.paths."/api/orders/checkout" -ne $null)
    Assert-Condition "Order Service defines /api/orders/user/{userId} path" ($orderDocs.paths."/api/orders/user/{userId}" -ne $null)
    Assert-Condition "Order Service defines /api/orders/{id}/cancel path" ($orderDocs.paths."/api/orders/{id}/cancel" -ne $null)
    Assert-Condition "Order Service defines bearerAuth JWT security scheme" ($orderDocs.components.securitySchemes.bearerAuth -ne $null)
    Assert-Condition "Order Service Order schema contains items array" ($orderDocs.components.schemas.Order.properties.items -ne $null)
    Assert-Condition "Order Service CheckoutRequest schema contains items" ($orderDocs.components.schemas.CheckoutRequest.properties.items -ne $null)
} catch {
    Assert-Condition "Order Service OpenAPI contract valid ($($_.Exception.Message))" $false
}

# 6. Contract Testing: Notification Service OpenAPI Contract (via Gateway Route)
Write-Host "`n--- 6. Testing Notification Service OpenAPI Contract ---" -ForegroundColor Yellow
try {
    $notifDocs = Invoke-RestMethod -Uri "$GatewayBase/notification-service/v3/api-docs" -TimeoutSec 10
    Assert-Condition "Notification Service OpenAPI 3.x spec loaded" ($notifDocs.openapi -like "3.*")
    Assert-Condition "Notification Service Title is 'Notification Service API'" ($notifDocs.info.title -eq "Notification Service API")
    Assert-Condition "Notification Service defines /api/notifications/user/{userId} path" ($notifDocs.paths."/api/notifications/user/{userId}" -ne $null)
    Assert-Condition "Notification Service defines /api/notifications/dlq path" ($notifDocs.paths."/api/notifications/dlq" -ne $null)
    Assert-Condition "Notification Service defines /api/notifications/dlq/{id}/replay path" ($notifDocs.paths."/api/notifications/dlq/{id}/replay" -ne $null)
    Assert-Condition "Notification Service defines bearerAuth JWT security scheme" ($notifDocs.components.securitySchemes.bearerAuth -ne $null)
    Assert-Condition "Notification Service Notification schema contains idempotencyKey" ($notifDocs.components.schemas.Notification.properties.idempotencyKey -ne $null)
    Assert-Condition "Notification Service DeadLetterEvent schema contains status" ($notifDocs.components.schemas.DeadLetterEvent.properties.status -ne $null)
} catch {
    Assert-Condition "Notification Service OpenAPI contract valid ($($_.Exception.Message))" $false
}

# 7. Gateway OpenAPI Contract (Edge fallbacks)
Write-Host "`n--- 7. Testing Gateway Edge Contract ---" -ForegroundColor Yellow
try {
    $gwDocs = Invoke-RestMethod -Uri "$GatewayBase/v3/api-docs" -TimeoutSec 10
    Assert-Condition "Gateway OpenAPI 3.x spec loaded" ($gwDocs.openapi -like "3.*")
    Assert-Condition "Gateway defines /fallback/user path" ($gwDocs.paths."/fallback/user" -ne $null)
    Assert-Condition "Gateway defines /fallback/order path" ($gwDocs.paths."/fallback/order" -ne $null)
} catch {
    Assert-Condition "Gateway OpenAPI contract valid ($($_.Exception.Message))" $false
}

# 8. Direct Microservice Port Check
Write-Host "`n--- 8. Testing Direct Microservice OpenAPI Endpoints ---" -ForegroundColor Yellow
$services = @(
    @{ Name = "user-service"; Port = 8081 },
    @{ Name = "product-service"; Port = 8082 },
    @{ Name = "order-service"; Port = 8083 },
    @{ Name = "notification-service"; Port = 8084 }
)

foreach ($svc in $services) {
    try {
        $svcDocs = Invoke-RestMethod -Uri "http://localhost:$($svc.Port)/v3/api-docs" -TimeoutSec 5
        Assert-Condition "$($svc.Name) direct port :$($svc.Port)/v3/api-docs responds with 200 OK" ($svcDocs.openapi -like "3.*")
    } catch {
        Assert-Condition "$($svc.Name) direct port :$($svc.Port)/v3/api-docs responds" $false
    }
}

Write-Host "`n=================================================================" -ForegroundColor Cyan
Write-Host "   Contract Validation Summary: Passed: $Passed, Failed: $Failed " -ForegroundColor $(if ($Failed -eq 0) { "Green" } else { "Red" })
Write-Host "=================================================================" -ForegroundColor Cyan

if ($Failed -gt 0) {
    exit 1
} else {
    exit 0
}
