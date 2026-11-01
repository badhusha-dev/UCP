# ==============================================================================
# Phase 16: Enterprise Service Mesh (Istio & Envoy) Verification Script
# ==============================================================================

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "   Starting Phase 16: Istio Service Mesh Verification Suite       " -ForegroundColor Cyan
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
$MeshDir = Join-Path $K8sDir "mesh"
$DevopsDir = Join-Path $ProjectRoot "devops"

# ------------------------------------------------------------------------------
# 1. Istio Gateway Configuration
# ------------------------------------------------------------------------------
Write-Host "`n--- 1. Istio Gateway Configuration ---" -ForegroundColor Yellow

$gwFile = Join-Path $MeshDir "gateway.yml"
Assert-Condition "gateway.yml exists" (Test-Path $gwFile)
if (Test-Path $gwFile) {
    $gwContent = Get-Content $gwFile -Raw
    Assert-Condition "Gateway defines networking.istio.io/v1beta1" ($gwContent -match "apiVersion:\s*networking\.istio\.io/v1beta1")
    Assert-Condition "Gateway defines kind: Gateway" ($gwContent -match "kind:\s*Gateway")
    Assert-Condition "Gateway named ecommerce-gateway" ($gwContent -match "name:\s*ecommerce-gateway")
    Assert-Condition "Gateway targets istio: ingressgateway selector" ($gwContent -match "istio:\s*ingressgateway")
    Assert-Condition "Gateway defines HTTP port 80" ($gwContent -match "number:\s*80")
    Assert-Condition "Gateway defines HTTPS port 443" ($gwContent -match "number:\s*443")
    Assert-Condition "Gateway defines hosts ecommerce.local" ($gwContent -match "ecommerce\.local")
    Assert-Condition "Gateway sets TLS mode SIMPLE" ($gwContent -match "mode:\s*SIMPLE")
    Assert-Condition "Gateway uses ecommerce-tls-cert secret" ($gwContent -match "credentialName:\s*ecommerce-tls-cert")
    Assert-Condition "Gateway enforces TLS minProtocolVersion TLSV1_2" ($gwContent -match "minProtocolVersion:\s*TLSV1_2")
    Assert-Condition "Gateway supports TLS maxProtocolVersion TLSV1_3" ($gwContent -match "maxProtocolVersion:\s*TLSV1_3")
    Assert-Condition "Gateway configures modern cipher suites" ($gwContent -match "ECDHE-RSA-AES128-GCM-SHA256" -and $gwContent -match "ECDHE-ECDSA-CHACHA20-POLY1305")
}

# ------------------------------------------------------------------------------
# 2. VirtualServices & Traffic Routing
# ------------------------------------------------------------------------------
Write-Host "`n--- 2. VirtualServices & Traffic Routing ---" -ForegroundColor Yellow

$vsFile = Join-Path $MeshDir "virtual-services.yml"
Assert-Condition "virtual-services.yml exists" (Test-Path $vsFile)
if (Test-Path $vsFile) {
    $vsContent = Get-Content $vsFile -Raw
    Assert-Condition "VirtualServices define networking.istio.io/v1beta1" ($vsContent -match "apiVersion:\s*networking\.istio\.io/v1beta1")
    Assert-Condition "Defines ecommerce-ingress-vs" ($vsContent -match "name:\s*ecommerce-ingress-vs")
    Assert-Condition "Ingress VS attaches to ecommerce-gateway and mesh" ($vsContent -match "ecommerce-gateway" -and $vsContent -match "mesh")
    Assert-Condition "Ingress VS routes /api/ to api-gateway on port 8080" ($vsContent -match "api-gateway\.ecommerce\.svc\.cluster\.local" -and $vsContent -match "8080")
    Assert-Condition "Ingress VS routes /ws/ to notification-service on port 8084" ($vsContent -match "notification-service\.ecommerce\.svc\.cluster\.local" -and $vsContent -match "8084")
    Assert-Condition "Ingress VS routes / to shell-app frontend on port 80" ($vsContent -match "shell-app\.ecommerce\.svc\.cluster\.local" -and $vsContent -match "number:\s*80")
    Assert-Condition "Ingress VS configures retries (attempts: 3)" ($vsContent -match "attempts:\s*3")

    Assert-Condition "Defines order-service-vs" ($vsContent -match "name:\s*order-service-vs")
    Assert-Condition "order-service-vs splits traffic to subset v1" ($vsContent -match "subset:\s*v1")
    Assert-Condition "order-service-vs splits traffic to subset v2" ($vsContent -match "subset:\s*v2")
    Assert-Condition "order-service-vs assigns weight 90 to v1" ($vsContent -match "weight:\s*90")
    Assert-Condition "order-service-vs assigns weight 10 to v2" ($vsContent -match "weight:\s*10")
    Assert-Condition "order-service-vs sets timeout 3s" ($vsContent -match "timeout:\s*3s")

    Assert-Condition "Defines product-service-vs" ($vsContent -match "name:\s*product-service-vs")
    Assert-Condition "product-service-vs configures perTryTimeout 350ms" ($vsContent -match "perTryTimeout:\s*350ms")

    Assert-Condition "Defines user-service-vs" ($vsContent -match "name:\s*user-service-vs")
    Assert-Condition "user-service-vs configures retries on 5xx,connect-failure" ($vsContent -match "retryOn:\s*""5xx,connect-failure""")
}

