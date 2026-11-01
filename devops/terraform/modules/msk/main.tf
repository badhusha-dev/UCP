# ==============================================================================
# Amazon MSK (Managed Streaming for Kafka) Module - Universal Commerce Platform
# High-Throughput Event Streaming, Schema Management & KMS Encryption
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

# ── CloudWatch Log Group for MSK Broker Logs ──────────────────────────────────
resource "aws_cloudwatch_log_group" "msk" {
  name              = "/aws/msk/${var.environment}-${var.project_name}-kafka"
  retention_in_days = var.log_retention_days

  tags = merge(
    var.tags,
    {
      Name = "/aws/msk/${var.environment}-${var.project_name}-kafka"
      Tier = "Observability"
    }
  )
}

# ── MSK Cluster Configuration ────────────────────────────────────────────────
resource "aws_msk_configuration" "main" {
  kafka_versions = [var.kafka_version]
  name           = "${var.environment}-${var.project_name}-msk-config"
  description    = "Optimized Kafka broker configurations for ${var.project_name}"

  server_properties = <<EOF
auto.create.topics.enable=false
default.replication.factor=3
min.insync.replicas=2
num.partitions=6
log.retention.hours=168
message.max.bytes=10485760
replica.fetch.max.bytes=10485760
EOF
}

# ── MSK Kafka Cluster ─────────────────────────────────────────────────────────
resource "aws_msk_cluster" "main" {
  cluster_name           = "${var.environment}-${var.project_name}-kafka"
  kafka_version          = var.kafka_version
  number_of_broker_nodes = var.number_of_broker_nodes

  broker_node_group_info {
    instance_type   = var.broker_instance_type
    client_subnets  = var.subnet_ids
    security_groups = [var.msk_security_group_id]

    storage_info {
      ebs_storage_info {
        volume_size = var.ebs_volume_size
      }
    }
  }

  encryption_info {
    encryption_at_rest_kms_key_arn = var.kms_key_arn

    encryption_in_transit {
      client_broker = var.client_broker_encryption
      in_cluster    = true
    }
  }

  configuration_info {
    arn      = aws_msk_configuration.main.arn
    revision = aws_msk_configuration.main.latest_revision
  }

  logging_info {
    broker_logs {
      cloudwatch_logs {
        enabled   = true
        log_group = aws_cloudwatch_log_group.msk.name
      }
    }
  }

  tags = merge(
    var.tags,
    {
      Name        = "${var.environment}-${var.project_name}-kafka"
      Tier        = "Messaging"
      Environment = var.environment
    }
  )
}
