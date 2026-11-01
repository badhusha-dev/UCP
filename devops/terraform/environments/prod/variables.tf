variable "aws_region" {
  type        = string
  default     = "us-east-1"
  description = "AWS production deployment region"
}

variable "project_name" {
  type        = string
  default     = "universal-commerce"
  description = "Project identifier"
}

variable "environment" {
  type        = string
  default     = "prod"
  description = "Environment identifier"
}

variable "vpc_cidr" {
  type        = string
  default     = "10.100.0.0/16"
  description = "Production VPC IP space"
}

variable "azs" {
  type        = list(string)
  default     = ["us-east-1a", "us-east-1b", "us-east-1c"]
  description = "Availability Zones to span"
}

variable "public_subnet_cidrs" {
  type        = list(string)
  default     = ["10.100.1.0/24", "10.100.2.0/24", "10.100.3.0/24"]
  description = "Public subnet CIDR blocks for load balancers and NAT Gateways"
}

variable "private_subnet_cidrs" {
  type        = list(string)
  default     = ["10.100.11.0/24", "10.100.12.0/24", "10.100.13.0/24"]
  description = "Private subnet CIDR blocks for EKS worker nodes and MSK"
}

variable "database_subnet_cidrs" {
  type        = list(string)
  default     = ["10.100.21.0/24", "10.100.22.0/24", "10.100.23.0/24"]
  description = "Isolated database subnet CIDR blocks for RDS and ElastiCache"
}

variable "cluster_version" {
  type        = string
  default     = "1.29"
  description = "Kubernetes version for Amazon EKS"
}

variable "admin_whitelist_cidrs" {
  type        = list(string)
  default     = ["10.100.0.0/16"]
  description = "CIDR blocks allowed to access the EKS API endpoint"
}

variable "db_password" {
  type        = string
  sensitive   = true
  description = "Production PostgreSQL administrator password"
}

variable "redis_auth_token" {
  type        = string
  sensitive   = true
  description = "Production Redis TLS authentication token (minimum 16 alphanumeric characters)"
}
