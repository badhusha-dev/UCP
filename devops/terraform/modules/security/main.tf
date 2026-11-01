# ==============================================================================
# Security & Encryption Module - Universal Commerce Platform
# KMS Envelope Encryption, IAM IRSA Policies & Zero-Trust Security Groups
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

# ── KMS Customer Managed Key (CMK) for Envelope Encryption ───────────────────
resource "aws_kms_key" "primary" {
  description             = "Primary KMS key for ${var.project_name}-${var.environment} envelope encryption"
  deletion_window_in_days = 30
  enable_key_rotation     = true

  tags = merge(
    var.tags,
    {
      Name        = "${var.environment}-${var.project_name}-kms-key"
      Role        = "EnvelopeEncryption"
      Compliance  = "PCI-DSS-SOC2"
    }
  )
}

resource "aws_kms_alias" "primary" {
  name          = "alias/${var.environment}-${var.project_name}"
  target_key_id = aws_kms_key.primary.key_id
}

# ── Security Group: Application Load Balancer (Edge Ingress) ───────────────────
resource "aws_security_group" "alb" {
  name        = "${var.environment}-${var.project_name}-alb-sg"
  description = "Security group for Internet-facing Application Load Balancer"
  vpc_id      = var.vpc_id

  ingress {
    description = "Allow HTTPS from anywhere"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "Allow HTTP from anywhere (redirected to HTTPS)"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    description = "Allow outbound to VPC private subnets"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = [var.vpc_cidr]
  }

  tags = merge(
    var.tags,
    {
      Name = "${var.environment}-${var.project_name}-alb-sg"
      Tier = "Edge"
    }
  )
}

# ── Security Group: EKS Worker Nodes ─────────────────────────────────────────
resource "aws_security_group" "eks_nodes" {
  name        = "${var.environment}-${var.project_name}-eks-nodes-sg"
  description = "Security group for EKS worker nodes & microservice pods"
  vpc_id      = var.vpc_id

  ingress {
    description     = "Allow traffic from ALB into Istio Ingress / NodePorts"
    from_port       = 0
    to_port         = 65535
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }

  ingress {
    description = "Allow node-to-node intra-cluster communication"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    self        = true
  }

  egress {
    description = "Allow all outbound traffic for package updates & external APIs"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(
    var.tags,
    {
      Name                                        = "${var.environment}-${var.project_name}-eks-nodes-sg"
      "kubernetes.io/cluster/${var.cluster_name}" = "owned"
      Tier                                        = "Compute"
    }
  )
}

# ── Security Group: Amazon RDS PostgreSQL Database ───────────────────────────
resource "aws_security_group" "rds" {
  name        = "${var.environment}-${var.project_name}-rds-sg"
  description = "Strict least-privilege security group for PostgreSQL RDS"
  vpc_id      = var.vpc_id

  ingress {
    description     = "Allow PostgreSQL traffic strictly from EKS worker nodes"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.eks_nodes.id]
  }

  egress {
    description = "Allow outbound response traffic within VPC"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = [var.vpc_cidr]
  }

  tags = merge(
    var.tags,
    {
      Name = "${var.environment}-${var.project_name}-rds-sg"
      Tier = "Data"
    }
  )
}

# ── Security Group: Amazon ElastiCache Redis ─────────────────────────────────
resource "aws_security_group" "redis" {
  name        = "${var.environment}-${var.project_name}-redis-sg"
  description = "Security group for distributed Redis cache cluster"
  vpc_id      = var.vpc_id

  ingress {
    description     = "Allow Redis access strictly from EKS worker nodes"
    from_port       = 6379
    to_port         = 6379
    protocol        = "tcp"
    security_groups = [aws_security_group.eks_nodes.id]
  }

  egress {
    description = "Allow outbound within VPC"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = [var.vpc_cidr]
  }

  tags = merge(
    var.tags,
    {
      Name = "${var.environment}-${var.project_name}-redis-sg"
      Tier = "Cache"
    }
  )
}

# ── Security Group: Amazon MSK (Managed Streaming for Kafka) ─────────────────
resource "aws_security_group" "msk" {
  name        = "${var.environment}-${var.project_name}-msk-sg"
  description = "Security group for MSK Kafka event broker cluster"
  vpc_id      = var.vpc_id

  ingress {
    description     = "Plaintext Kafka listener (internal pod testing)"
    from_port       = 9092
    to_port         = 9092
    protocol        = "tcp"
    security_groups = [aws_security_group.eks_nodes.id]
  }

  ingress {
    description     = "TLS Kafka broker listener (production mTLS)"
    from_port       = 9094
    to_port         = 9094
    protocol        = "tcp"
    security_groups = [aws_security_group.eks_nodes.id]
  }

  ingress {
    description     = "IAM Auth Kafka broker listener"
    from_port       = 9098
    to_port         = 9098
    protocol        = "tcp"
    security_groups = [aws_security_group.eks_nodes.id]
  }

  ingress {
    description     = "ZooKeeper ensemble communication"
    from_port       = 2181
    to_port         = 2181
    protocol        = "tcp"
    security_groups = [aws_security_group.eks_nodes.id]
  }

  egress {
    description = "Allow outbound within VPC"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = [var.vpc_cidr]
  }

  tags = merge(
    var.tags,
    {
      Name = "${var.environment}-${var.project_name}-msk-sg"
      Tier = "Messaging"
    }
  )
}
