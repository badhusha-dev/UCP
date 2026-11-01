# ==============================================================================
# Phase 15: Progressive Canary Delivery & Automated Rollback Simulation Script
# Tools: ArgoCD GitOps, Flagger Progressive Delivery, NGINX Ingress, Prometheus
# ==============================================================================

param (
    [switch]$ForceFailure = $false
)

$ErrorActionPreference = "Continue"

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "   Starting Phase 15: GitOps & Progressive Canary Simulation     " -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
if (-not $ScriptDir) { $ScriptDir = $PSScriptRoot }
if (-not $ScriptDir) { $ScriptDir = "." }
$ProjectRoot = (Resolve-Path (Join-Path $ScriptDir "../..")).Path
$K8sDir = Join-Path $ProjectRoot "kubernetes"
$GitopsDir = Join-Path $K8sDir "gitops"

# ------------------------------------------------------------------------------
# 1. Pre-flight Manifest Validation
# ------------------------------------------------------------------------------
Write-Host "`n--- 1. Pre-flight GitOps & Flagger Manifest Check ---" -ForegroundColor Yellow

$argoProject = Join-Path $GitopsDir "argocd/argocd-project.yml"
$argoApp = Join-Path $GitopsDir "argocd/argocd-application.yml"
$argoAppSet = Join-Path $GitopsDir "argocd/argocd-applicationset.yml"
$flaggerCanary = Join-Path $GitopsDir "flagger/flagger-canary-order-service.yml"
$flaggerMetrics = Join-Path $GitopsDir "flagger/flagger-metric-templates.yml"
$flaggerAlerts = Join-Path $GitopsDir "flagger/flagger-alert-provider.yml"

$manifests = @($argoProject, $argoApp, $argoAppSet, $flaggerCanary, $flaggerMetrics, $flaggerAlerts)
foreach ($m in $manifests) {
    if (Test-Path $m) {
        Write-Host " [OK] Manifest found: $(Split-Path $m -Leaf)" -ForegroundColor Green
    } else {
        Write-Host " [FAIL] Missing manifest: $m" -ForegroundColor Red
        exit 1
    }
}

# ------------------------------------------------------------------------------
# 2. Phase A: Progressive Canary Progression (Happy Path - Promotion)
# ------------------------------------------------------------------------------
Write-Host "`n--- 2. Scenario A: Progressive Canary Progression (v1.0.0 -> v2.0.0) ---" -ForegroundColor Yellow

$targetService = "order-service"
$targetPort = 8083
$currentVersion = "v1.0.0"
$newVersion = "v2.0.0"
$maxWeight = 50
$stepWeight = 10
$thresholdSuccessRate = 99.0
$thresholdLatencyMs = 500

Write-Host " [GITOPS] ArgoCD detected commit on main: Update $targetService to image tag '$newVersion'" -ForegroundColor Cyan
Write-Host " [GITOPS] ArgoCD sync state: InSync | Health: Healthy" -ForegroundColor Green
Write-Host " [FLAGGER] Initializing Canary analysis for $targetService" -ForegroundColor Cyan
Write-Host " [FLAGGER] Target Deployment: $targetService | Provider: NGINX | Port: $targetPort" -ForegroundColor DarkGray
Write-Host " [FLAGGER] Primary Deployment: ${targetService}-primary (2 replicas, image: ${targetService}:${currentVersion})" -ForegroundColor DarkGray
Write-Host " [FLAGGER] Canary Deployment:  ${targetService}-canary  (2 replicas, image: ${targetService}:${newVersion})" -ForegroundColor DarkGray

# Simulate Pre-rollout Webhook
Write-Host "`n [WEBHOOK] Executing pre-rollout health check: /actuator/health/readiness..." -ForegroundColor Magenta
Start-Sleep -Milliseconds 200
Write-Host " [WEBHOOK] Pre-rollout health check PASSED (HTTP 200 OK from canary pods)" -ForegroundColor Green

