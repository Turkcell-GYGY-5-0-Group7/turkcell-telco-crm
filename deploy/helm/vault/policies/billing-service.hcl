# Vault KV v2 policy for the "billing-service" ServiceAccount (task 18.2.2, ADR-025 Section
# 1/2). Least-privilege: read + list on exactly this service's own secret
# path (secret/data/billing-service/*, secret/metadata/billing-service/* for KV v2 list/versioning) -
# nothing else. Bound to this service's Kubernetes auth role in
# auth/kubernetes/role/billing-service (task 18.2.3). No write/delete: secret material is
# authored by an operator (`vault kv put`), never by the service itself.
path "secret/data/billing-service/*" {
  capabilities = ["read", "list"]
}

path "secret/metadata/billing-service/*" {
  capabilities = ["read", "list"]
}
