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
  description = "List of VPC subnet IDs for ElastiCache Redis replication group"
}

variable "redis_security_group_id" {
  type        = string
  description = "Security group ID allowing port 6379 ingress from EKS nodes"
}

variable "kms_key_arn" {
  type        = string
  description = "KMS Customer Managed Key ARN for at-rest storage encryption"
}

variable "node_type" {
  type        = string
  default     = "cache.t4g.small"
  description = "ElastiCache node instance type"
}

variable "num_cache_clusters" {
  type        = number
  default     = 1
  description = "Number of cache clusters (primary + replicas) in the replication group"
}

variable "automatic_failover_enabled" {
  type        = bool
  default     = false
  description = "Specifies whether a read-only replica will be automatically promoted to read/write primary if the existing primary fails"
}

variable "multi_az_enabled" {
  type        = bool
  default     = false
  description = "Specifies whether to enable Multi-AZ deployment"
}

variable "transit_encryption_enabled" {
  type        = bool
  default     = false
  description = "Whether to enable TLS encryption in transit"
}

variable "auth_token" {
  type        = string
  default     = null
  sensitive   = true
  description = "The password used to access a password-protected Redis server (required if transit_encryption_enabled is true)"
}

variable "snapshot_retention_limit" {
  type        = number
  default     = 0
  description = "The number of days for which ElastiCache will retain automatic cache cluster snapshots"
}

variable "tags" {
  type        = map(string)
  default     = {}
  description = "Common resource tags"
}
