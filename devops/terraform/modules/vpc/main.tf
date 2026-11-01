# ==============================================================================
# Amazon VPC Module - Universal Commerce Platform
# High-Availability Multi-AZ Architecture (3 AZs: Public, Private, Database)
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

# ── Primary VPC ──────────────────────────────────────────────────────────────
resource "aws_vpc" "this" {
  cidr_block           = var.vpc_cidr
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = merge(
    var.tags,
    {
      Name                                        = "${var.environment}-${var.project_name}-vpc"
      "kubernetes.io/cluster/${var.cluster_name}" = "shared"
    }
  )
}

# ── Internet Gateway ─────────────────────────────────────────────────────────
resource "aws_internet_gateway" "this" {
  vpc_id = aws_vpc.this.id

  tags = merge(
    var.tags,
    {
      Name = "${var.environment}-${var.project_name}-igw"
    }
  )
}

# ── Public Subnets (ALB & NAT Gateways) ───────────────────────────────────────
resource "aws_subnet" "public" {
  count                   = length(var.public_subnet_cidrs)
  vpc_id                  = aws_vpc.this.id
  cidr_block              = var.public_subnet_cidrs[count.index]
  availability_zone       = var.availability_zones[count.index]
  map_public_ip_on_launch = true

  tags = merge(
    var.tags,
    {
      Name                                        = "${var.environment}-${var.project_name}-public-${var.availability_zones[count.index]}"
      "kubernetes.io/role/elb"                    = "1"
      "kubernetes.io/cluster/${var.cluster_name}" = "shared"
      Tier                                        = "Public"
    }
  )
}

# ── Elastic IPs for NAT Gateways ─────────────────────────────────────────────
resource "aws_eip" "nat" {
  count  = var.single_nat_gateway ? 1 : length(var.public_subnet_cidrs)
  domain = "vpc"

  tags = merge(
    var.tags,
    {
      Name = "${var.environment}-${var.project_name}-nat-eip-${count.index + 1}"
    }
  )

  depends_on = [aws_internet_gateway.this]
}

# ── NAT Gateways (Multi-AZ in Prod, Single in Dev) ────────────────────────────
resource "aws_nat_gateway" "this" {
  count         = var.single_nat_gateway ? 1 : length(var.public_subnet_cidrs)
  allocation_id = aws_eip.nat[count.index].id
  subnet_id     = aws_subnet.public[count.index].id

  tags = merge(
    var.tags,
    {
      Name = "${var.environment}-${var.project_name}-nat-${count.index + 1}"
    }
  )

  depends_on = [aws_internet_gateway.this]
}

# ── Private Compute Subnets (EKS Worker Nodes & Pods) ────────────────────────
resource "aws_subnet" "private" {
  count             = length(var.private_subnet_cidrs)
  vpc_id            = aws_vpc.this.id
  cidr_block        = var.private_subnet_cidrs[count.index]
  availability_zone = var.availability_zones[count.index]

  tags = merge(
    var.tags,
    {
      Name                                        = "${var.environment}-${var.project_name}-private-${var.availability_zones[count.index]}"
      "kubernetes.io/role/internal-elb"           = "1"
      "kubernetes.io/cluster/${var.cluster_name}" = "owned"
      "karpenter.sh/discovery"                    = var.cluster_name
      Tier                                        = "Private"
    }
  )
}

# ── Isolated Database Subnets (RDS PostgreSQL, ElastiCache Redis, MSK Kafka) ─
resource "aws_subnet" "database" {
  count             = length(var.database_subnet_cidrs)
  vpc_id            = aws_vpc.this.id
  cidr_block        = var.database_subnet_cidrs[count.index]
  availability_zone = var.availability_zones[count.index]

  tags = merge(
    var.tags,
    {
      Name = "${var.environment}-${var.project_name}-db-${var.availability_zones[count.index]}"
      Tier = "Database"
    }
  )
}

# ── Database Subnet Group for RDS ────────────────────────────────────────────
resource "aws_db_subnet_group" "this" {
  name        = "${var.environment}-${var.project_name}-db-subnet-group"
  description = "Subnet group for Multi-AZ PostgreSQL RDS cluster"
  subnet_ids  = aws_subnet.database[*].id

  tags = merge(
    var.tags,
    {
      Name = "${var.environment}-${var.project_name}-db-subnet-group"
    }
  )
}

# ── Route Tables ─────────────────────────────────────────────────────────────

# Public Route Table (Direct Internet Route)
resource "aws_route_table" "public" {
  vpc_id = aws_vpc.this.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.this.id
  }

  tags = merge(
    var.tags,
    {
      Name = "${var.environment}-${var.project_name}-public-rt"
    }
  )
}

resource "aws_route_table_association" "public" {
  count          = length(var.public_subnet_cidrs)
  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

# Private Route Tables (Egress via NAT Gateways)
resource "aws_route_table" "private" {
  count  = length(var.private_subnet_cidrs)
  vpc_id = aws_vpc.this.id

  route {
    cidr_block     = "0.0.0.0/0"
    nat_gateway_id = var.single_nat_gateway ? aws_nat_gateway.this[0].id : aws_nat_gateway.this[count.index].id
  }

  tags = merge(
    var.tags,
    {
      Name = "${var.environment}-${var.project_name}-private-rt-${var.availability_zones[count.index]}"
    }
  )
}

resource "aws_route_table_association" "private" {
  count          = length(var.private_subnet_cidrs)
  subnet_id      = aws_subnet.private[count.index].id
  route_table_id = aws_route_table.private[count.index].id
}

# Database Route Table (Completely Isolated - No Internet Route)
resource "aws_route_table" "database" {
  vpc_id = aws_vpc.this.id

  tags = merge(
    var.tags,
    {
      Name = "${var.environment}-${var.project_name}-db-rt"
    }
  )
}

resource "aws_route_table_association" "database" {
  count          = length(var.database_subnet_cidrs)
  subnet_id      = aws_subnet.database[count.index].id
  route_table_id = aws_route_table.database.id
}
