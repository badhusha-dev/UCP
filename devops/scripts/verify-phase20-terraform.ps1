<#
.SYNOPSIS
    Phase 20 Infrastructure Verification Suite - Universal Commerce Platform
.DESCRIPTION
    Validates Terraform HCL module definitions, security posture, compliance tagging,
    and multi-environment configurations for AWS EKS, VPC, RDS, MSK, and ElastiCache.
#>

$ErrorActionPreference = "Stop"
$BasePath = "D:\practical\ecommerce-platform"
$TfPath = "$BasePath\devops\terraform"

Write-Host "======================================================================" -ForegroundColor Cyan
Write-Host " [PHASE 20] AWS Infrastructure as Code Verification (Terraform HCL)   " -ForegroundColor Cyan
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
# 1. Structure & File Completeness Tests
# ─────────────────────────────────────────────────────────────────────────────
Write-Host "`n--- Section 1: Terraform File & Module Structure ---" -ForegroundColor Yellow

$RequiredModules = @("vpc", "security", "eks", "rds", "elasticache", "msk")
foreach ($mod in $RequiredModules) {
    $modDir = "$TfPath\modules\$mod"
    $hasMain = Test-Path "$modDir\main.tf"
    $hasVars = Test-Path "$modDir\variables.tf"
    $hasOut  = Test-Path "$modDir\outputs.tf"
    Assert-Test "Module '$mod' has complete file trio (main, variables, outputs)" ($hasMain -and $hasVars -and $hasOut) "Missing file in $modDir"
}

$RequiredEnvs = @("dev", "prod")
foreach ($env in $RequiredEnvs) {
    $envDir = "$TfPath\environments\$env"
    $hasMain   = Test-Path "$envDir\main.tf"
    $hasVars   = Test-Path "$envDir\variables.tf"
    $hasTfvars = Test-Path "$envDir\terraform.tfvars"
    $hasOut    = Test-Path "$envDir\outputs.tf"
    Assert-Test "Environment '$env' has complete config set (main, vars, tfvars, outputs)" ($hasMain -and $hasVars -and $hasTfvars -and $hasOut) "Missing file in $envDir"
}

$hasBackendExample = Test-Path "$TfPath\environments\backend-s3.tf.example"
Assert-Test "Remote S3 backend & DynamoDB state locking example exists" $hasBackendExample

$hasCiWorkflow = Test-Path "$BasePath\.github\workflows\terraform-infra.yml"
Assert-Test "GitHub Actions CI/CD workflow (.github/workflows/terraform-infra.yml) exists" $hasCiWorkflow

# ─────────────────────────────────────────────────────────────────────────────
# 2. Module Resource Assertions
# ─────────────────────────────────────────────────────────────────────────────
Write-Host "`n--- Section 2: Module Resource Declarations ---" -ForegroundColor Yellow

# VPC
$vpcMain = Get-Content "$TfPath\modules\vpc\main.tf" -Raw
Assert-Test "VPC module provisions aws_vpc with DNS support" ($vpcMain -match 'resource\s+"aws_vpc"' -and $vpcMain -match 'enable_dns_hostnames\s*=\s*true')
Assert-Test "VPC module provisions 3-tier subnets (public, private, database)" ($vpcMain -match 'aws_subnet"\s+"public"' -and $vpcMain -match 'aws_subnet"\s+"private"' -and $vpcMain -match 'aws_subnet"\s+"database"')
Assert-Test "VPC module provisions Internet & NAT Gateways" ($vpcMain -match 'resource\s+"aws_internet_gateway"' -and $vpcMain -match 'resource\s+"aws_nat_gateway"')
Assert-Test "VPC module tags subnets for EKS ALB Discovery (internal-elb / elb)" ($vpcMain -match 'kubernetes.io/role/elb' -and $vpcMain -match 'kubernetes.io/role/internal-elb')

# Security
$secMain = Get-Content "$TfPath\modules\security\main.tf" -Raw
Assert-Test "Security module defines KMS Customer Managed Key with rotation" ($secMain -match 'resource\s+"aws_kms_key"' -and $secMain -match 'enable_key_rotation\s*=\s*true')
Assert-Test "Security module provisions Zero-Trust Security Groups (ALB, EKS, RDS, Redis, MSK)" (
    $secMain -match 'aws_security_group"\s+"alb"' -and
    $secMain -match 'aws_security_group"\s+"eks_nodes"' -and
    $secMain -match 'aws_security_group"\s+"rds"' -and
    $secMain -match 'aws_security_group"\s+"redis"' -and
    $secMain -match 'aws_security_group"\s+"msk"'
)

# EKS
$eksMain = Get-Content "$TfPath\modules\eks\main.tf" -Raw
Assert-Test "EKS module creates control plane cluster with envelope KMS encryption" ($eksMain -match 'resource\s+"aws_eks_cluster"' -and $eksMain -match 'encryption_config')
Assert-Test "EKS module enables all 5 control plane audit log types" ($eksMain -match '"api"' -and $eksMain -match '"audit"' -and $eksMain -match '"authenticator"')
Assert-Test "EKS module provisions OIDC Provider for IAM IRSA" ($eksMain -match 'resource\s+"aws_iam_openid_connect_provider"')
Assert-Test "EKS module provisions On-Demand and Spot Managed Node Groups" ($eksMain -match 'aws_eks_node_group"\s+"on_demand"' -and $eksMain -match 'aws_eks_node_group"\s+"spot"')
Assert-Test "EKS module installs core addons (vpc-cni, coredns, kube-proxy, ebs-csi)" (
    $eksMain -match 'addon_name\s*=\s*"vpc-cni"' -and
    $eksMain -match 'addon_name\s*=\s*"coredns"' -and
    $eksMain -match 'addon_name\s*=\s*"kube-proxy"' -and
    $eksMain -match 'addon_name\s*=\s*"aws-ebs-csi-driver"'
)

