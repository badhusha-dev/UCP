variable "project_name" {
  type        = string
  description = "Project name identifier"
}

variable "environment" {
  type        = string
  description = "Deployment environment (e.g. dev, prod)"
}

variable "subnet_ids" {
  type        = list(string)
  description = "List of VPC subnets across at least 2 or 3 AZs for MSK broker placement"
}

variable "msk_security_group_id" {
  type        = string
  description = "Security group ID allowing Kafka broker communication from EKS nodes"
}

variable "kms_key_arn" {
  type        = string
  description = "KMS Customer Managed Key ARN for at-rest storage and broker disk encryption"
}

variable "kafka_version" {
  type        = string
  default     = "3.6.0"
  description = "Apache Kafka engine version"
}

variable "number_of_broker_nodes" {
  type        = number
  default     = 3
  description = "Total number of Kafka broker nodes in the cluster (must be a multiple of the number of subnets)"
}

variable "broker_instance_type" {
  type        = string
  default     = "kafka.t3.small"
  description = "EC2 instance size for Kafka brokers"
}

variable "ebs_volume_size" {
  type        = number
  default     = 50
  description = "Size in GiB of the EBS volume attached to each broker node"
}

variable "client_broker_encryption" {
  type        = string
  default     = "TLS_PLAINTEXT"
  description = "Encryption setting for data in transit between clients and brokers (TLS, TLS_PLAINTEXT, or PLAINTEXT)"
}

variable "log_retention_days" {
  type        = number
  default     = 30
  description = "CloudWatch log retention in days for Kafka broker logs"
}

variable "tags" {
  type        = map(string)
  default     = {}
  description = "Common resource tags"
}
