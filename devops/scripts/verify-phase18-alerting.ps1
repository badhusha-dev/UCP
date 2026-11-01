# ==============================================================================
# Phase 18: Production Observability, Alerting & SLO Automation Verification
# ==============================================================================

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "  Starting Phase 18: Observability, Alerting & SLO Test Suite    " -ForegroundColor Cyan
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

# ── 1. Prometheus Alerting Rules Schema Validation ────────────────────────────
Write-Host "`n--- 1. Testing Prometheus Alerting Rules ---" -ForegroundColor Yellow
$RulesFile = Join-Path $ProjectRoot "devops/prometheus/rules/ecommerce-alerts.yml"
Assert-Condition "Prometheus alerting rules file exists" (Test-Path $RulesFile)

if (Test-Path $RulesFile) {
    $rulesContent = Get-Content $RulesFile -Raw
    Assert-Condition "Rules file defines rule group 'ecommerce.slo.rules'" ($rulesContent.Contains("name: ecommerce.slo.rules"))
    Assert-Condition "Rule 'ServiceDown' defined" ($rulesContent.Contains("alert: ServiceDown"))
    Assert-Condition "Rule 'HighHttp5xxRate' defined" ($rulesContent.Contains("alert: HighHttp5xxRate"))
    Assert-Condition "Rule 'HighP99Latency' defined" ($rulesContent.Contains("alert: HighP99Latency"))
    Assert-Condition "Rule 'Resilience4jCircuitBreakerOpen' defined" ($rulesContent.Contains("alert: Resilience4jCircuitBreakerOpen"))
    Assert-Condition "Rule 'Resilience4jCircuitBreakerHalfOpen' defined" ($rulesContent.Contains("alert: Resilience4jCircuitBreakerHalfOpen"))
    Assert-Condition "Rule 'HighJvmHeapUsage' defined" ($rulesContent.Contains("alert: HighJvmHeapUsage"))
    Assert-Condition "Rule 'HighSystemCpuUsage' defined" ($rulesContent.Contains("alert: HighSystemCpuUsage"))
    Assert-Condition "Rule 'HikariPoolSaturation' defined" ($rulesContent.Contains("alert: HikariPoolSaturation"))
    Assert-Condition "Rule 'SyntheticProbeFailure' defined" ($rulesContent.Contains("alert: SyntheticProbeFailure"))
    Assert-Condition "Critical severity labels configured" ($rulesContent.Contains("severity: critical"))
    Assert-Condition "Warning severity labels configured" ($rulesContent.Contains("severity: warning"))
    Assert-Condition "Runbook URL annotations configured" ($rulesContent.Contains("runbook_url:"))
}

# ── 2. Alertmanager Configuration Schema Validation ───────────────────────────
Write-Host "`n--- 2. Testing Alertmanager Configuration ---" -ForegroundColor Yellow
$AlertmanagerConfigFile = Join-Path $ProjectRoot "devops/prometheus/alertmanager.yml"
Assert-Condition "Alertmanager configuration file exists" (Test-Path $AlertmanagerConfigFile)

if (Test-Path $AlertmanagerConfigFile) {
    $amContent = Get-Content $AlertmanagerConfigFile -Raw
    Assert-Condition "Alertmanager defines resolve_timeout" ($amContent.Contains("resolve_timeout:"))
    Assert-Condition "Alertmanager defines routing tree" ($amContent.Contains("route:"))
    Assert-Condition "Alertmanager defines root receiver 'ecommerce-default-webhook'" ($amContent.Contains("receiver: 'ecommerce-default-webhook'"))
    Assert-Condition "Alertmanager defines receiver 'pagerduty-critical'" ($amContent.Contains("name: 'pagerduty-critical'"))
    Assert-Condition "Alertmanager defines receiver 'slack-critical-alerts'" ($amContent.Contains("name: 'slack-critical-alerts'"))
    Assert-Condition "Alertmanager defines receiver 'slack-warning-alerts'" ($amContent.Contains("name: 'slack-warning-alerts'"))
    Assert-Condition "Alertmanager defines receiver 'dba-alerts'" ($amContent.Contains("name: 'dba-alerts'"))
    Assert-Condition "Alertmanager defines receiver 'resilience-alerts'" ($amContent.Contains("name: 'resilience-alerts'"))
    Assert-Condition "Alertmanager defines inhibition rules for ServiceDown" ($amContent.Contains("alertname: 'ServiceDown'"))
    Assert-Condition "Alertmanager defines inhibition rules for CircuitBreaker" ($amContent.Contains("alertname: 'Resilience4jCircuitBreakerOpen'"))
}

