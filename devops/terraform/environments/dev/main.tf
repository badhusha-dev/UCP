# ==============================================================================
# Development Environment - Universal Commerce Platform
# Cost-Optimized Single-NAT Topology with Multi-AZ Subnets & Spot Workers
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
  }
}

# ── 1. VPC Networking Tier ───────────────────────────────────────────────────
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
  single_nat_gateway    = true # Cost optimization: single NAT for dev

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
  log_retention_days     = 7
  endpoint_public_access = true

  on_demand_instance_types = ["t3.medium"]
  on_demand_scaling = {
    desired_size = 2
    min_size     = 1
    max_size     = 3
  }

  enable_spot_nodes   = true
  spot_instance_types = ["t3.large", "t3a.large"]
  spot_scaling = {
    desired_size = 2
    min_size     = 1
    max_size     = 4
  }

  tags = local.common_tags
}

# ── 4. Amazon RDS PostgreSQL 15 ───────────────────────────────────────────────
module "rds" {
  source = "../../modules/rds"

  project_name          = var.project_name
  environment           = var.environment
  database_subnet_ids   = module.vpc.database_subnet_ids
  rds_security_group_id = module.security.rds_security_group_id
  kms_key_arn           = module.security.kms_key_arn

  instance_class        = "db.t4g.medium"
  allocated_storage     = 20
  max_allocated_storage = 100
  db_name               = "ecommerce_db"
  db_username           = "postgres_admin"
  db_password           = var.db_password
  multi_az              = false # Dev: single instance to reduce cost
  deletion_protection   = false
  skip_final_snapshot   = true

  tags = local.common_tags
}

# ── 5. Amazon ElastiCache Redis ───────────────────────────────────────────────
module "elasticache" {
  source = "../../modules/elasticache"

  project_name            = var.project_name
  environment             = var.environment
  database_subnet_ids     = module.vpc.database_subnet_ids
  redis_security_group_id = module.security.redis_security_group_id
  kms_key_arn             = module.security.kms_key_arn

  node_type                  = "cache.t4g.small"
  num_cache_clusters         = 1
  automatic_failover_enabled = false
  multi_az_enabled           = false
  transit_encryption_enabled = false

  tags = local.common_tags
}

# ── 6. Amazon MSK Kafka ───────────────────────────────────────────────────────
module "msk" {
  source = "../../modules/msk"

  project_name          = var.project_name
  environment           = var.environment
  subnet_ids            = module.vpc.private_subnet_ids
  msk_security_group_id = module.security.msk_security_group_id
  kms_key_arn           = module.security.kms_key_arn

  kafka_version            = "3.6.0"
  number_of_broker_nodes   = 3
  broker_instance_type     = "kafka.t3.small"
  ebs_volume_size          = 50
  client_broker_encryption = "TLS_PLAINTEXT"
  log_retention_days       = 7

  tags = local.common_tags
}
