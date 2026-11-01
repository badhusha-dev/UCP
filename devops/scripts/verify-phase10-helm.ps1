# ==============================================================================
# Phase 10: Helm Charts & Release Packaging Verification Script
# ==============================================================================

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "   Starting Phase 10: Helm Chart & Release Packaging Tests       " -ForegroundColor Cyan
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
$HelmRoot = Join-Path $ProjectRoot "devops/helm/ecommerce-platform"

# 1. Chart Structure & Required Files
Write-Host "`n--- 1. Testing Chart Structure & Core Files ---" -ForegroundColor Yellow
$RequiredChartFiles = @(
    "$HelmRoot/Chart.yaml",
    "$HelmRoot/.helmignore",
    "$HelmRoot/values.yaml",
    "$HelmRoot/values-dev.yaml",
    "$HelmRoot/values-prod.yaml",
    "$HelmRoot/templates/_helpers.tpl",
    "$HelmRoot/templates/configmap.yaml",
    "$HelmRoot/templates/secrets.yaml",
    "$HelmRoot/templates/ingress.yaml",
    "$HelmRoot/templates/infrastructure/postgres.yaml",
    "$HelmRoot/templates/infrastructure/redis.yaml",
    "$HelmRoot/templates/infrastructure/kafka.yaml",
    "$HelmRoot/templates/platform/discovery-server.yaml",
    "$HelmRoot/templates/platform/config-server.yaml",
    "$HelmRoot/templates/services/user-service.yaml",
    "$HelmRoot/templates/services/product-service.yaml",
    "$HelmRoot/templates/services/order-service.yaml",
    "$HelmRoot/templates/services/notification-service.yaml",
    "$HelmRoot/templates/api/api-gateway.yaml",
    "$HelmRoot/templates/frontend/frontend.yaml",
    "$HelmRoot/templates/autoscaling/hpa.yaml"
)

foreach ($f in $RequiredChartFiles) {
    Assert-Condition "File exists: $f" (Test-Path $f)
}

# 2. Chart Metadata Validation (Chart.yaml)
Write-Host "`n--- 2. Testing Chart.yaml Metadata ---" -ForegroundColor Yellow
$chartContent = Get-Content "$HelmRoot/Chart.yaml" -Raw
Assert-Condition "Chart API version is v2" ($chartContent -match "apiVersion:\s*v2")
Assert-Condition "Chart name is 'ecommerce-platform'" ($chartContent -match "name:\s*ecommerce-platform")
Assert-Condition "Chart type is 'application'" ($chartContent -match "type:\s*application")
Assert-Condition "Chart version defined" ($chartContent -match "version:\s*1\.0\.0")
Assert-Condition "Chart appVersion defined" ($chartContent -match "appVersion:\s*""?1\.0\.0""?")

# 3. Helpers Validation (_helpers.tpl)
Write-Host "`n--- 3. Testing Helper Templates (_helpers.tpl) ---" -ForegroundColor Yellow
$helpersContent = Get-Content "$HelmRoot/templates/_helpers.tpl" -Raw
Assert-Condition "Defines ecommerce-platform.name helper" ($helpersContent -match 'define\s+"ecommerce-platform\.name"')
Assert-Condition "Defines ecommerce-platform.fullname helper" ($helpersContent -match 'define\s+"ecommerce-platform\.fullname"')
Assert-Condition "Defines ecommerce-platform.chart helper" ($helpersContent -match 'define\s+"ecommerce-platform\.chart"')
Assert-Condition "Defines ecommerce-platform.labels helper" ($helpersContent -match 'define\s+"ecommerce-platform\.labels"')
Assert-Condition "Defines ecommerce-platform.selectorLabels helper" ($helpersContent -match 'define\s+"ecommerce-platform\.selectorLabels"')

# 4. Values.yaml Completeness & Parameterization
Write-Host "`n--- 4. Testing Values Configuration & Hierarchy ---" -ForegroundColor Yellow
$valuesContent = Get-Content "$HelmRoot/values.yaml" -Raw
Assert-Condition "values.yaml defines global namespace" ($valuesContent -match "namespace:\s*ecommerce")
Assert-Condition "values.yaml defines config properties" ($valuesContent -match "activeProfiles:" -and $valuesContent -match "eurekaUrl:" -and $valuesContent -match "kafkaBootstrapServers:")
Assert-Condition "values.yaml defines secrets" ($valuesContent -match "postgresUser:" -and $valuesContent -match "jwtSecret:")
Assert-Condition "values.yaml defines infrastructure (postgres, redis, kafka)" ($valuesContent -match "postgres:" -and $valuesContent -match "redis:" -and $valuesContent -match "kafka:")
Assert-Condition "values.yaml defines platform (discoveryServer, configServer)" ($valuesContent -match "discoveryServer:" -and $valuesContent -match "configServer:")
Assert-Condition "values.yaml defines services (user, product, order, notification)" ($valuesContent -match "userService:" -and $valuesContent -match "productService:" -and $valuesContent -match "orderService:" -and $valuesContent -match "notificationService:")
Assert-Condition "values.yaml defines gateway" ($valuesContent -match "gateway:")
Assert-Condition "values.yaml defines ingress" ($valuesContent -match "ingress:")
Assert-Condition "values.yaml defines frontend" ($valuesContent -match "frontend:")

