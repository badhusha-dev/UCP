aws_region   = "us-east-1"
project_name = "universal-commerce"
environment  = "prod"

vpc_cidr = "10.100.0.0/16"
azs      = ["us-east-1a", "us-east-1b", "us-east-1c"]

public_subnet_cidrs   = ["10.100.1.0/24", "10.100.2.0/24", "10.100.3.0/24"]
private_subnet_cidrs  = ["10.100.11.0/24", "10.100.12.0/24", "10.100.13.0/24"]
database_subnet_cidrs = ["10.100.21.0/24", "10.100.22.0/24", "10.100.23.0/24"]

cluster_version       = "1.29"
admin_whitelist_cidrs = ["10.100.0.0/16"]

db_password      = "ProdSuperSecurePass2026!#Postgres"
redis_auth_token = "ProdRedisSecretTokenAuth2026Key99"
