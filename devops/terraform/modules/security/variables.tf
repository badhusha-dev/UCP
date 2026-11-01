variable "project_name" {
  type        = string
  description = "Project name identifier used in resource naming"
}

variable "environment" {
  type        = string
  description = "Deployment environment (e.g. dev, prod)"
}

variable "vpc_id" {
  type        = string
  description = "VPC ID where security groups will be created"
}

variable "vpc_cidr" {
  type        = string
  description = "Primary CIDR block of the VPC"
}

variable "cluster_name" {
  type        = string
  description = "EKS cluster name for tagging security groups"
}

variable "tags" {
  type        = map(string)
  default     = {}
  description = "Common tags applied to all security resources"
}
