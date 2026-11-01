# ==============================================================================
# Phase 11: CI/CD Pipelines & Quality Gate Verification Script
# ==============================================================================

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "   Starting Phase 11: CI/CD & SonarQube Pipeline Validation     " -ForegroundColor Cyan
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

# 1. Pipeline Manifests & Config Existence
Write-Host "`n--- 1. Testing Pipeline Manifests Existence ---" -ForegroundColor Yellow
$RequiredPipelineFiles = @(
    (Join-Path $ProjectRoot ".github/workflows/ci-backend.yml"),
    (Join-Path $ProjectRoot ".github/workflows/ci-frontend.yml"),
    (Join-Path $ProjectRoot ".github/workflows/cd-release.yml"),
    (Join-Path $ProjectRoot "backend/sonar-project.properties")
)

foreach ($f in $RequiredPipelineFiles) {
    Assert-Condition "File exists: $(Split-Path -Leaf $f)" (Test-Path $f)
}

# 2. Backend CI Workflow Validation
Write-Host "`n--- 2. Testing Backend CI & Quality Gate Workflow ---" -ForegroundColor Yellow
$backendCiPath = Join-Path $ProjectRoot ".github/workflows/ci-backend.yml"
$backendContent = Get-Content $backendCiPath -Raw

Assert-Condition "ci-backend defines matrix strategy" ($backendContent -match "matrix:")
$RequiredBackendServices = @("discovery-server", "config-server", "user-service", "product-service", "order-service", "notification-service", "api-gateway")
foreach ($svc in $RequiredBackendServices) {
    Assert-Condition "ci-backend matrix includes $svc" ($backendContent -match "\s+-\s+$svc\b")
}

Assert-Condition "ci-backend sets up JDK 17 with Temurin" ($backendContent -match "java-version:\s*'17'" -and $backendContent -match "distribution:\s*'temurin'")
Assert-Condition "ci-backend enables Maven caching" ($backendContent -match "cache:\s*maven")
Assert-Condition "ci-backend runs unit tests (mvnw clean test)" ($backendContent -match "mvnw clean test")
Assert-Condition "ci-backend packages JAR artifacts" ($backendContent -match "mvnw package")
Assert-Condition "ci-backend uploads test report artifacts" ($backendContent -match "upload-artifact" -and $backendContent -match "surefire-reports")
Assert-Condition "ci-backend defines SonarQube analysis job" ($backendContent -match "sonarqube-scan-action")
Assert-Condition "ci-backend enforces SonarQube Quality Gate action" ($backendContent -match "sonarqube-quality-gate-action")
Assert-Condition "ci-backend defines Docker publish job" ($backendContent -match "docker/build-push-action")
Assert-Condition "ci-backend tags images with commit sha and latest" ($backendContent -match "github\.sha" -and $backendContent -match ":latest")

# 3. Frontend CI Workflow Validation
Write-Host "`n--- 3. Testing Frontend Microfrontends CI Workflow ---" -ForegroundColor Yellow
$frontendCiPath = Join-Path $ProjectRoot ".github/workflows/ci-frontend.yml"
$frontendContent = Get-Content $frontendCiPath -Raw

Assert-Condition "ci-frontend defines matrix strategy" ($frontendContent -match "matrix:")
$RequiredFrontendApps = @("shell-app", "mfe-product", "mfe-order", "mfe-user")
foreach ($app in $RequiredFrontendApps) {
    Assert-Condition "ci-frontend matrix includes $app" ($frontendContent -match "\s+-\s+$app\b")
}

Assert-Condition "ci-frontend sets up Node.js 20" ($frontendContent -match "node-version:\s*20")
Assert-Condition "ci-frontend enables npm caching" ($frontendContent -match "cache:\s*'npm'")
Assert-Condition "ci-frontend executes npm run build" ($frontendContent -match "npm run build")
Assert-Condition "ci-frontend validates dist directory generation" ($frontendContent -match "dist")
Assert-Condition "ci-frontend defines Docker build and push for MFEs" ($frontendContent -match "docker/build-push-action")