# ── 3. Prometheus Server Configuration & Integration ──────────────────────────
Write-Host "`n--- 3. Testing Prometheus Server Config & Live Rules API ---" -ForegroundColor Yellow
$PromConfigFile = Join-Path $ProjectRoot "devops/prometheus/prometheus.yml"
Assert-Condition "Prometheus config exists" (Test-Path $PromConfigFile)

if (Test-Path $PromConfigFile) {
    $promContent = Get-Content $PromConfigFile -Raw
    Assert-Condition "Prometheus config references alertmanager:9093" ($promContent.Contains("alertmanager:9093"))
    Assert-Condition "Prometheus config references rule_files /etc/prometheus/rules/*.yml" ($promContent.Contains("/etc/prometheus/rules/*.yml"))
}

# Live Prometheus API checks
try {
    $promHealthy = Invoke-RestMethod -Uri "http://localhost:9090/-/healthy" -TimeoutSec 5 -ErrorAction Stop
    Assert-Condition "Prometheus live health endpoint responds healthy" ($promHealthy.Contains("Prometheus Server is Healthy"))
} catch {
    Assert-Condition "Prometheus live health endpoint responds healthy" $false
}

try {
    $rulesResp = Invoke-RestMethod -Uri "http://localhost:9090/api/v1/rules" -TimeoutSec 5 -ErrorAction Stop
    $loadedGroups = $rulesResp.data.groups
    $sloGroup = $loadedGroups | Where-Object { $_.name -eq "ecommerce.slo.rules" }
    Assert-Condition "Prometheus loaded rule group 'ecommerce.slo.rules'" ($null -ne $sloGroup)
    Assert-Condition "Prometheus has 9 rules loaded in group" ($sloGroup.rules.Count -eq 9)
} catch {
    Assert-Condition "Prometheus loaded rule group 'ecommerce.slo.rules'" $false
    Assert-Condition "Prometheus has 9 rules loaded in group" $false
}

try {
    $amResp = Invoke-RestMethod -Uri "http://localhost:9090/api/v1/alertmanagers" -TimeoutSec 5 -ErrorAction Stop
    $activeAms = $amResp.data.activeAlertmanagers
    Assert-Condition "Prometheus has active Alertmanager connection" ($activeAms.Count -ge 1)
    Assert-Condition "Prometheus Alertmanager URL matches alertmanager:9093" ($activeAms[0].url.Contains("alertmanager:9093"))
} catch {
    Assert-Condition "Prometheus has active Alertmanager connection" $false
    Assert-Condition "Prometheus Alertmanager URL matches alertmanager:9093" $false
}

# ── 4. Alertmanager Live Container & API Verification ──────────────────────────
Write-Host "`n--- 4. Testing Alertmanager Container & Live API ---" -ForegroundColor Yellow
try {
    $amHealthy = Invoke-RestMethod -Uri "http://localhost:9093/-/healthy" -TimeoutSec 5 -ErrorAction Stop
    Assert-Condition "Alertmanager live health endpoint responds 'OK'" ($amHealthy.Trim() -eq "OK")
} catch {
    Assert-Condition "Alertmanager live health endpoint responds 'OK'" $false
}

try {
    $amStatus = Invoke-RestMethod -Uri "http://localhost:9093/api/v2/status" -TimeoutSec 5 -ErrorAction Stop
    Assert-Condition "Alertmanager API v2 status reports version 0.27.0" ($amStatus.versionInfo.version -eq "0.27.0")
    Assert-Condition "Alertmanager API v2 status contains configuration" ($null -ne $amStatus.config.original)
} catch {
    Assert-Condition "Alertmanager API v2 status reports version 0.27.0" $false
    Assert-Condition "Alertmanager API v2 status contains configuration" $false
}

