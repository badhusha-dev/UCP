output "vpc_id" {
  description = "The ID of the VPC"
  value       = aws_vpc.this.id
}

output "vpc_cidr" {
  description = "The CIDR block of the VPC"
  value       = aws_vpc.this.cidr_block
}

output "public_subnet_ids" {
  description = "List of IDs of public subnets"
  value       = aws_subnet.public[*].id
}

output "private_subnet_ids" {
  description = "List of IDs of private compute subnets for EKS"
  value       = aws_subnet.private[*].id
}

output "database_subnet_ids" {
  description = "List of IDs of isolated database subnets"
  value       = aws_subnet.database[*].id
}

output "db_subnet_group_name" {
  description = "The name of the database subnet group for RDS"
  value       = aws_db_subnet_group.this.name
}

output "nat_gateway_ips" {
  description = "List of Elastic IP addresses associated with NAT Gateways"
  value       = aws_eip.nat[*].public_ip
}
