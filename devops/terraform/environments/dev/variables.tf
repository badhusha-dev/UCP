variable "aws_region" {
  type        = string
  default     = "us-east-1"
  description = "AWS deployment region"
}

variable "project_name" {
  type        = string
  default     = "universal-commerce"
  description = "Project identifier"
}

variable "environment" {
  type        = string
  default     = "dev"
  description = "Environment name"
}

variable "vpc_cidr" {
  type        = string
  default     = "10.0.0.0/16"
  description = "CIDR block for the VPC"
}

variable "azs" {
  type        = list(string)
  default     = ["us-east-1a", "us-east-1b", "us-east-1c"]
  description = "Availability Zones to span"
}

variable "public_subnet_cidrs" {
  type        = list(string)
  default     = ["10.0.1.0/24", "10.0.2.0/24", "10.0.3.0/24"]
  description = "Public subnet CIDR blocks for load balancers and NAT"
}

variable "private_subnet_cidrs" {
  type        = list(string)
  default     = ["10.0.11.0/24", "10.0.12.0/24", "10.0.13.0/24"]
  description = "Private subnet CIDR blocks for EKS worker nodes and MSK"
}

variable "database_subnet_cidrs" {
  type        = list(string)
  default     = ["10.0.21.0/24", "10.0.22.0/24", "10.0.23.0/24"]
  description = "Isolated database subnet CIDR blocks for RDS and ElastiCache"
}

variable "cluster_version" {
  type        = string
  default     = "1.29"
  description = "Kubernetes version for Amazon EKS"
}

variable "db_password" {
  type        = string
  sensitive   = true
  default     = "DevSecurePass2026!"
  description = "PostgreSQL administrator password"
}
