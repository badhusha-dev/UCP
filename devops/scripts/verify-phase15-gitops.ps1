# ==============================================================================
# Phase 15: GitOps & Progressive Canary Delivery Verification Script
# ==============================================================================

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "   Starting Phase 15: GitOps & Progressive Delivery Verification  " -ForegroundColor Cyan
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
if (-not $ScriptDir) { $ScriptDir = "." }
$ProjectRoot = (Resolve-Path (Join-Path $ScriptDir "../..")).Path
$K8sDir = Join-Path $ProjectRoot "kubernetes"
$GitopsDir = Join-Path $K8sDir "gitops"
$ArgoDir = Join-Path $GitopsDir "argocd"
$FlaggerDir = Join-Path $GitopsDir "flagger"
$DevopsDir = Join-Path $ProjectRoot "devops"

# ------------------------------------------------------------------------------
# 1. ArgoCD GitOps Manifests
# ------------------------------------------------------------------------------
Write-Host "`n--- 1. ArgoCD GitOps Manifests ---" -ForegroundColor Yellow

$argoProjectFile = Join-Path $ArgoDir "argocd-project.yml"
Assert-Condition "argocd-project.yml exists" (Test-Path $argoProjectFile)
if (Test-Path $argoProjectFile) {
    $argoProject = Get-Content $argoProjectFile -Raw
    Assert-Condition "AppProject defines argoproj.io/v1alpha1 apiVersion" ($argoProject -match "apiVersion:\s*argoproj\.io/v1alpha1")
    Assert-Condition "AppProject defines kind: AppProject" ($argoProject -match "kind:\s*AppProject")
    Assert-Condition "AppProject targets ecommerce-platform name" ($argoProject -match "name:\s*ecommerce-platform")
    Assert-Condition "AppProject configures ecommerce destination namespace" ($argoProject -match "namespace:\s*ecommerce\b")
    Assert-Condition "AppProject configures ecommerce-dev destination namespace" ($argoProject -match "namespace:\s*ecommerce-dev")
    Assert-Condition "AppProject configures ecommerce-staging destination namespace" ($argoProject -match "namespace:\s*ecommerce-staging")
    Assert-Condition "AppProject whitelists Flagger MetricTemplate" ($argoProject -match "kind:\s*MetricTemplate")
    Assert-Condition "AppProject whitelists Flagger AlertProvider" ($argoProject -match "kind:\s*AlertProvider")
    Assert-Condition "AppProject defines platform-admin role" ($argoProject -match "name:\s*platform-admin")
    Assert-Condition "AppProject defines developer-readonly role" ($argoProject -match "name:\s*developer-readonly")
}

$argoAppFile = Join-Path $ArgoDir "argocd-application.yml"
Assert-Condition "argocd-application.yml exists" (Test-Path $argoAppFile)
if (Test-Path $argoAppFile) {
    $argoApp = Get-Content $argoAppFile -Raw
    Assert-Condition "Application defines argoproj.io/v1alpha1 apiVersion" ($argoApp -match "apiVersion:\s*argoproj\.io/v1alpha1")
    Assert-Condition "Application defines kind: Application" ($argoApp -match "kind:\s*Application")
    Assert-Condition "Application targets ecommerce namespace" ($argoApp -match "namespace:\s*ecommerce")
    Assert-Condition "Application points to kubernetes path" ($argoApp -match "path:\s*kubernetes")
    Assert-Condition "Application syncPolicy enables prune" ($argoApp -match "prune:\s*true")
    Assert-Condition "Application syncPolicy enables selfHeal" ($argoApp -match "selfHeal:\s*true")
    Assert-Condition "Application configures CreateNamespace syncOption" ($argoApp -match "CreateNamespace=true")
    Assert-Condition "Application configures ApplyOutOfSyncOnly syncOption" ($argoApp -match "ApplyOutOfSyncOnly=true")
    Assert-Condition "Application configures retry backoff" ($argoApp -match "retry:\s*(\r?\n\s+)+limit:\s*\d+")
    Assert-Condition "Application ignores differences on Deployment /spec/replicas" ($argoApp -match "/spec/replicas")
}

$argoAppSetFile = Join-Path $ArgoDir "argocd-applicationset.yml"
Assert-Condition "argocd-applicationset.yml exists" (Test-Path $argoAppSetFile)
if (Test-Path $argoAppSetFile) {
    $argoAppSet = Get-Content $argoAppSetFile -Raw
    Assert-Condition "ApplicationSet defines kind: ApplicationSet" ($argoAppSet -match "kind:\s*ApplicationSet")
    Assert-Condition "ApplicationSet configures list generator" ($argoAppSet -match "generators:\s*(\r?\n\s+)+-\s*list:")
    Assert-Condition "ApplicationSet includes dev environment" ($argoAppSet -match "env:\s*dev")
    Assert-Condition "ApplicationSet includes staging environment" ($argoAppSet -match "env:\s*staging")
    Assert-Condition "ApplicationSet includes prod environment" ($argoAppSet -match "env:\s*prod")
    Assert-Condition "ApplicationSet uses parameter interpolation" ($argoAppSet -match "\{\{env\}\}")
}