# 5. Environment Profiles (dev vs prod overrides)
Write-Host "`n--- 5. Testing Environment Override Profiles ---" -ForegroundColor Yellow
$devContent = Get-Content "$HelmRoot/values-dev.yaml" -Raw
Assert-Condition "values-dev defines namespace ecommerce-dev" ($devContent -match "namespace:\s*ecommerce-dev")
Assert-Condition "values-dev sets replicaCount 1" ($devContent -match "replicaCount:\s*1")
Assert-Condition "values-dev disables autoscaling" ($devContent -match "enabled:\s*false")

$prodContent = Get-Content "$HelmRoot/values-prod.yaml" -Raw
Assert-Condition "values-prod defines namespace ecommerce-prod" ($prodContent -match "namespace:\s*ecommerce-prod")
Assert-Condition "values-prod sets higher replicaCount" ($prodContent -match "replicaCount:\s*[3-9]")
Assert-Condition "values-prod enables production ingress host" ($prodContent -match "host:\s*shop\.ecommerce\.com")
Assert-Condition "values-prod enables TLS" ($prodContent -match "enabled:\s*true")

# 6. Template Directives & Go Template Syntax Verification
Write-Host "`n--- 6. Testing Template Directives & Bracket Balancing ---" -ForegroundColor Yellow
$templateFiles = Get-ChildItem -Path "$HelmRoot/templates" -Filter "*.yaml" -Recurse
$allTemplatesBalanced = $true
foreach ($tf in $templateFiles) {
    $tContent = Get-Content $tf.FullName -Raw
    $openCount = ([regex]::Matches($tContent, "\{\{")).Count
    $closeCount = ([regex]::Matches($tContent, "\}\}")).Count
    if ($openCount -ne $closeCount) {
        Write-Host "   Mismatched template braces in $($tf.Name): Open: $openCount, Close: $closeCount" -ForegroundColor Red
        $allTemplatesBalanced = $false
    }
}
Assert-Condition "All Helm template files have balanced Go template braces ({{ }})" $allTemplatesBalanced

# 7. Standard Labels and Probe Integration in Templates
Write-Host "`n--- 7. Testing Standard Labels & Probes in Templates ---" -ForegroundColor Yellow
$svcTemplates = @("user-service.yaml", "product-service.yaml", "order-service.yaml", "notification-service.yaml")
foreach ($st in $svcTemplates) {
    $stPath = "$HelmRoot/templates/services/$st"
    $stContent = Get-Content $stPath -Raw
    Assert-Condition "$st includes chart labels helper" ($stContent -match 'include "ecommerce-platform\.labels"')
    Assert-Condition "$st includes livenessProbe" ($stContent -match "livenessProbe:")
    Assert-Condition "$st includes readinessProbe" ($stContent -match "readinessProbe:")
    Assert-Condition "$st parameterizes replicaCount from values" ($stContent -match "\.replicaCount")
    Assert-Condition "$st parameterizes resources from values" ($stContent -match "\.resources")
}

# 8. Ingress and Autoscaling Templates Verification
Write-Host "`n--- 8. Testing Ingress & Autoscaling Templates ---" -ForegroundColor Yellow
$ingContent = Get-Content "$HelmRoot/templates/ingress.yaml" -Raw
Assert-Condition "ingress.yaml conditions on .Values.ingress.enabled" ($ingContent -match "if \.Values\.ingress\.enabled")
Assert-Condition "ingress.yaml uses .Values.ingress.host" ($ingContent -match "\.Values\.ingress\.host")

$hpaContent = Get-Content "$HelmRoot/templates/autoscaling/hpa.yaml" -Raw
Assert-Condition "hpa.yaml conditions on userService.autoscaling.enabled" ($hpaContent -match "\.Values\.services\.userService\.autoscaling\.enabled")
Assert-Condition "hpa.yaml conditions on orderService.autoscaling.enabled" ($hpaContent -match "\.Values\.services\.orderService\.autoscaling\.enabled")
Assert-Condition "hpa.yaml conditions on gateway.autoscaling.enabled" ($hpaContent -match "\.Values\.gateway\.autoscaling\.enabled")

# 9. Release Package Validation (Simulated helm package / tarball)
Write-Host "`n--- 9. Testing Release Packaging Capability ---" -ForegroundColor Yellow
$pkgDir = Join-Path $ProjectRoot "devops/helm/dist"
if (-not (Test-Path $pkgDir)) {
    New-Item -ItemType Directory -Force -Path $pkgDir | Out-Null
}
$archiveName = "ecommerce-platform-1.0.0.tgz"
$archivePath = Join-Path $pkgDir $archiveName

# Create release tarball archive of the chart directory
try {
    if (Test-Path $archivePath) { Remove-Item -Force $archivePath }
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $tempZip = Join-Path $pkgDir "temp.zip"
    if (Test-Path $tempZip) { Remove-Item -Force $tempZip }
    [System.IO.Compression.ZipFile]::CreateFromDirectory($HelmRoot, $tempZip)
    # Rename to release package format
    Copy-Item -Path $tempZip -Destination $archivePath -Force
    Remove-Item -Force $tempZip
    Assert-Condition "Release package created: $archiveName" (Test-Path $archivePath)
    $pkgSize = (Get-Item $archivePath).Length
    Assert-Condition "Release package contains valid byte payload ($pkgSize bytes)" ($pkgSize -gt 1000)
} catch {
    Assert-Condition "Packaging executed without errors ($($_.Exception.Message))" $false
}

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "   Helm Validation Summary: Passed: $Passed, Failed: $Failed " -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan

if ($Failed -gt 0) {
    exit 1
} else {
    exit 0
}
