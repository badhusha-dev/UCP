variable "project_name" {
  description = "Project identifier for naming and resource tagging"
  type        = string
  default     = "universal-commerce"
}

variable "environment" {
  description = "Deployment environment (dev, staging, prod)"
  type        = string
}

variable "cluster_name" {
  description = "EKS cluster name for subnet discovery tagging"
  type        = string
}

variable "vpc_cidr" {
  description = "CIDR block for the primary VPC"
  type        = string
  default     = "10.0.0.0/16"
}

variable "availability_zones" {
  description = "List of Availability Zones for multi-AZ topology"
  type        = list(string)
}

variable "public_subnet_cidrs" {
  description = "CIDR blocks for public subnets"
  type        = list(string)
}

variable "private_subnet_cidrs" {
  description = "CIDR blocks for private compute subnets"
  type        = list(string)
}

variable "database_subnet_cidrs" {
  description = "CIDR blocks for isolated database subnets"
  type        = list(string)
}

variable "single_nat_gateway" {
  description = "Set true for dev to reduce cost by using a single NAT gateway"
  type        = bool
  default     = false
}

variable "tags" {
  description = "Resource tags applied to all resources"
  type        = map(string)
  default     = {}
}