try {
    $amAlerts = Invoke-RestMethod -Uri "http://localhost:9093/api/v2/alerts" -TimeoutSec 5 -ErrorAction Stop
    Assert-Condition "Alertmanager API v2 alerts endpoint reachable" ($null -ne $amAlerts)
} catch {
    Assert-Condition "Alertmanager API v2 alerts endpoint reachable" $false
}

# ── 5. Synthetic Health Prober & SLO Verification ─────────────────────────────
Write-Host "`n--- 5. Testing Synthetic Health Prober & Live SLOs ---" -ForegroundColor Yellow
$ProberScriptPs = Join-Path $ProjectRoot "devops/scripts/probe-synthetic-health.ps1"
$ProberScriptSh = Join-Path $ProjectRoot "devops/scripts/probe-synthetic-health.sh"
$ProbeReport = Join-Path $ProjectRoot "devops/reports/synthetic-probe-latest.json"

Assert-Condition "Synthetic prober PowerShell script exists" (Test-Path $ProberScriptPs)
Assert-Condition "Synthetic prober Bash script exists" (Test-Path $ProberScriptSh)
Assert-Condition "Synthetic prober report JSON exists" (Test-Path $ProbeReport)

if (Test-Path $ProbeReport) {
    $reportObj = Get-Content $ProbeReport -Raw | ConvertFrom-Json
    Assert-Condition "Synthetic report probed 15 platform & service endpoints" ($reportObj.TotalProbed -eq 15)
    Assert-Condition "Synthetic report reports 100% endpoint health" ($reportObj.HealthyCount -eq 15)
    Assert-Condition "Synthetic report zero failures" ($reportObj.FailedCount -eq 0)
    Assert-Condition "Synthetic report availability is 100%" ($reportObj.AvailabilityPercent -eq 100)
    Assert-Condition "Synthetic report SLO is COMPLIANT" ($reportObj.SloCompliant -eq $true)
    Assert-Condition "Synthetic report average probe latency < 500ms" ($reportObj.AvgLatencyMs -lt 500)
}

# ── 6. Grafana SLO Dashboard Verification ─────────────────────────────────────
Write-Host "`n--- 6. Testing Grafana SLO Dashboard ---" -ForegroundColor Yellow
$SloDashboardFile = Join-Path $ProjectRoot "devops/grafana/dashboards/slo-observability.json"
Assert-Condition "Grafana SLO dashboard JSON exists" (Test-Path $SloDashboardFile)

if (Test-Path $SloDashboardFile) {
    $dashObj = Get-Content $SloDashboardFile -Raw | ConvertFrom-Json
    Assert-Condition "Dashboard UID is ecommerce-slo-observability" ($dashObj.uid -eq "ecommerce-slo-observability")
    Assert-Condition "Dashboard title contains SLO Observability" ($dashObj.title.Contains("SLO Observability"))
    Assert-Condition "Dashboard defines 8 visualization panels" ($dashObj.panels.Count -eq 8)
    
    $panelTitles = $dashObj.panels | ForEach-Object { $_.title }
    Assert-Condition "Panel Global Service Availability SLO present" (@($panelTitles -match "Global Service Availability SLO").Count -gt 0)
    Assert-Condition "Panel P99 Gateway Latency SLO present" (@($panelTitles -match "P99 Gateway Latency SLO").Count -gt 0)
    Assert-Condition "Panel Active Critical and Warning Alerts present" (@($panelTitles -match "Active Critical").Count -gt 0)
    Assert-Condition "Panel SLO Error Budget Remaining present" (@($panelTitles -match "SLO Error Budget Remaining").Count -gt 0)
    Assert-Condition "Panel Resilience4j Circuit Breaker State present" (@($panelTitles -match "Resilience4j Circuit Breaker State").Count -gt 0)
    Assert-Condition "Panel HikariCP Database Pool Saturation present" (@($panelTitles -match "HikariCP Database Pool Saturation").Count -gt 0)
}