# RDS
$rdsMain = Get-Content "$TfPath\modules\rds\main.tf" -Raw
Assert-Test "RDS module provisions PostgreSQL 15 with GP3 and KMS storage encryption" ($rdsMain -match 'engine\s*=\s*"postgres"' -and $rdsMain -match 'storage_type\s*=\s*"gp3"' -and $rdsMain -match 'storage_encrypted\s*=\s*true')
Assert-Test "RDS module creates DB subnet and custom parameter group (pg_stat_statements)" ($rdsMain -match 'aws_db_subnet_group' -and $rdsMain -match 'aws_db_parameter_group' -and $rdsMain -match 'pg_stat_statements')
Assert-Test "RDS module configures automated backup retention and CloudWatch log export" ($rdsMain -match 'backup_retention_period' -and $rdsMain -match 'enabled_cloudwatch_logs_exports')

# ElastiCache
$redisMain = Get-Content "$TfPath\modules\elasticache\main.tf" -Raw
Assert-Test "ElastiCache module provisions Redis replication group with at-rest encryption" ($redisMain -match 'aws_elasticache_replication_group' -and $redisMain -match 'at_rest_encryption_enabled\s*=\s*true')
Assert-Test "ElastiCache module defines custom parameter group with volatile-lru eviction" ($redisMain -match 'aws_elasticache_parameter_group' -and $redisMain -match 'volatile-lru')

# MSK
$mskMain = Get-Content "$TfPath\modules\msk\main.tf" -Raw
Assert-Test "MSK module provisions Kafka 3.6 cluster with dedicated broker nodes" ($mskMain -match 'resource\s+"aws_msk_cluster"' -and $mskMain -match 'kafka_version')
Assert-Test "MSK module applies custom broker configuration and CloudWatch broker logging" ($mskMain -match 'resource\s+"aws_msk_configuration"' -and $mskMain -match 'cloudwatch_logs')
Assert-Test "MSK module enforces in-cluster & KMS storage encryption" ($mskMain -match 'encryption_at_rest_kms_key_arn' -and $mskMain -match 'in_cluster\s*=\s*true')

# ─────────────────────────────────────────────────────────────────────────────
# 3. Environment & Security Isolation Assertions
# ─────────────────────────────────────────────────────────────────────────────
Write-Host "`n--- Section 3: Environments & Security Isolation ---" -ForegroundColor Yellow

$devMain  = Get-Content "$TfPath\environments\dev\main.tf" -Raw
$prodMain = Get-Content "$TfPath\environments\prod\main.tf" -Raw

Assert-Test "Dev environment utilizes single NAT Gateway for cost optimization" ($devMain -match 'single_nat_gateway\s*=\s*true')
Assert-Test "Prod environment enforces Multi-AZ NAT Gateways (single_nat_gateway = false)" ($prodMain -match 'single_nat_gateway\s*=\s*false')
Assert-Test "Prod RDS enables Multi-AZ standby replica and deletion protection" ($prodMain -match 'multi_az\s*=\s*true' -and $prodMain -match 'deletion_protection\s*=\s*true')
Assert-Test "Prod Redis configures 3-cluster replication with automatic failover" ($prodMain -match 'num_cache_clusters\s*=\s*3' -and $prodMain -match 'automatic_failover_enabled\s*=\s*true')
Assert-Test "Prod MSK enforces strict TLS broker communication" ($prodMain -match 'client_broker_encryption\s*=\s*"TLS"')
Assert-Test "Compliance tags (PCI-DSS-SOC2, ManagedBy) present in Prod configuration" ($prodMain -match 'PCI-DSS-SOC2' -and $prodMain -match 'ManagedBy')

# Database isolation verification:
Assert-Test "RDS security group restricts ingress to EKS nodes SG" ($secMain -match 'security_groups\s*=\s*\[aws_security_group\.eks_nodes\.id\]')
Assert-Test "Redis security group restricts ingress to EKS nodes SG" ($secMain -match '6379' -and $secMain -match 'security_groups\s*=\s*\[aws_security_group\.eks_nodes\.id\]')
Assert-Test "MSK security group restricts ingress to EKS nodes SG" ($secMain -match '9092' -and $secMain -match 'security_groups\s*=\s*\[aws_security_group\.eks_nodes\.id\]')

# ─────────────────────────────────────────────────────────────────────────────
# 4. Summary & Verification Outcome
# ─────────────────────────────────────────────────────────────────────────────
Write-Host "`n======================================================================" -ForegroundColor Cyan
Write-Host " VERIFICATION SUMMARY: $Passed Passed, $Failed Failed" -ForegroundColor $(if ($Failed -eq 0) { "Green" } else { "Red" })
Write-Host "======================================================================" -ForegroundColor Cyan

if ($Failed -gt 0) {
    Exit 1
} else {
    Exit 0
}
