# ==============================================================================
# HashiCorp Vault Configuration for Enterprise E-Commerce Platform
# ==============================================================================

ui = true
disable_mlock = true

storage "file" {
  path = "/vault/data"
}

listener "tcp" {
  address     = "0.0.0.0:8200"
  tls_disable = 1
}

api_addr = "http://127.0.0.1:8200"
cluster_addr = "https://127.0.0.1:8201"
max_lease_ttl = "720h"
default_lease_ttl = "168h"