# Live Grafana API Verification
try {
    $cred = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("admin:$($env:GRAFANA_ADMIN_PASSWORD)"))
    $dashboards = Invoke-RestMethod -Uri "http://localhost:3030/api/search" -Headers @{Authorization = "Basic $cred"} -TimeoutSec 5 -ErrorAction Stop
    $sloDash = $dashboards | Where-Object { $_.uid -eq "ecommerce-slo-observability" }
    Assert-Condition "Grafana live API confirms 'ecommerce-slo-observability' registered" ($null -ne $sloDash)
    Assert-Condition "Grafana loaded dashboard in 'E-Commerce Platform' folder" ($sloDash.folderTitle -eq "E-Commerce Platform")
} catch {
    Assert-Condition "Grafana live API confirms 'ecommerce-slo-observability' registered" $false
    Assert-Condition "Grafana loaded dashboard in 'E-Commerce Platform' folder" $false
}

# ── 7. Kubernetes & Helm Production Manifests Verification ───────────────────
Write-Host "`n--- 7. Testing Kubernetes & Helm Production Manifests ---" -ForegroundColor Yellow
$K8sAlertmanager = Join-Path $ProjectRoot "kubernetes/monitoring/alertmanager.yml"
$K8sPromGrafana = Join-Path $ProjectRoot "kubernetes/monitoring/prometheus-grafana.yml"
$K8sKustomize = Join-Path $ProjectRoot "kubernetes/kustomization.yml"
$HelmAlertmanager = Join-Path $ProjectRoot "devops/helm/ecommerce-platform/templates/monitoring/alertmanager.yaml"
$HelmValues = Join-Path $ProjectRoot "devops/helm/ecommerce-platform/values.yaml"

Assert-Condition "Kubernetes Alertmanager manifest exists" (Test-Path $K8sAlertmanager)
Assert-Condition "Kubernetes Prometheus & Grafana manifest exists" (Test-Path $K8sPromGrafana)
Assert-Condition "Kubernetes root Kustomization manifest exists" (Test-Path $K8sKustomize)
Assert-Condition "Helm Alertmanager template exists" (Test-Path $HelmAlertmanager)
Assert-Condition "Helm values.yaml exists" (Test-Path $HelmValues)

if (Test-Path $K8sAlertmanager) {
    $k8sAm = Get-Content $K8sAlertmanager -Raw
    Assert-Condition "K8s manifest defines ConfigMap 'alertmanager-config'" ($k8sAm.Contains("name: alertmanager-config"))
    Assert-Condition "K8s manifest defines ConfigMap 'prometheus-rules'" ($k8sAm.Contains("name: prometheus-rules"))
    Assert-Condition "K8s manifest defines Deployment 'alertmanager'" ($k8sAm.Contains("name: alertmanager"))
    Assert-Condition "K8s manifest defines Service 'alertmanager'" ($k8sAm.Contains("kind: Service"))
}

if (Test-Path $K8sPromGrafana) {
    $k8sPg = Get-Content $K8sPromGrafana -Raw
    Assert-Condition "K8s Prometheus config references alertmanager:9093" ($k8sPg.Contains("alertmanager:9093"))
    Assert-Condition "K8s Prometheus mounts rules-volume" ($k8sPg.Contains("name: rules-volume"))
}

if (Test-Path $K8sKustomize) {
    $k8sKust = Get-Content $K8sKustomize -Raw
    Assert-Condition "K8s Kustomization includes monitoring/alertmanager.yml" ($k8sKust.Contains("monitoring/alertmanager.yml"))
}

if (Test-Path $HelmValues) {
    $hv = Get-Content $HelmValues -Raw
    Assert-Condition "Helm values.yaml enables Alertmanager" ($hv.Contains("alertmanager:"))
    Assert-Condition "Helm values.yaml enables monitoring alerts" ($hv.Contains("alerts:"))
}

# ── Summary ───────────────────────────────────────────────────────────────────
Write-Host "`n=================================================================" -ForegroundColor Cyan
Write-Host ("   Phase 18 Verification Complete: {0} Passed, {1} Failed" -f $Passed, $Failed) -ForegroundColor $(if ($Failed -eq 0) { "Green" } else { "Red" })
Write-Host "=================================================================`n" -ForegroundColor Cyan

if ($Failed -gt 0) {
    exit 1
}
exit 0
