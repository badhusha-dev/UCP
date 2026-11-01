variable "project_name" {
  type        = string
  description = "Project name identifier"
}

variable "environment" {
  type        = string
  description = "Deployment environment (e.g. dev, prod)"
}

variable "database_subnet_ids" {
  type        = list(string)
  description = "VPC isolated database subnet IDs"
}

variable "rds_security_group_id" {
  type        = string
  description = "Security group ID allowing port 5432 ingress from EKS nodes"
}

variable "kms_key_arn" {
  type        = string
  description = "KMS Customer Managed Key ARN for storage and Performance Insights encryption"
}

variable "engine_version" {
  type        = string
  default     = "15.5"
  description = "PostgreSQL engine version"
}

variable "instance_class" {
  type        = string
  default     = "db.t4g.medium"
  description = "RDS instance compute size"
}

variable "allocated_storage" {
  type        = number
  default     = 20
  description = "Initial allocated storage in GiB"
}

variable "max_allocated_storage" {
  type        = number
  default     = 100
  description = "Maximum storage auto-scaling threshold in GiB"
}

variable "db_name" {
  type        = string
  default     = "ecommerce_db"
  description = "Default database name created upon initialization"
}

variable "db_username" {
  type        = string
  default     = "postgres_admin"
  description = "Master database administrator username"
}

variable "db_password" {
  type        = string
  sensitive   = true
  description = "Master database administrator password"
}

variable "multi_az" {
  type        = bool
  default     = false
  description = "Whether to deploy a standby replica in a secondary AZ for failover"
}

variable "backup_retention_period" {
  type        = number
  default     = 7
  description = "Days of automated backup snapshots to retain"
}

variable "deletion_protection" {
  type        = bool
  default     = false
  description = "If true, database cannot be deleted without manual override"
}

variable "skip_final_snapshot" {
  type        = bool
  default     = true
  description = "Whether to skip final snapshot before database deletion"
}

variable "performance_insights_enabled" {
  type        = bool
  default     = true
  description = "Enable AWS Performance Insights query telemetry"
}

variable "tags" {
  type        = map(string)
  default     = {}
  description = "Common resource tags"
}
