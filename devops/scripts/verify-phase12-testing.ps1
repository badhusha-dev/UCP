# ==============================================================================
# Phase 12: Enterprise Contract & Integration Testing Verification Script
# ==============================================================================

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "   Starting Phase 12: Enterprise Integration & Contract Testing  " -ForegroundColor Cyan
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
$ProjectRoot = (Resolve-Path (Join-Path $ScriptDir "../..")).Path
$BackendDir = Join-Path $ProjectRoot "backend"

# 1. Parent POM Testing Dependencies & Versions
Write-Host "`n--- 1. Testing Parent POM Dependencies ---" -ForegroundColor Yellow
$parentPomPath = Join-Path $BackendDir "pom.xml"
$parentPom = Get-Content $parentPomPath -Raw

Assert-Condition "Parent POM defines spring-boot-starter-test" ($parentPom -match "spring-boot-starter-test")
Assert-Condition "Parent POM defines testcontainers-bom" ($parentPom -match "testcontainers-bom")
Assert-Condition "Parent POM defines wiremock-standalone" ($parentPom -match "wiremock-standalone")
Assert-Condition "Parent POM defines testcontainers.version 1.19.7" ($parentPom -match "<testcontainers\.version>1\.19\.7")
Assert-Condition "Parent POM defines wiremock.version 3.5.2" ($parentPom -match "<wiremock\.version>3\.5\.2")

# 2. Service POM Test Dependencies
Write-Host "`n--- 2. Testing Microservice POM Test Dependencies ---" -ForegroundColor Yellow
$userPom = Get-Content (Join-Path $BackendDir "user-service/pom.xml") -Raw
Assert-Condition "user-service pom has h2 dependency" ($userPom -match "h2")
Assert-Condition "user-service pom has spring-security-test" ($userPom -match "spring-security-test")

$productPom = Get-Content (Join-Path $BackendDir "product-service/pom.xml") -Raw
Assert-Condition "product-service pom has h2 dependency" ($productPom -match "h2")
Assert-Condition "product-service pom has wiremock-standalone" ($productPom -match "wiremock-standalone")

$orderPom = Get-Content (Join-Path $BackendDir "order-service/pom.xml") -Raw
Assert-Condition "order-service pom has h2 dependency" ($orderPom -match "h2")
Assert-Condition "order-service pom has wiremock-standalone" ($orderPom -match "wiremock-standalone")
Assert-Condition "order-service pom has spring-kafka-test" ($orderPom -match "spring-kafka-test")

$notifPom = Get-Content (Join-Path $BackendDir "notification-service/pom.xml") -Raw
Assert-Condition "notification-service pom has h2 dependency" ($notifPom -match "h2")
Assert-Condition "notification-service pom has spring-kafka-test" ($notifPom -match "spring-kafka-test")

$gwPom = Get-Content (Join-Path $BackendDir "api-gateway/pom.xml") -Raw
Assert-Condition "api-gateway pom has reactor-test" ($gwPom -match "reactor-test")

# 3. Test Suites Existence Across Microservices
Write-Host "`n--- 3. Testing Integration Test Suites Existence ---" -ForegroundColor Yellow
$RequiredTestFiles = @(
    "user-service/src/test/java/com/ecommerce/user/UserRepositoryTest.java",
    "user-service/src/test/java/com/ecommerce/user/AuthAndUserIntegrationTest.java",
    "product-service/src/test/java/com/ecommerce/product/ProductRepositoryTest.java",
    "product-service/src/test/java/com/ecommerce/product/ProductControllerIntegrationTest.java",
    "product-service/src/test/java/com/ecommerce/product/ProductResilienceWireMockTest.java",
    "order-service/src/test/java/com/ecommerce/order/OrderRepositoryTest.java",
    "order-service/src/test/java/com/ecommerce/order/OrderCheckoutIntegrationTest.java",
    "order-service/src/test/java/com/ecommerce/order/OrderResilienceWireMockTest.java",
    "notification-service/src/test/java/com/ecommerce/notification/NotificationRepositoryTest.java",
    "notification-service/src/test/java/com/ecommerce/notification/NotificationConsumerIdempotencyTest.java",
    "notification-service/src/test/java/com/ecommerce/notification/DeadLetterQueueIntegrationTest.java",
    "api-gateway/src/test/java/com/ecommerce/gateway/GatewaySecurityAndRoutingTest.java",
    "discovery-server/src/test/java/com/ecommerce/discovery/DiscoveryServerApplicationTest.java",
    "config-server/src/test/java/com/ecommerce/config/ConfigServerApplicationTest.java"
)

