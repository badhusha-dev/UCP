output "vpc_id" {
  description = "The ID of the VPC"
  value       = module.vpc.vpc_id
}

output "eks_cluster_name" {
  description = "EKS Cluster Name"
  value       = module.eks.cluster_name
}

output "eks_cluster_endpoint" {
  description = "EKS Cluster Kubernetes API Server Endpoint"
  value       = module.eks.cluster_endpoint
}

output "eks_oidc_provider_arn" {
  description = "EKS OIDC Provider ARN for IRSA"
  value       = module.eks.oidc_provider_arn
}

output "kms_key_arn" {
  description = "Customer Managed KMS Key ARN"
  value       = module.security.kms_key_arn
}

output "rds_postgres_endpoint" {
  description = "RDS PostgreSQL Host Endpoint"
  value       = module.rds.db_instance_endpoint
}

output "rds_postgres_address" {
  description = "RDS PostgreSQL Host Address"
  value       = module.rds.db_instance_address
}

output "redis_primary_endpoint" {
  description = "ElastiCache Redis Primary Host Endpoint"
  value       = module.elasticache.primary_endpoint_address
}

output "msk_bootstrap_brokers" {
  description = "MSK Kafka Plaintext Broker Endpoints"
  value       = module.msk.bootstrap_brokers
}

output "msk_bootstrap_brokers_tls" {
  description = "MSK Kafka TLS Broker Endpoints"
  value       = module.msk.bootstrap_brokers_tls
}