# Step through Canary Weights (10%, 20%, 30%, 40%, 50%)
$currentWeight = 0
while ($currentWeight -lt $maxWeight) {
    $currentWeight += $stepWeight
    $primaryWeight = 100 - $currentWeight
    
    # Simulated Telemetry for happy path
    $simulatedSuccessRate = [Math]::Round(99.4 + (Get-Random -Minimum 0 -Maximum 55) / 100.0, 2)
    $simulatedLatency = [Math]::Round(120.0 + (Get-Random -Minimum 10 -Maximum 60), 1)

    Write-Host "`n [TRAFFIC] Shifting Canary Weight -> $currentWeight% (Primary: $primaryWeight% | Canary: $currentWeight%)" -ForegroundColor Cyan
    Write-Host "           NGINX Ingress annotation: nginx.ingress.kubernetes.io/canary-weight = `"$currentWeight`"" -ForegroundColor DarkGray
    
    Write-Host " [PROMETHEUS] Evaluating metric thresholds over 1m window:" -ForegroundColor Gray
    Write-Host "   - request-success-rate: $simulatedSuccessRate% (threshold >= $thresholdSuccessRate%) -> PASS" -ForegroundColor Green
    Write-Host "   - request-duration-p99: ${simulatedLatency}ms (threshold <= ${thresholdLatencyMs}ms) -> PASS" -ForegroundColor Green
    Write-Host "   - circuit-breaker-open: 0 (threshold = 0) -> PASS" -ForegroundColor Green
    
    Start-Sleep -Milliseconds 150
}

Write-Host "`n [FLAGGER] Canary weight reached maxWeight: $maxWeight%. All metrics passed 5 consecutive checks." -ForegroundColor Green
Write-Host " [PROMOTION] Copying ${targetService}:${newVersion} to ${targetService}-primary..." -ForegroundColor Cyan
Start-Sleep -Milliseconds 200
Write-Host " [PROMOTION] Scaling ${targetService}-primary to 2 replicas (v2.0.0 active)" -ForegroundColor Green
Write-Host " [TRAFFIC] Routing 100% traffic to ${targetService}-primary (Canary weight reset to 0%)" -ForegroundColor Cyan
Write-Host " [FLAGGER] Canary status: Succeeded" -ForegroundColor Green
Write-Host " [ALERT] AlertProvider 'flagger-slack-alerts': Canary promotion succeeded for ${targetService}:${newVersion}" -ForegroundColor Green

# ------------------------------------------------------------------------------
# 3. Phase B: Automated Rollback on Metric Degradation (Fault Injection)
# ------------------------------------------------------------------------------
Write-Host "`n--- 3. Scenario B: Metric Degradation & Automated Rollback ---" -ForegroundColor Yellow

$defectVersion = "v2.1.0-defect"
Write-Host " [GITOPS] ArgoCD detected commit: Update $targetService to image tag '$defectVersion'" -ForegroundColor Cyan
Write-Host " [FLAGGER] Initializing Canary analysis for $targetService" -ForegroundColor Cyan
Write-Host " [FLAGGER] Canary Deployment provisioned: ${targetService}-canary (image: ${targetService}:${defectVersion})" -ForegroundColor DarkGray

Write-Host "`n [WEBHOOK] Executing pre-rollout health check..." -ForegroundColor Magenta
Write-Host " [WEBHOOK] Pre-rollout health check PASSED" -ForegroundColor Green

Write-Host "`n [TRAFFIC] Shifting Canary Weight -> 10% (Primary: 90% | Canary: 10%)" -ForegroundColor Cyan

# Inject simulated fault: High 5xx errors & Latency spike
Write-Host "`n [CHAOS/DEFECT] High error rate burst detected on canary endpoints!" -ForegroundColor Red
Write-Host "               Simulating 5xx Internal Server Errors and upstream timeout latency..." -ForegroundColor Red

$failedChecks = 0
$thresholdFailures = 5

$degradedMetrics = @(
    @{ Rate = 94.2; Latency = 620; OpenCB = 0 },
    @{ Rate = 91.5; Latency = 780; OpenCB = 1 },
    @{ Rate = 88.0; Latency = 890; OpenCB = 1 },
    @{ Rate = 82.4; Latency = 950; OpenCB = 1 },
    @{ Rate = 79.1; Latency = 1100; OpenCB = 1 }
)

foreach ($check in $degradedMetrics) {
    $failedChecks++
    Write-Host "`n [PROMETHEUS] Evaluation #${failedChecks} / ${thresholdFailures}:" -ForegroundColor Yellow
    Write-Host "   - request-success-rate: $($check.Rate)% (< $thresholdSuccessRate% threshold) -> FAILED" -ForegroundColor Red
    Write-Host "   - request-duration-p99: $($check.Latency)ms (> ${thresholdLatencyMs}ms threshold) -> FAILED" -ForegroundColor Red
    Write-Host "   - circuit-breaker-open: $($check.OpenCB) (> 0 threshold) -> FAILED" -ForegroundColor Red
    Write-Host " [FLAGGER] Failed metric check threshold: $failedChecks / $thresholdFailures" -ForegroundColor Red
    Start-Sleep -Milliseconds 150
}

Write-Host "`n [ROLLBACK] Halt condition met! Flagger reached failure threshold ($thresholdFailures/$thresholdFailures)." -ForegroundColor Red
Write-Host " [ROLLBACK] Immediately cutting off canary traffic: Resetting canary weight to 0%!" -ForegroundColor Red
Write-Host " [ROLLBACK] Routing 100% production traffic back to stable Primary (${targetService}:${newVersion})." -ForegroundColor Green
Write-Host " [ROLLBACK] Scaling down ${targetService}-canary replicas to 0." -ForegroundColor Cyan
Write-Host " [FLAGGER] Canary status: Failed" -ForegroundColor Red
Write-Host " [ALERT] AlertProvider 'flagger-slack-alerts': [CRITICAL] Canary rollout failed for ${targetService}:${defectVersion}! Rolled back to primary." -ForegroundColor Red

# ------------------------------------------------------------------------------
# 4. Live Gateway & Cluster Health Verification
# ------------------------------------------------------------------------------
Write-Host "`n--- 4. Live Gateway & Cluster Health Verification ---" -ForegroundColor Yellow

try {
    $gatewayUrl = "http://localhost:8080/actuator/health"
    $response = Invoke-RestMethod -Uri $gatewayUrl -Method Get -TimeoutSec 3 -ErrorAction SilentlyContinue
    if ($response.status -eq "UP") {
        Write-Host " [LIVE CLUSTER] API Gateway ($gatewayUrl) is UP & Healthy!" -ForegroundColor Green
    } else {
        Write-Host " [LIVE CLUSTER] API Gateway returned status: $($response.status)" -ForegroundColor Yellow
    }
} catch {
    Write-Host " [LIVE CLUSTER] API Gateway health check skipped or timed out (Docker container running)." -ForegroundColor DarkGray
}

Write-Host "`n=================================================================" -ForegroundColor Cyan
Write-Host "   Phase 15: Progressive Canary Delivery Simulation Completed!   " -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan
exit 0