foreach ($testFile in $RequiredTestFiles) {
    $fullPath = Join-Path $BackendDir $testFile
    Assert-Condition "Test file exists: $(Split-Path -Leaf $testFile)" (Test-Path $fullPath)
}

# 4. Test Configuration Profiles Existence (bootstrap.yml & application.yml)
Write-Host "`n--- 4. Testing Test Isolation Configuration ---" -ForegroundColor Yellow
$RequiredConfigs = @(
    "user-service/src/test/resources/bootstrap.yml",
    "user-service/src/test/resources/application.yml",
    "product-service/src/test/resources/bootstrap.yml",
    "product-service/src/test/resources/application.yml",
    "order-service/src/test/resources/bootstrap.yml",
    "order-service/src/test/resources/application.yml",
    "notification-service/src/test/resources/bootstrap.yml",
    "notification-service/src/test/resources/application.yml",
    "api-gateway/src/test/resources/bootstrap.yml",
    "api-gateway/src/test/resources/application.yml",
    "discovery-server/src/test/resources/bootstrap.yml",
    "discovery-server/src/test/resources/application.yml",
    "config-server/src/test/resources/application.yml"
)

foreach ($configFile in $RequiredConfigs) {
    $fullPath = Join-Path $BackendDir $configFile
    Assert-Condition "Config exists: $configFile" (Test-Path $fullPath)
}

# 5. Semantic Resilience & Contract Assertions
Write-Host "`n--- 5. Testing Contract & Resilience Test Logic ---" -ForegroundColor Yellow

$orderResilience = Get-Content (Join-Path $BackendDir "order-service/src/test/java/com/ecommerce/order/OrderResilienceWireMockTest.java") -Raw
Assert-Condition "order-service tests healthy inventory response" ($orderResilience -match "testOrderPlacementHealthyProductService")
Assert-Condition "order-service tests degraded fallback status (PENDING_INVENTORY)" ($orderResilience -match "PENDING_INVENTORY")

$productResilience = Get-Content (Join-Path $BackendDir "product-service/src/test/java/com/ecommerce/product/ProductResilienceWireMockTest.java") -Raw
Assert-Condition "product-service tests healthy order query" ($productResilience -match "testGetSystemOrdersSuccess")
Assert-Condition "product-service tests CircuitBreaker fallback response" ($productResilience -match "testGetSystemOrdersCircuitBreakerFallback")

$notifIdempotency = Get-Content (Join-Path $BackendDir "notification-service/src/test/java/com/ecommerce/notification/NotificationConsumerIdempotencyTest.java") -Raw
Assert-Condition "notification-service tests new event consumption" ($notifIdempotency -match "testConsumeNewOrderEvent")
Assert-Condition "notification-service tests duplicate event deduplication" ($notifIdempotency -match "testConsumeDuplicateOrderEvent")

$gatewaySecurity = Get-Content (Join-Path $BackendDir "api-gateway/src/test/java/com/ecommerce/gateway/GatewaySecurityAndRoutingTest.java") -Raw
Assert-Condition "api-gateway tests 401 Unauthorized on missing header" ($gatewaySecurity -match "testMissingAuthHeaderReturns401")
Assert-Condition "api-gateway tests 401 Unauthorized on invalid token" ($gatewaySecurity -match "testInvalidTokenReturns401")
Assert-Condition "api-gateway tests 503 on fallback routes" ($gatewaySecurity -match "SERVICE_UNAVAILABLE")

# 6. Surefire Reports Verification
Write-Host "`n--- 6. Testing Surefire Reports Status ---" -ForegroundColor Yellow
$ServicesWithTests = @("user-service", "product-service", "order-service", "notification-service", "api-gateway")
foreach ($svc in $ServicesWithTests) {
    $reportDir = Join-Path $BackendDir "$svc/target/surefire-reports"
    if (Test-Path $reportDir) {
        $xmlReports = Get-ChildItem -Path $reportDir -Filter "TEST-*.xml"
        $hasReports = ($xmlReports.Count -gt 0)
        Assert-Condition "$svc generated $($xmlReports.Count) Surefire test report(s)" $hasReports
    } else {
        Assert-Condition "$svc target/surefire-reports directory found" $false
    }
}

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "   Testing Validation Summary: Passed: $Passed, Failed: $Failed   " -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan

if ($Failed -gt 0) {
    exit 1
} else {
    exit 0
}
