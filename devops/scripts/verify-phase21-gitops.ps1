<#
.SYNOPSIS
    Phase 21 GitOps & Cloud Kubernetes Verification Suite - Universal Commerce Platform
.DESCRIPTION
    Validates AWS EKS Cloud Integration manifests, PodDisruptionBudgets, ArgoCD App-of-Apps,
    and multi-environment Kustomize overlays (cloud-dev, cloud-prod).
#>

$ErrorActionPreference = "Stop"
$BasePath = "D:\practical\ecommerce-platform"
$K8sPath  = "$BasePath\kubernetes"

Write-Host "======================================================================" -ForegroundColor Cyan
Write-Host " [PHASE 21] Kubernetes GitOps & Cloud EKS Deployment Verification    " -ForegroundColor Cyan
Write-Host "======================================================================" -ForegroundColor Cyan

$Passed = 0
$Failed = 0

function Assert-Test {
    param(
        [string]$TestName,
        [bool]$Condition,
        [string]$Details = ""
    )
    if ($Condition) {
        Write-Host "  [PASS] $TestName" -ForegroundColor Green
        $script:Passed++
    } else {
        Write-Host "  [FAIL] $TestName - $Details" -ForegroundColor Red
        $script:Failed++
    }
}

# ─────────────────────────────────────────────────────────────────────────────
# 1. Structure & Manifest Completeness Tests
# ─────────────────────────────────────────────────────────────────────────────
Write-Host "`n--- Section 1: Manifest Files & Directory Completeness ---" -ForegroundColor Yellow

$RequiredFiles = @(
    "cloud-integration\aws-services-endpoints.yml",
    "cloud-integration\aws-storage-class.yml",
    "cloud-integration\aws-irsa-service-accounts.yml",
    "cloud-integration\aws-alb-ingress.yml",
    "base\pod-disruption-budgets.yml",
    "gitops\argocd\app-of-apps.yml",
    "gitops\argocd\argocd-project.yml",
    "gitops\argocd\argocd-application.yml",
    "gitops\argocd\argocd-applicationset.yml",
    "overlays\cloud-dev\kustomization.yml",
    "overlays\cloud-prod\kustomization.yml"
)

foreach ($file in $RequiredFiles) {
    $fullPath = "$K8sPath\$file"
    Assert-Test "Required manifest '$file' exists" (Test-Path $fullPath) "Missing at $fullPath"
}

# ─────────────────────────────────────────────────────────────────────────────
# 2. AWS Managed Services & Cloud Integration Assertions
# ─────────────────────────────────────────────────────────────────────────────
Write-Host "`n--- Section 2: AWS Managed Services & Cloud Integration ---" -ForegroundColor Yellow

$endpoints = Get-Content "$K8sPath\cloud-integration\aws-services-endpoints.yml" -Raw
Assert-Test "PostgreSQL ExternalName routes to AWS RDS endpoint (*.rds.amazonaws.com)" ($endpoints -match 'name:\s*postgres' -and $endpoints -match 'rds\.amazonaws\.com')
Assert-Test "Redis ExternalName routes to AWS ElastiCache endpoint (*.cache.amazonaws.com)" ($endpoints -match 'name:\s*redis' -and $endpoints -match 'cache\.amazonaws\.com')
Assert-Test "Kafka ExternalName routes to AWS MSK endpoint (*.msk.us-east-1.amazonaws.com)" ($endpoints -match 'name:\s*kafka' -and $endpoints -match 'msk\.us-east-1\.amazonaws\.com')
Assert-Test "Kafka Service exposes plaintext (9092), TLS (9094), and IAM (9098) ports" ($endpoints -match 'port:\s*9092' -and $endpoints -match 'port:\s*9094' -and $endpoints -match 'port:\s*9098')

$sc = Get-Content "$K8sPath\cloud-integration\aws-storage-class.yml" -Raw
Assert-Test "EBS StorageClass uses 'ebs.csi.aws.com' provisioner with GP3" ($sc -match 'provisioner:\s*ebs\.csi\.aws\.com' -and $sc -match 'type:\s*gp3')
Assert-Test "StorageClass enforces KMS encryption (encrypted: 'true')" ($sc -match 'encrypted:\s*"true"')
Assert-Test "StorageClass sets volumeBindingMode to WaitForFirstConsumer" ($sc -match 'volumeBindingMode:\s*WaitForFirstConsumer')

