output "vpc_id" {
  description = "The ID of the VPC"
  value       = module.vpc.vpc_id
}

output "nat_gateway_ids" {
  description = "IDs of the Multi-AZ NAT Gateways"
  value       = module.vpc.nat_gateway_ids
}

output "eks_cluster_name" {
  description = "Production EKS Cluster Name"
  value       = module.eks.cluster_name
}

output "eks_cluster_endpoint" {
  description = "Production EKS Cluster Kubernetes API Server Endpoint"
  value       = module.eks.cluster_endpoint
}

output "eks_oidc_provider_arn" {
  description = "Production EKS OIDC Provider ARN for IRSA"
  value       = module.eks.oidc_provider_arn
}

output "kms_key_arn" {
  description = "Primary Customer Managed KMS Key ARN for Envelope Encryption"
  value       = module.security.kms_key_arn
}

output "rds_postgres_endpoint" {
  description = "Production RDS PostgreSQL Multi-AZ Primary Endpoint"
  value       = module.rds.db_instance_endpoint
}

output "rds_postgres_address" {
  description = "Production RDS PostgreSQL Host Address"
  value       = module.rds.db_instance_address
}

output "redis_primary_endpoint" {
  description = "Production ElastiCache Redis Primary Endpoint (Write)"
  value       = module.elasticache.primary_endpoint_address
}

output "redis_reader_endpoint" {
  description = "Production ElastiCache Redis Reader Endpoint (Read Replicas)"
  value       = module.elasticache.reader_endpoint_address
}

output "msk_bootstrap_brokers_tls" {
  description = "Production MSK Kafka TLS-Encrypted Broker Endpoints"
  value       = module.msk.bootstrap_brokers_tls
}
