# ==============================================================================
# Production Environment - Universal Commerce Platform
# High-Availability Multi-AZ Resilience, Managed EKS, RDS, MSK & Redis
# ==============================================================================

terraform {
  required_version = ">= 1.5.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.40"
    }
    tls = {
      source  = "hashicorp/tls"
      version = "~> 4.0"
    }
  }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = var.project_name
      Environment = var.environment
      ManagedBy   = "Terraform"
      Compliance  = "PCI-DSS-SOC2"
      Repository  = "https://github.com/universal-commerce/ecommerce-platform"
    }
  }
}

locals {
  cluster_name = "${var.environment}-${var.project_name}-eks"
  common_tags = {
    Project     = var.project_name
    Environment = var.environment
    ManagedBy   = "Terraform"
    Compliance  = "PCI-DSS-SOC2"
  }
}

# ── 1. VPC Networking Tier (Multi-AZ with Dedicated NAT Gateways) ────────────
module "vpc" {
  source = "../../modules/vpc"

  project_name          = var.project_name
  environment           = var.environment
  cluster_name          = local.cluster_name
  vpc_cidr              = var.vpc_cidr
  azs                   = var.azs
  public_subnet_cidrs   = var.public_subnet_cidrs
  private_subnet_cidrs  = var.private_subnet_cidrs
  database_subnet_cidrs = var.database_subnet_cidrs
  single_nat_gateway    = false # Production HA: 1 NAT Gateway per AZ (3 Total)

  tags = local.common_tags
}

# ── 2. Security & Envelope Encryption ─────────────────────────────────────────
module "security" {
  source = "../../modules/security"

  project_name = var.project_name
  environment  = var.environment
  cluster_name = local.cluster_name
  vpc_id       = module.vpc.vpc_id
  vpc_cidr     = module.vpc.vpc_cidr

  tags = local.common_tags
}

# ── 3. Amazon EKS Kubernetes Cluster ──────────────────────────────────────────
module "eks" {
  source = "../../modules/eks"

  cluster_name           = local.cluster_name
  cluster_version        = var.cluster_version
  environment            = var.environment
  subnet_ids             = module.vpc.private_subnet_ids
  node_security_group_id = module.security.eks_nodes_security_group_id
  kms_key_arn            = module.security.kms_key_arn
  log_retention_days     = 90
  endpoint_public_access = false # In prod, restrict to private or VPN/bastion
  public_access_cidrs    = var.admin_whitelist_cidrs

  on_demand_instance_types = ["m6i.xlarge", "m5.xlarge"]
  on_demand_scaling = {
    desired_size = 3
    min_size     = 3
    max_size     = 6
  }

  enable_spot_nodes   = true
  spot_instance_types = ["c6i.2xlarge", "c5.2xlarge", "m6i.2xlarge"]
  spot_scaling = {
    desired_size = 4
    min_size     = 2
    max_size     = 12
  }

  tags = local.common_tags
}

# ── 4. Amazon RDS PostgreSQL 15 (Multi-AZ Primary + Standby) ─────────────────
module "rds" {
  source = "../../modules/rds"

  project_name          = var.project_name
  environment           = var.environment
  database_subnet_ids   = module.vpc.database_subnet_ids
  rds_security_group_id = module.security.rds_security_group_id
  kms_key_arn           = module.security.kms_key_arn

  instance_class        = "db.r6g.xlarge"
  allocated_storage     = 100
  max_allocated_storage = 500
  db_name               = "ecommerce_db"
  db_username           = "postgres_admin"
  db_password           = var.db_password
  multi_az              = true # Production HA Multi-AZ
  deletion_protection   = true
  skip_final_snapshot   = false
  backup_retention_period = 30
  performance_insights_enabled = true

  tags = local.common_tags
}

# ── 5. Amazon ElastiCache Redis (3-Node Multi-AZ Cluster with Failover) ─────
module "elasticache" {
  source = "../../modules/elasticache"

  project_name            = var.project_name
  environment             = var.environment
  database_subnet_ids     = module.vpc.database_subnet_ids
  redis_security_group_id = module.security.redis_security_group_id
  kms_key_arn             = module.security.kms_key_arn

  node_type                  = "cache.r6g.large"
  num_cache_clusters         = 3
  automatic_failover_enabled = true
  multi_az_enabled           = true
  transit_encryption_enabled = true
  auth_token                 = var.redis_auth_token
  snapshot_retention_limit   = 7

  tags = local.common_tags
}

# ── 6. Amazon MSK Kafka (Dedicated 3-Broker Production Cluster) ───────────────
module "msk" {
  source = "../../modules/msk"

  project_name          = var.project_name
  environment           = var.environment
  subnet_ids            = module.vpc.private_subnet_ids
  msk_security_group_id = module.security.msk_security_group_id
  kms_key_arn           = module.security.kms_key_arn

  kafka_version            = "3.6.0"
  number_of_broker_nodes   = 3
  broker_instance_type     = "kafka.m5.large"
  ebs_volume_size          = 250
  client_broker_encryption = "TLS" # Strict mTLS/TLS in production
  log_retention_days       = 90

  tags = local.common_tags
}