# ------------------------------------------------------------------------------
# 2. Flagger Metric Templates & Alert Providers
# ------------------------------------------------------------------------------
Write-Host "`n--- 2. Flagger Metric Templates & Alert Providers ---" -ForegroundColor Yellow

$flaggerMetricFile = Join-Path $FlaggerDir "flagger-metric-templates.yml"
Assert-Condition "flagger-metric-templates.yml exists" (Test-Path $flaggerMetricFile)
if (Test-Path $flaggerMetricFile) {
    $metricsContent = Get-Content $flaggerMetricFile -Raw
    Assert-Condition "MetricTemplate defines flagger.app/v1beta1 apiVersion" ($metricsContent -match "apiVersion:\s*flagger\.app/v1beta1")
    Assert-Condition "MetricTemplate defines request-success-rate" ($metricsContent -match "name:\s*request-success-rate")
    Assert-Condition "MetricTemplate defines request-duration" ($metricsContent -match "name:\s*request-duration")
    Assert-Condition "MetricTemplate defines circuit-breaker-open-state" ($metricsContent -match "name:\s*circuit-breaker-open-state")
    Assert-Condition "MetricTemplate queries Prometheus provider" ($metricsContent -match "type:\s*prometheus")
    Assert-Condition "request-duration calculates P99 histogram quantile" ($metricsContent -match "histogram_quantile\(\s*0\.99")
}

$flaggerAlertFile = Join-Path $FlaggerDir "flagger-alert-provider.yml"
Assert-Condition "flagger-alert-provider.yml exists" (Test-Path $flaggerAlertFile)
if (Test-Path $flaggerAlertFile) {
    $alertsContent = Get-Content $flaggerAlertFile -Raw
    Assert-Condition "AlertProvider defines flagger-slack-alerts" ($alertsContent -match "name:\s*flagger-slack-alerts")
    Assert-Condition "AlertProvider defines flagger-webhook-alerts" ($alertsContent -match "name:\s*flagger-webhook-alerts")
    Assert-Condition "AlertProvider configures slack type" ($alertsContent -match "type:\s*slack")
}

# ------------------------------------------------------------------------------
# 3. Flagger Progressive Canary Releases
# ------------------------------------------------------------------------------
Write-Host "`n--- 3. Flagger Progressive Canary Manifests ---" -ForegroundColor Yellow

$orderCanaryFile = Join-Path $FlaggerDir "flagger-canary-order-service.yml"
Assert-Condition "flagger-canary-order-service.yml exists" (Test-Path $orderCanaryFile)
if (Test-Path $orderCanaryFile) {
    $orderCanary = Get-Content $orderCanaryFile -Raw
    Assert-Condition "order-service canary defines kind: Canary" ($orderCanary -match "kind:\s*Canary")
    Assert-Condition "order-service canary targets Deployment order-service" ($orderCanary -match "name:\s*order-service")
    Assert-Condition "order-service canary uses nginx provider" ($orderCanary -match "provider:\s*nginx")
    Assert-Condition "order-service canary references ecommerce-ingress" ($orderCanary -match "name:\s*ecommerce-ingress")
    Assert-Condition "order-service canary configures port 8083" ($orderCanary -match "port:\s*8083")
    Assert-Condition "order-service canary analysis interval is 1m" ($orderCanary -match "interval:\s*1m")
    Assert-Condition "order-service canary threshold is 5 failures" ($orderCanary -match "threshold:\s*5")
    Assert-Condition "order-service canary maxWeight is 50%" ($orderCanary -match "maxWeight:\s*50")
    Assert-Condition "order-service canary stepWeight is 10%" ($orderCanary -match "stepWeight:\s*10")
    Assert-Condition "order-service canary defines request-success-rate min 99" ($orderCanary -match "min:\s*99")
    Assert-Condition "order-service canary defines request-duration max 500" ($orderCanary -match "max:\s*500")
    Assert-Condition "order-service canary defines pre-rollout health check" ($orderCanary -match "pre-rollout-health-check")
}

