#!/usr/bin/env bash
# Vault Kubernetes auth method + per-service policies + per-service auth roles
# (Feature 18.2, ADR-025 Section 1/2). Idempotent: safe to re-run after a
# partial failure or to pick up a newly added service.
#
# Prerequisites: Vault is installed (18.1), initialized, and UNSEALED
# (deploy/RUNBOOK.md Section 3). Run from the repo root with a working
# `kubectl` context pointed at the target cluster and the Vault root token
# (or any token with sufficient privilege) exported as VAULT_ROOT_TOKEN.
#
# Usage:
#   VAULT_ROOT_TOKEN=<root-token> deploy/helm/vault/bootstrap-k8s-auth.sh
#
# What it does, in order (18.2.1 -> 18.2.2 -> 18.2.3):
#   1. Enables the `kubernetes` auth method (no-op if already enabled).
#   2. Configures it against the in-cluster Kubernetes API, using Vault's own
#      pod's projected ServiceAccount token/CA as the token-reviewer JWT (that
#      ServiceAccount already carries `system:auth-delegator`, granted by
#      deploy/helm/vault/templates/auth-delegator-clusterrolebinding.yaml).
#   3. Enables the KV v2 secrets engine at `secret/` (no-op if already
#      enabled).
#   4. Writes one least-privilege policy per service from
#      deploy/helm/vault/policies/<service>.hcl.
#   5. Writes one `auth/kubernetes/role/<service>` per service, binding that
#      service's ServiceAccount (name == service name, namespace `telco`,
#      per deploy/helm/telco-service/templates/serviceaccount.yaml) to its
#      policy.
set -euo pipefail

NAMESPACE="${VAULT_NAMESPACE:-telco}"
VAULT_POD="${VAULT_POD:-vault-0}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
POLICIES_DIR="$SCRIPT_DIR/policies"

: "${VAULT_ROOT_TOKEN:?Set VAULT_ROOT_TOKEN to a Vault token with sufficient privilege (e.g. the init root token)}"

kexec() {
  kubectl -n "$NAMESPACE" exec -i "$VAULT_POD" -- env VAULT_TOKEN="$VAULT_ROOT_TOKEN" "$@"
}

# 16 services from deploy/helm/values/*.yaml (deploy/helm/README.md "Config /
# Secret model": every one of them consumes at least EUREKA_PASSWORD today).
SERVICES=(
  api-gateway
  billing-service
  campaign-service
  config-server
  customer-service
  discovery-server
  dispute-service
  fraud-service
  identity-service
  notification-service
  order-service
  payment-service
  product-catalog-service
  subscription-service
  ticket-service
  usage-service
)

echo "== 18.2.1: Kubernetes auth method =="
if kexec vault auth list -format=json | grep -q '"kubernetes/"'; then
  echo "kubernetes/ auth method already enabled, skipping enable"
else
  kexec vault auth enable kubernetes
fi

# Run the config write from inside the Vault pod itself: its own projected
# ServiceAccount token/CA (standard K8s downward-API paths) are exactly the
# token-reviewer credentials this needs, and that ServiceAccount already has
# system:auth-delegator via the ClusterRoleBinding shipped with this chart.
kexec sh -c '
  vault write auth/kubernetes/config \
    kubernetes_host="https://${KUBERNETES_SERVICE_HOST}:${KUBERNETES_SERVICE_PORT}" \
    token_reviewer_jwt=@/var/run/secrets/kubernetes.io/serviceaccount/token \
    kubernetes_ca_cert=@/var/run/secrets/kubernetes.io/serviceaccount/ca.crt
'

echo "== 18.2.2: KV v2 engine + per-service policies =="
if kexec vault secrets list -format=json | grep -q '"secret/"'; then
  echo "secret/ KV engine already enabled, skipping enable"
else
  kexec vault secrets enable -path=secret kv-v2
fi

for svc in "${SERVICES[@]}"; do
  echo "-- policy: $svc"
  kubectl -n "$NAMESPACE" exec -i "$VAULT_POD" -- env VAULT_TOKEN="$VAULT_ROOT_TOKEN" \
    vault policy write "$svc" - < "$POLICIES_DIR/$svc.hcl"
done

echo "== 18.2.3: per-service Kubernetes auth roles =="
for svc in "${SERVICES[@]}"; do
  echo "-- role: $svc"
  kexec vault write "auth/kubernetes/role/$svc" \
    bound_service_account_names="$svc" \
    bound_service_account_namespaces="$NAMESPACE" \
    policies="$svc" \
    ttl=15m
done

echo "Done. Verify with:"
echo "  kubectl -n $NAMESPACE exec -it $VAULT_POD -- env VAULT_TOKEN=$VAULT_ROOT_TOKEN vault auth list"
echo "  kubectl -n $NAMESPACE exec -it $VAULT_POD -- env VAULT_TOKEN=$VAULT_ROOT_TOKEN vault read auth/kubernetes/config"
echo "  kubectl -n $NAMESPACE exec -it $VAULT_POD -- env VAULT_TOKEN=$VAULT_ROOT_TOKEN vault policy read customer-service"
echo "  kubectl -n $NAMESPACE exec -it $VAULT_POD -- env VAULT_TOKEN=$VAULT_ROOT_TOKEN vault read auth/kubernetes/role/customer-service"
