# ==============================================================================
# Amazon ElastiCache Redis Module - Universal Commerce Platform
# High-Availability In-Memory Caching & Session Store with KMS Encryption
# ==============================================================================

terraform {
  required_version = ">= 1.5.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.40"
    }
  }
}

# ── ElastiCache Subnet Group ──────────────────────────────────────────────────
resource "aws_elasticache_subnet_group" "main" {
  name        = "${var.environment}-${var.project_name}-redis-subnet-group"
  description = "Subnet group for ${var.project_name} Redis replication group"
  subnet_ids  = var.database_subnet_ids

  tags = merge(
    var.tags,
    {
      Name = "${var.environment}-${var.project_name}-redis-subnet-group"
      Tier = "Cache"
    }
  )
}

# ── ElastiCache Parameter Group (Redis 7.x) ───────────────────────────────────
resource "aws_elasticache_parameter_group" "main" {
  name        = "${var.environment}-${var.project_name}-redis7-params"
  family      = "redis7"
  description = "Custom Redis 7.x parameter group for ${var.project_name}"

  parameter {
    name  = "maxmemory-policy"
    value = "volatile-lru"
  }

  parameter {
    name  = "timeout"
    value = "300"
  }

  parameter {
    name  = "tcp-keepalive"
    value = "300"
  }

  tags = var.tags
}

# ── Redis Replication Group ───────────────────────────────────────────────────
resource "aws_elasticache_replication_group" "main" {
  replication_group_id = "${var.environment}-${var.project_name}-redis"
  description          = "Redis replication group for ${var.project_name} (${var.environment})"

  node_type                  = var.node_type
  num_cache_clusters         = var.num_cache_clusters
  port                       = 6379
  parameter_group_name       = aws_elasticache_parameter_group.main.name
  subnet_group_name          = aws_elasticache_subnet_group.main.name
  security_group_ids         = [var.redis_security_group_id]

  automatic_failover_enabled = var.automatic_failover_enabled
  multi_az_enabled           = var.multi_az_enabled

  at_rest_encryption_enabled = true
  kms_key_id                 = var.kms_key_arn
  transit_encryption_enabled = var.transit_encryption_enabled
  auth_token                 = var.transit_encryption_enabled ? var.auth_token : null

  snapshot_retention_limit   = var.snapshot_retention_limit
  snapshot_window            = "02:00-03:00"
  maintenance_window         = "sun:03:30-sun:04:30"
  auto_minor_version_upgrade = true

  tags = merge(
    var.tags,
    {
      Name        = "${var.environment}-${var.project_name}-redis"
      Tier        = "Cache"
      Environment = var.environment
    }
  )
}