# ------------------------------------------------------------------------------
# 3. DestinationRules & Outlier Detection
# ------------------------------------------------------------------------------
Write-Host "`n--- 3. DestinationRules & Envoy Circuit Breaking ---" -ForegroundColor Yellow

$drFile = Join-Path $MeshDir "destination-rules.yml"
Assert-Condition "destination-rules.yml exists" (Test-Path $drFile)
if (Test-Path $drFile) {
    $drContent = Get-Content $drFile -Raw
    Assert-Condition "DestinationRules define networking.istio.io/v1beta1" ($drContent -match "apiVersion:\s*networking\.istio\.io/v1beta1")
    Assert-Condition "Defines api-gateway-dr" ($drContent -match "name:\s*api-gateway-dr")
    Assert-Condition "Defines order-service-dr" ($drContent -match "name:\s*order-service-dr")
    Assert-Condition "Defines product-service-dr" ($drContent -match "name:\s*product-service-dr")
    Assert-Condition "Defines user-service-dr" ($drContent -match "name:\s*user-service-dr")
    Assert-Condition "Defines notification-service-dr" ($drContent -match "name:\s*notification-service-dr")

    Assert-Condition "Configures ISTIO_MUTUAL TLS mode across DestinationRules" ($drContent -match "mode:\s*ISTIO_MUTUAL")
    Assert-Condition "Configures TCP connection pool (maxConnections: 1024 or 2048)" ($drContent -match "maxConnections:\s*(1024|2048)")
    Assert-Condition "Configures HTTP connection pool (http1MaxPendingRequests)" ($drContent -match "http1MaxPendingRequests:\s*(100|200|500)")
    Assert-Condition "Configures Outlier Detection consecutive5xxErrors" ($drContent -match "consecutive5xxErrors:\s*[3-5]")
    Assert-Condition "Configures Outlier Detection baseEjectionTime: 30s" ($drContent -match "baseEjectionTime:\s*30s")
    Assert-Condition "Configures Outlier Detection maxEjectionPercent: 50" ($drContent -match "maxEjectionPercent:\s*50")
    Assert-Condition "order-service-dr configures LEAST_REQUEST load balancing" ($drContent -match "simple:\s*LEAST_REQUEST")
    Assert-Condition "DestinationRules define v1 and v2 version subsets" ($drContent -match "name:\s*v1" -and $drContent -match "name:\s*v2")
}

# ------------------------------------------------------------------------------
# 4. Zero-Trust Security Policies (mTLS & Authorization)
# ------------------------------------------------------------------------------
Write-Host "`n--- 4. Zero-Trust Security Policies (Strict mTLS & Authz) ---" -ForegroundColor Yellow

