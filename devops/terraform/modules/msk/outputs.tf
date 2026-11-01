output "cluster_arn" {
  description = "Amazon Resource Name of the MSK Kafka cluster"
  value       = aws_msk_cluster.main.arn
}

output "cluster_name" {
  description = "The name of the MSK Kafka cluster"
  value       = aws_msk_cluster.main.cluster_name
}

output "bootstrap_brokers" {
  description = "Comma separated list of one or more hostname:port pairs of Kafka brokers (plaintext)"
  value       = aws_msk_cluster.main.bootstrap_brokers
}

output "bootstrap_brokers_tls" {
  description = "Comma separated list of one or more hostname:port pairs of Kafka brokers with TLS encryption"
  value       = aws_msk_cluster.main.bootstrap_brokers_tls
}

output "bootstrap_brokers_sasl_iam" {
  description = "Comma separated list of one or more hostname:port pairs of Kafka brokers with IAM authentication"
  value       = aws_msk_cluster.main.bootstrap_brokers_sasl_iam
}

output "zookeeper_connect_string" {
  description = "A comma separated list of one or more hostname:port pairs for ZooKeeper ensemble"
  value       = aws_msk_cluster.main.zookeeper_connect_string
}