$irsa = Get-Content "$K8sPath\cloud-integration\aws-irsa-service-accounts.yml" -Raw
Assert-Test "App ServiceAccount has 'eks.amazonaws.com/role-arn' annotation" ($irsa -match 'name:\s*ecommerce-app-sa' -and $irsa -match 'eks\.amazonaws\.com/role-arn')
Assert-Test "ALB Controller ServiceAccount configured for kube-system" ($irsa -match 'name:\s*aws-load-balancer-controller' -and $irsa -match 'namespace:\s*kube-system')
Assert-Test "ExternalDNS ServiceAccount configured for Route53 DNS automation" ($irsa -match 'name:\s*external-dns')

$alb = Get-Content "$K8sPath\cloud-integration\aws-alb-ingress.yml" -Raw
Assert-Test "ALB Ingress specifies internet-facing scheme and ip target-type" ($alb -match 'alb\.ingress\.kubernetes\.io/scheme:\s*internet-facing' -and $alb -match 'alb\.ingress\.kubernetes\.io/target-type:\s*ip')
Assert-Test "ALB Ingress enforces SSL redirect to port 443" ($alb -match 'alb\.ingress\.kubernetes\.io/ssl-redirect:\s*.443.')
Assert-Test "ALB Ingress routes /api to api-gateway:8080 and / to shell-app:80" ($alb -match 'path:\s*/api' -and $alb -match 'name:\s*api-gateway' -and $alb -match 'path:\s*/' -and $alb -match 'name:\s*shell-app')

# ─────────────────────────────────────────────────────────────────────────────
# 3. High-Availability Pod Disruption Budgets (PDB) Assertions
# ─────────────────────────────────────────────────────────────────────────────
Write-Host "`n--- Section 3: High-Availability Pod Disruption Budgets ---" -ForegroundColor Yellow

$pdb = Get-Content "$K8sPath\base\pod-disruption-budgets.yml" -Raw
$criticalServices = @("api-gateway", "discovery-server", "config-server", "user-service", "product-service", "order-service", "notification-service", "shell-app", "mfe-product", "mfe-order", "mfe-user")

foreach ($svc in $criticalServices) {
    Assert-Test "PodDisruptionBudget '$svc-pdb' guarantees minAvailable: 1" ($pdb -match "name:\s*$svc-pdb" -and $pdb -match 'minAvailable:\s*1')
}

# ─────────────────────────────────────────────────────────────────────────────
# 4. ArgoCD "App-of-Apps" & GitOps Sync Engine Assertions
# ─────────────────────────────────────────────────────────────────────────────
Write-Host "`n--- Section 4: ArgoCD App-of-Apps & GitOps Sync Waves ---" -ForegroundColor Yellow

