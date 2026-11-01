output "db_instance_id" {
  description = "The RDS instance identifier"
  value       = aws_db_instance.main.id
}

output "db_instance_arn" {
  description = "The Amazon Resource Name of the RDS instance"
  value       = aws_db_instance.main.arn
}

output "db_instance_endpoint" {
  description = "The connection endpoint in address:port format"
  value       = aws_db_instance.main.endpoint
}

output "db_instance_address" {
  description = "The address of the RDS database host"
  value       = aws_db_instance.main.address
}

output "db_instance_port" {
  description = "The database listening port"
  value       = aws_db_instance.main.port
}

output "db_name" {
  description = "The default database name"
  value       = aws_db_instance.main.db_name
}

output "db_username" {
  description = "The master username for the database"
  value       = aws_db_instance.main.username
  sensitive   = true
}

output "db_subnet_group_id" {
  description = "The ID of the DB subnet group"
  value       = aws_db_subnet_group.main.id
}

output "db_parameter_group_id" {
  description = "The ID of the DB parameter group"
  value       = aws_db_parameter_group.main.id
}
