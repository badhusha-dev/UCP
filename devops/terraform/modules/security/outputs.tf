output "kms_key_arn" {
  description = "ARN of primary KMS Customer Managed Key"
  value       = aws_kms_key.primary.arn
}

output "kms_key_id" {
  description = "ID of primary KMS Customer Managed Key"
  value       = aws_kms_key.primary.key_id
}

output "kms_alias_arn" {
  description = "ARN of KMS primary key alias"
  value       = aws_kms_alias.primary.arn
}

output "alb_security_group_id" {
  description = "Security Group ID for Internet-facing Application Load Balancer"
  value       = aws_security_group.alb.id
}

output "eks_nodes_security_group_id" {
  description = "Security Group ID for EKS worker nodes & pods"
  value       = aws_security_group.eks_nodes.id
}

output "rds_security_group_id" {
  description = "Security Group ID for PostgreSQL RDS instance"
  value       = aws_security_group.rds.id
}

output "redis_security_group_id" {
  description = "Security Group ID for ElastiCache Redis cluster"
  value       = aws_security_group.redis.id
}

output "msk_security_group_id" {
  description = "Security Group ID for MSK Kafka broker cluster"
  value       = aws_security_group.msk.id
}