$productCanaryFile = Join-Path $FlaggerDir "flagger-canary-product-service.yml"
Assert-Condition "flagger-canary-product-service.yml exists" (Test-Path $productCanaryFile)
if (Test-Path $productCanaryFile) {
    $productCanary = Get-Content $productCanaryFile -Raw
    Assert-Condition "product-service canary targets product-service" ($productCanary -match "name:\s*product-service")
    Assert-Condition "product-service canary configures port 8082" ($productCanary -match "port:\s*8082")
    Assert-Condition "product-service canary latency threshold is 350ms" ($productCanary -match "max:\s*350")
}

$userCanaryFile = Join-Path $FlaggerDir "flagger-canary-user-service.yml"
Assert-Condition "flagger-canary-user-service.yml exists" (Test-Path $userCanaryFile)
if (Test-Path $userCanaryFile) {
    $userCanary = Get-Content $userCanaryFile -Raw
    Assert-Condition "user-service canary targets user-service" ($userCanary -match "name:\s*user-service")
    Assert-Condition "user-service canary configures port 8081" ($userCanary -match "port:\s*8081")
    Assert-Condition "user-service canary latency threshold is 400ms" ($userCanary -match "max:\s*400")
}

# ------------------------------------------------------------------------------
# 4. Kustomize Integration & Manifest Compilation
# ------------------------------------------------------------------------------
Write-Host "`n--- 4. Kustomize Integration & Manifest Compilation ---" -ForegroundColor Yellow

$gitopsKustomizeFile = Join-Path $GitopsDir "kustomization.yml"
Assert-Condition "gitops/kustomization.yml exists" (Test-Path $gitopsKustomizeFile)

$rootKustomizeFile = Join-Path $K8sDir "kustomization.yml"
if (Test-Path $rootKustomizeFile) {
    $rootKustomize = Get-Content $rootKustomizeFile -Raw
    Assert-Condition "root kustomization.yml references gitops" ($rootKustomize -match "-\s*gitops\b")
}

# Test Kustomize Build
try {
    $kustomizeOutput = kubectl kustomize $GitopsDir 2>&1
    $kustomizeExit = $LASTEXITCODE
    Assert-Condition "kubectl kustomize gitops compiles with code 0" ($kustomizeExit -eq 0)
} catch {
    Assert-Condition "kubectl kustomize gitops executes without exception" $false
}

try {
    $rootKustomizeOutput = kubectl kustomize $K8sDir 2>&1
    $rootKustomizeExit = $LASTEXITCODE
    Assert-Condition "kubectl kustomize root k8s compiles with code 0" ($rootKustomizeExit -eq 0)
} catch {
    Assert-Condition "kubectl kustomize root k8s executes without exception" $false
}

# ------------------------------------------------------------------------------
# 5. Progressive Canary Simulation Automation
# ------------------------------------------------------------------------------
Write-Host "`n--- 5. Progressive Canary Simulation Automation ---" -ForegroundColor Yellow

$simScriptPs1 = Join-Path $DevopsDir "scripts/simulate-canary.ps1"
Assert-Condition "simulate-canary.ps1 exists" (Test-Path $simScriptPs1)

$simScriptSh = Join-Path $DevopsDir "scripts/simulate-canary.sh"
Assert-Condition "simulate-canary.sh exists" (Test-Path $simScriptSh)

if (Test-Path $simScriptPs1) {
    try {
        Write-Host " Running simulate-canary.ps1..." -ForegroundColor Gray
        & $simScriptPs1
        $simExit = $LASTEXITCODE
        Assert-Condition "simulate-canary.ps1 completed with exit code 0" ($simExit -eq 0)
    } catch {
        Assert-Condition "simulate-canary.ps1 executed successfully" $false
    }
}

if (Get-Command bash -ErrorAction SilentlyContinue) {
    try {
        Write-Host " Running simulate-canary.sh via bash..." -ForegroundColor Gray
        $drive = $DevopsDir.Substring(0, 1).ToLower()
        $rest = ($DevopsDir.Substring(2) -replace '\\', '/')
        $wslPath = "/mnt/$drive$rest/scripts"
        $bashOut = & bash -c "cd '$wslPath' && ./simulate-canary.sh" 2>&1
        $bashExit = $LASTEXITCODE
        Assert-Condition "simulate-canary.sh completed with exit code 0" ($bashExit -eq 0)
    } catch {
        Assert-Condition "simulate-canary.sh executed successfully via bash" $false
    }
}

# ------------------------------------------------------------------------------
# Final Verification Summary
# ------------------------------------------------------------------------------
Write-Host "`n=================================================================" -ForegroundColor Cyan
Write-Host "               Phase 15 Verification Summary                     " -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host " Total Assertions Passed : $Passed" -ForegroundColor Green
Write-Host " Total Assertions Failed : $Failed" -ForegroundColor $(if ($Failed -eq 0) { "Green" } else { "Red" })
Write-Host "=================================================================" -ForegroundColor Cyan

if ($Failed -gt 0) {
    exit 1
}
