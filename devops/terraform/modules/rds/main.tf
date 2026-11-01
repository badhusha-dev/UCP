# ==============================================================================
# Amazon RDS PostgreSQL Module - Universal Commerce Platform
# High-Availability Multi-AZ PostgreSQL with GP3, KMS & Performance Insights
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

# ── RDS DB Subnet Group ───────────────────────────────────────────────────────
resource "aws_db_subnet_group" "main" {
  name        = "${var.environment}-${var.project_name}-db-subnet-group"
  description = "Subnet group for ${var.project_name} PostgreSQL database tier"
  subnet_ids  = var.database_subnet_ids

  tags = merge(
    var.tags,
    {
      Name = "${var.environment}-${var.project_name}-db-subnet-group"
      Tier = "Data"
    }
  )
}

# ── RDS Parameter Group (PostgreSQL 15 Tuning) ───────────────────────────────
resource "aws_db_parameter_group" "main" {
  name        = "${var.environment}-${var.project_name}-pg15-params"
  family      = "postgres15"
  description = "PostgreSQL 15 custom parameter group for ${var.project_name}"

  parameter {
    name  = "rds.force_ssl"
    value = "1"
  }

  parameter {
    name  = "shared_preload_libraries"
    value = "pg_stat_statements"
  }

  parameter {
    name  = "log_connections"
    value = "1"
  }

  parameter {
    name  = "log_disconnections"
    value = "1"
  }

  parameter {
    name  = "log_min_duration_statement"
    value = "500"
  }

  tags = var.tags
}

# ── RDS PostgreSQL Primary Database Instance ─────────────────────────────────
resource "aws_db_instance" "main" {
  identifier = "${var.environment}-${var.project_name}-postgres"

  engine               = "postgres"
  engine_version       = var.engine_version
  instance_class       = var.instance_class
  allocated_storage    = var.allocated_storage
  max_allocated_storage = var.max_allocated_storage
  storage_type         = "gp3"
  storage_encrypted    = true
  kms_key_id           = var.kms_key_arn

  db_name  = var.db_name
  username = var.db_username
  password = var.db_password
  port     = 5432

  vpc_security_group_ids = [var.rds_security_group_id]
  db_subnet_group_name   = aws_db_subnet_group.main.name
  parameter_group_name   = aws_db_parameter_group.main.name

  multi_az            = var.multi_az
  publicly_accessible = false

  backup_retention_period   = var.backup_retention_period
  backup_window             = "03:00-04:00"
  maintenance_window        = "Sun:04:30-Sun:05:30"
  auto_minor_version_upgrade = true
  copy_tags_to_snapshot     = true

  deletion_protection       = var.deletion_protection
  skip_final_snapshot       = var.skip_final_snapshot
  final_snapshot_identifier = "${var.environment}-${var.project_name}-postgres-final-${formatdate("YYYYMMDDhhmmss", timestamp())}"

  performance_insights_enabled          = var.performance_insights_enabled
  performance_insights_kms_key_id       = var.performance_insights_enabled ? var.kms_key_arn : null
  performance_insights_retention_period = var.performance_insights_enabled ? 7 : null

  enabled_cloudwatch_logs_exports = ["postgresql", "upgrade"]

  lifecycle {
    ignore_changes = [
      final_snapshot_identifier
    ]
  }

  tags = merge(
    var.tags,
    {
      Name        = "${var.environment}-${var.project_name}-postgres"
      Tier        = "Data"
      Engine      = "PostgreSQL-${var.engine_version}"
      HighAvailability = var.multi_az ? "Multi-AZ" : "Single-AZ"
    }
  )
}