# 4. Release CD Workflow Validation
Write-Host "`n--- 4. Testing Release CD Workflow (K8s & Helm) ---" -ForegroundColor Yellow
$cdPath = Join-Path $ProjectRoot ".github/workflows/cd-release.yml"
$cdContent = Get-Content $cdPath -Raw

Assert-Condition "cd-release triggers on main push and release tags (v*)" ($cdContent -match "branches:\s*\[\s*main\s*\]" -and $cdContent -match "tags:\s*\[\s*'v\*'\s*\]")
Assert-Condition "cd-release validates Kustomize resource graph" ($cdContent -match "kubectl kustomize kubernetes/")
Assert-Condition "cd-release lints Helm base chart" ($cdContent -match "helm lint devops/helm/ecommerce-platform")
Assert-Condition "cd-release lints Helm dev profile" ($cdContent -match "values-dev\.yaml")
Assert-Condition "cd-release lints Helm prod profile" ($cdContent -match "values-prod\.yaml")
Assert-Condition "cd-release executes Helm dry-run template rendering" ($cdContent -match "helm template")
Assert-Condition "cd-release packages Helm release archive" ($cdContent -match "helm package")
Assert-Condition "cd-release uploads Helm package artifact" ($cdContent -match "upload-artifact" -and $cdContent -match "\*\.tgz")
Assert-Condition "cd-release simulates dry-run deployment" ($cdContent -match "helm upgrade --dry-run")

# 5. SonarQube Properties Validation
Write-Host "`n--- 5. Testing SonarQube Scanner Configuration ---" -ForegroundColor Yellow
$sonarPath = Join-Path $ProjectRoot "backend/sonar-project.properties"
$sonarContent = Get-Content $sonarPath -Raw

Assert-Condition "sonar-project defines projectKey" ($sonarContent -match "sonar\.projectKey=ecommerce-platform-backend")
Assert-Condition "sonar-project configures Java source 17" ($sonarContent -match "sonar\.java\.source=17")
Assert-Condition "sonar-project includes all microservices in sources" ($sonarContent -match "user-service" -and $sonarContent -match "product-service" -and $sonarContent -match "order-service" -and $sonarContent -match "notification-service")
Assert-Condition "sonar-project specifies jacoco XML report path" ($sonarContent -match "sonar\.coverage\.jacoco\.xmlReportPaths")
Assert-Condition "sonar-project specifies surefire report path" ($sonarContent -match "sonar\.junit\.reportPaths")
Assert-Condition "sonar-project excludes generated migration & swagger configs" ($sonarContent -match "db/migration" -and $sonarContent -match "OpenApiConfig")

# 6. Local Pipeline Simulation (Maven validate in backend)
Write-Host "`n--- 6. Local Build Engine Verification ---" -ForegroundColor Yellow
try {
    $backendDir = Join-Path $ProjectRoot "backend"
    $mvnwCmd = Join-Path $backendDir "mvnw.cmd"
    if (Test-Path $mvnwCmd) {
        Push-Location $backendDir
        $mvnOutput = & ".\mvnw.cmd" -v 2>&1
        Pop-Location
        $hasMvn = ($mvnOutput -match "Apache Maven" -or $mvnOutput -match "Maven home")
        Assert-Condition "Maven wrapper is functional for CI runner execution" $hasMvn
    } else {
        Assert-Condition "Maven wrapper script found" $false
    }
} catch {
    if ((Get-Location).Path -ne $ProjectRoot) { Pop-Location }
    Assert-Condition "Maven wrapper check failed ($($_.Exception.Message))" $false
}

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "   CI/CD Validation Summary: Passed: $Passed, Failed: $Failed   " -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan

if ($Failed -gt 0) {
    exit 1
} else {
    exit 0
}
