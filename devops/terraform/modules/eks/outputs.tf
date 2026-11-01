output "cluster_id" {
  description = "The name/ID of the EKS cluster"
  value       = aws_eks_cluster.main.id
}

output "cluster_name" {
  description = "The name of the EKS cluster"
  value       = aws_eks_cluster.main.name
}

output "cluster_arn" {
  description = "The Amazon Resource Name (ARN) of the cluster"
  value       = aws_eks_cluster.main.arn
}

output "cluster_endpoint" {
  description = "The endpoint for your EKS Kubernetes API server"
  value       = aws_eks_cluster.main.endpoint
}

output "cluster_certificate_authority_data" {
  description = "Base64 encoded certificate data required to communicate with the cluster"
  value       = aws_eks_cluster.main.certificate_authority[0].data
}

output "cluster_security_group_id" {
  description = "Security group ID created automatically by EKS control plane"
  value       = aws_eks_cluster.main.vpc_config[0].cluster_security_group_id
}

output "oidc_provider_arn" {
  description = "ARN of the OIDC Provider for IAM Roles for Service Accounts (IRSA)"
  value       = aws_iam_openid_connect_provider.cluster.arn
}

output "oidc_provider_url" {
  description = "The URL on the EKS cluster for the OIDC identity provider"
  value       = aws_eks_cluster.main.identity[0].oidc[0].issuer
}

output "node_role_arn" {
  description = "IAM role ARN associated with EKS worker node groups"
  value       = aws_iam_role.node_group.arn
}

output "cluster_role_arn" {
  description = "IAM role ARN associated with EKS cluster control plane"
  value       = aws_iam_role.cluster.arn
}

output "on_demand_node_group_arn" {
  description = "ARN of the On-Demand EKS node group"
  value       = aws_eks_node_group.on_demand.arn
}

output "spot_node_group_arn" {
  description = "ARN of the Spot EKS node group if enabled"
  value       = var.enable_spot_nodes ? aws_eks_node_group.spot[0].arn : null
}