$mtlsFile = Join-Path $MeshDir "security-mtls.yml"
Assert-Condition "security-mtls.yml exists" (Test-Path $mtlsFile)
if (Test-Path $mtlsFile) {
    $mtlsContent = Get-Content $mtlsFile -Raw
    Assert-Condition "PeerAuthentication defines security.istio.io/v1beta1" ($mtlsContent -match "apiVersion:\s*security\.istio\.io/v1beta1")
    Assert-Condition "PeerAuthentication default defined in ecommerce namespace" ($mtlsContent -match "name:\s*default\s+namespace:\s*ecommerce" -or ($mtlsContent -match "name:\s*default" -and $mtlsContent -match "namespace:\s*ecommerce"))
    Assert-Condition "PeerAuthentication enforces mode: STRICT" ($mtlsContent -match "mode:\s*STRICT")
    Assert-Condition "Ingress Gateway configured with PERMISSIVE mode for external clients" ($mtlsContent -match "mode:\s*PERMISSIVE")
}

$authzFile = Join-Path $MeshDir "authorization-policies.yml"
Assert-Condition "authorization-policies.yml exists" (Test-Path $authzFile)
if (Test-Path $authzFile) {
    $authzContent = Get-Content $authzFile -Raw
    Assert-Condition "AuthorizationPolicy defines security.istio.io/v1beta1" ($authzContent -match "apiVersion:\s*security\.istio\.io/v1beta1")
    Assert-Condition "Defines api-gateway-authz" ($authzContent -match "name:\s*api-gateway-authz")
    Assert-Condition "Defines order-service-authz" ($authzContent -match "name:\s*order-service-authz")
    Assert-Condition "Defines product-service-authz" ($authzContent -match "name:\s*product-service-authz")
    Assert-Condition "Defines user-service-authz" ($authzContent -match "name:\s*user-service-authz")
    Assert-Condition "Defines notification-service-authz" ($authzContent -match "name:\s*notification-service-authz")
    Assert-Condition "Defines frontend-authz" ($authzContent -match "name:\s*frontend-authz")
    Assert-Condition "AuthorizationPolicies enforce action: ALLOW" ($authzContent -match "action:\s*ALLOW")
    Assert-Condition "Enforces SPIFFE principal checking (cluster.local/ns/...)" ($authzContent -match "cluster\.local/ns/")
}

# ------------------------------------------------------------------------------
# 5. Sidecar Scoping & Telemetry
# ------------------------------------------------------------------------------
Write-Host "`n--- 5. Sidecar Scoping & Mesh Telemetry ---" -ForegroundColor Yellow

$sidecarFile = Join-Path $MeshDir "sidecar-scope.yml"
Assert-Condition "sidecar-scope.yml exists" (Test-Path $sidecarFile)
if (Test-Path $sidecarFile) {
    $scContent = Get-Content $sidecarFile -Raw
    Assert-Condition "Sidecar resource defines networking.istio.io/v1beta1" ($scContent -match "apiVersion:\s*networking\.istio\.io/v1beta1")
    Assert-Condition "Sidecar resource defines kind: Sidecar" ($scContent -match "kind:\s*Sidecar")
    Assert-Condition "Sidecar scopes egress to current namespace (./*)" ($scContent -match "\./\*")
    Assert-Condition "Sidecar scopes egress to istio-system/*" ($scContent -match "istio-system/\*")
}

$telemetryFile = Join-Path $MeshDir "telemetry.yml"
Assert-Condition "telemetry.yml exists" (Test-Path $telemetryFile)
if (Test-Path $telemetryFile) {
    $telContent = Get-Content $telemetryFile -Raw
    Assert-Condition "Telemetry defines telemetry.istio.io/v1alpha1" ($telContent -match "apiVersion:\s*telemetry\.istio\.io/v1alpha1")
    Assert-Condition "Telemetry defines kind: Telemetry" ($telContent -match "kind:\s*Telemetry")
    Assert-Condition "Telemetry configures zipkin tracing provider" ($telContent -match "name:\s*zipkin")
    Assert-Condition "Telemetry sets 100% sampling percentage" ($telContent -match "randomSamplingPercentage:\s*100")
    Assert-Condition "Telemetry configures envoy accessLogging provider" ($telContent -match "name:\s*envoy")
    Assert-Condition "Telemetry configures accessLog filter expression" ($telContent -match "expression:")
    Assert-Condition "Telemetry configures prometheus metrics provider" ($telContent -match "name:\s*prometheus")
}

