variable "cluster_name" {
  type        = string
  description = "Name of the EKS cluster"
}

variable "cluster_version" {
  type        = string
  default     = "1.29"
  description = "Kubernetes control plane version"
}

variable "environment" {
  type        = string
  description = "Deployment environment (e.g. dev, prod)"
}

variable "subnet_ids" {
  type        = list(string)
  description = "List of VPC private subnet IDs for EKS control plane and worker nodes"
}

variable "node_security_group_id" {
  type        = string
  description = "Security group ID attached to EKS nodes"
}

variable "kms_key_arn" {
  type        = string
  description = "KMS Key ARN for EKS secret envelope encryption"
}

variable "log_retention_days" {
  type        = number
  default     = 30
  description = "Number of days to retain EKS control plane logs in CloudWatch"
}

variable "endpoint_public_access" {
  type        = bool
  default     = true
  description = "Indicates whether the Amazon EKS public API server endpoint is enabled"
}

variable "public_access_cidrs" {
  type        = list(string)
  default     = ["0.0.0.0/0"]
  description = "List of CIDR blocks that can access the Amazon EKS public API server endpoint"
}

variable "node_disk_size" {
  type        = number
  default     = 50
  description = "Root EBS volume size in GiB for worker nodes"
}

variable "on_demand_instance_types" {
  type        = list(string)
  default     = ["t3.medium"]
  description = "Instance types for On-Demand system node group"
}

variable "on_demand_scaling" {
  type = object({
    desired_size = number
    min_size     = number
    max_size     = number
  })
  default = {
    desired_size = 2
    min_size     = 1
    max_size     = 4
  }
  description = "Scaling parameters for system on-demand node group"
}

variable "enable_spot_nodes" {
  type        = bool
  default     = true
  description = "Whether to provision the spot worker node group"
}

variable "spot_instance_types" {
  type        = list(string)
  default     = ["t3.large", "t3a.large"]
  description = "Instance types for Spot application workloads"
}

variable "spot_scaling" {
  type = object({
    desired_size = number
    min_size     = number
    max_size     = number
  })
  default = {
    desired_size = 2
    min_size     = 1
    max_size     = 6
  }
  description = "Scaling parameters for spot application node group"
}

variable "tags" {
  type        = map(string)
  default     = {}
  description = "Common resource tags"
}