$appOfApps = Get-Content "$K8sPath\gitops\argocd\app-of-apps.yml" -Raw
Assert-Test "ArgoCD Master Root App 'ecommerce-root-app' declared" ($appOfApps -match 'name:\s*ecommerce-root-app')
Assert-Test "Wave 0: Infrastructure & Cloud Storage declared" ($appOfApps -match 'argocd\.argoproj\.io/sync-wave:\s*"0"' -and $appOfApps -match 'name:\s*ecommerce-cloud-infrastructure')
Assert-Test "Wave 1: Security Policies & Vault declared" ($appOfApps -match 'argocd\.argoproj\.io/sync-wave:\s*"1"' -and $appOfApps -match 'name:\s*ecommerce-security-policies')
Assert-Test "Wave 2: Platform Core (Config & Discovery) declared" ($appOfApps -match 'argocd\.argoproj\.io/sync-wave:\s*"2"' -and $appOfApps -match 'name:\s*ecommerce-platform-core')
Assert-Test "Wave 3: Domain Microservices declared" ($appOfApps -match 'argocd\.argoproj\.io/sync-wave:\s*"3"' -and $appOfApps -match 'name:\s*ecommerce-domain-services')
Assert-Test "Wave 4: Frontend Microfrontends Storefront declared" ($appOfApps -match 'argocd\.argoproj\.io/sync-wave:\s*"4"' -and $appOfApps -match 'name:\s*ecommerce-frontend-storefront')
Assert-Test "Wave 5: Istio Service Mesh & Gateway declared" ($appOfApps -match 'argocd\.argoproj\.io/sync-wave:\s*"5"' -and $appOfApps -match 'name:\s*ecommerce-service-mesh')
Assert-Test "Wave 6: Observability (Prometheus, Grafana, Alertmanager) declared" ($appOfApps -match 'argocd\.argoproj\.io/sync-wave:\s*"6"' -and $appOfApps -match 'name:\s*ecommerce-observability')
Assert-Test "Wave 7: Progressive Canary Releases (Flagger) declared" ($appOfApps -match 'argocd\.argoproj\.io/sync-wave:\s*"7"' -and $appOfApps -match 'name:\s*ecommerce-canary-rollouts')
Assert-Test "Automated sync policy enabled (prune: true, selfHeal: true)" ($appOfApps -match 'prune:\s*true' -and $appOfApps -match 'selfHeal:\s*true')

# ─────────────────────────────────────────────────────────────────────────────
# 5. Multi-Environment Kustomize Overlays Assertions
# ─────────────────────────────────────────────────────────────────────────────
Write-Host "`n--- Section 5: Multi-Environment Cloud Overlays ---" -ForegroundColor Yellow

$devOverlay  = Get-Content "$K8sPath\overlays\cloud-dev\kustomization.yml" -Raw
$prodOverlay = Get-Content "$K8sPath\overlays\cloud-prod\kustomization.yml" -Raw

Assert-Test "Cloud Dev overlay configures namespace 'ecommerce-dev' and 'dev-' namePrefix" ($devOverlay -match 'namespace:\s*ecommerce-dev' -and $devOverlay -match 'namePrefix:\s*dev-')
Assert-Test "Cloud Dev overlay applies Spot node scheduling toleration (lifecycle: spot)" ($devOverlay -match 'key:\s*"workload"' -and $devOverlay -match '"spot"')
Assert-Test "Cloud Dev overlay enforces cost-effective single replica (replicas: 1)" ($devOverlay -match 'replicas:\s*1')

Assert-Test "Cloud Prod overlay configures namespace 'ecommerce' and 'prod-' namePrefix" ($prodOverlay -match 'namespace:\s*ecommerce' -and $prodOverlay -match 'namePrefix:\s*prod-')
Assert-Test "Cloud Prod overlay enforces Multi-AZ Pod Anti-Affinity (topology.kubernetes.io/zone)" ($prodOverlay -match 'topologyKey:\s*topology\.kubernetes\.io/zone')
Assert-Test "Cloud Prod overlay enforces Istio sidecar injection (sidecar.istio.io/inject: 'true')" ($prodOverlay -match 'sidecar\.istio\.io/inject:\s*"true"')
Assert-Test "Cloud Prod overlay configures ServiceAccount ecommerce-app-sa for all pods" ($prodOverlay -match 'serviceAccountName:\s*ecommerce-app-sa')
Assert-Test "Cloud Prod overlay includes Flagger canaries, alert providers, and telemetry" ($prodOverlay -match 'flagger-canary' -and $prodOverlay -match 'telemetry\.yml')

# ─────────────────────────────────────────────────────────────────────────────
# 6. Summary & Verification Outcome
# ─────────────────────────────────────────────────────────────────────────────
Write-Host "`n======================================================================" -ForegroundColor Cyan
Write-Host " VERIFICATION SUMMARY: $Passed Passed, $Failed Failed" -ForegroundColor $(if ($Failed -eq 0) { "Green" } else { "Red" })
Write-Host "======================================================================" -ForegroundColor Cyan

if ($Failed -gt 0) {
    Exit 1
} else {
    Exit 0
}