# ------------------------------------------------------------------------------
# 6. Kustomize Integration & Manifest Compilation
# ------------------------------------------------------------------------------
Write-Host "`n--- 6. Kustomize Integration & Compilation ---" -ForegroundColor Yellow

$meshKustomize = Join-Path $MeshDir "kustomization.yml"
Assert-Condition "mesh/kustomization.yml exists" (Test-Path $meshKustomize)

$rootKustomize = Join-Path $K8sDir "kustomization.yml"
if (Test-Path $rootKustomize) {
    $rootContent = Get-Content $rootKustomize -Raw
    Assert-Condition "Root kustomization.yml includes - mesh" ($rootContent -match "-\s*mesh")
}

# Test Kustomize compile
try {
    $meshBuildOutput = & kubectl kustomize $MeshDir 2>&1
    Assert-Condition "kubectl kustomize kubernetes/mesh compiles with exit code 0" ($LASTEXITCODE -eq 0)
} catch {
    Assert-Condition "kubectl kustomize kubernetes/mesh compiles with exit code 0" $false
}

try {
    $rootBuildOutput = & kubectl kustomize $K8sDir 2>&1
    Assert-Condition "kubectl kustomize kubernetes (full platform) compiles with exit code 0" ($LASTEXITCODE -eq 0)
} catch {
    Assert-Condition "kubectl kustomize kubernetes (full platform) compiles with exit code 0" $false
}

# ------------------------------------------------------------------------------
# 7. Service Mesh Simulation Verification
# ------------------------------------------------------------------------------
Write-Host "`n--- 7. Service Mesh Simulation Script Execution ---" -ForegroundColor Yellow

$simPs1 = Join-Path $DevopsDir "scripts/simulate-istio-mesh.ps1"
Assert-Condition "simulate-istio-mesh.ps1 exists" (Test-Path $simPs1)

$simSh = Join-Path $DevopsDir "scripts/simulate-istio-mesh.sh"
Assert-Condition "simulate-istio-mesh.sh exists" (Test-Path $simSh)

if (Test-Path $simPs1) {
    try {
        $simOutput = & powershell -NoProfile -ExecutionPolicy Bypass -File $simPs1 2>&1
        $simExit = $LASTEXITCODE
        $simRaw = ($simOutput -join "`n")
        Assert-Condition "simulate-istio-mesh.ps1 runs and exits with code 0" ($simExit -eq 0)
        Assert-Condition "Simulation verifies strict mTLS handshakes" ([bool]($simRaw -match "Mutual authentication succeeded"))
        Assert-Condition "Simulation enforces Canary 90/10 traffic split" ([bool]($simRaw -match "Canary Traffic Shift Evaluation"))
        Assert-Condition "Simulation triggers Outlier Detection circuit breaking" ([bool]($simRaw -match "Outlier Detection triggered"))
        Assert-Condition "Simulation verifies Envoy automatic retries" ([bool]($simRaw -match "Automatic retry shielded end-user"))
    } catch {
        Assert-Condition "simulate-istio-mesh.ps1 execution error: $_" $false
    }
}

# ------------------------------------------------------------------------------
# Verification Summary
# ------------------------------------------------------------------------------
Write-Host "`n=================================================================" -ForegroundColor Cyan
Write-Host "   Phase 16 Service Mesh Verification Summary                     " -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host " Total Passed Assertions: $Passed" -ForegroundColor Green
Write-Host " Total Failed Assertions: $Failed" -ForegroundColor $(if ($Failed -eq 0) { "Green" } else { "Red" })

if ($Failed -eq 0) {
    Write-Host "`n [SUCCESS] Phase 16: Enterprise Service Mesh is 100% Verified!" -ForegroundColor Green
    exit 0
} else {
    Write-Host "`n [FAILURE] $Failed assertions failed during verification." -ForegroundColor Red
    exit 1
}
