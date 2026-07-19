#!/usr/bin/env bash
# Generate real, non-committed values for the five ADR-025 Section 2 core
# credentials (ENCRYPT_KEY, CUSTOMER_AES_KEY, CONFIG_SERVER_PASSWORD,
# EUREKA_PASSWORD, REDIS_PASSWORD) and write them into Vault KV v2 at the
# exact paths the 13 services' `vault.secretKeys` (deploy/helm/values/*.yaml,
# Feature 18.3) already expect (Feature 18.4.1, ADR-025 Section 2).
#
# Idempotent-ish: re-running generates FRESH random values and overwrites
# every path (KV v2 keeps the old value as a prior version - nothing is lost,
# `vault kv get -version=N` still reads it). Run this once per environment,
# not once per deploy, unless you are deliberately rotating.
#
# Prerequisites: Vault installed (18.1), initialized/unsealed (RUNBOOK
# Section 3), Kubernetes auth + per-service policies bootstrapped
# (deploy/helm/vault/bootstrap-k8s-auth.sh, 18.2) - the KV v2 engine at
# `secret/` is enabled by that script; this one only writes values into it.
#
# Usage:
#   VAULT_ROOT_TOKEN=<root-token> deploy/helm/vault/seed-secrets.sh
#
# What it does:
#   1. Generates ENCRYPT_KEY: `openssl rand -hex 32` (64 hex chars), matching
#      the generation-method comment in deploy/helm/values/config-server.yaml
#      and the compose convention it mirrors.
#   2. Generates CUSTOMER_AES_KEY: a fresh base64-encoded 32-byte value
#      (openssl rand -base64 32), satisfying AesKeyProvider's AES-256
#      decode-to-32-bytes validation
#      (microservices/customer-service/.../AesKeyProvider.java).
#   3. Generates ONE value each for CONFIG_SERVER_PASSWORD, EUREKA_PASSWORD,
#      REDIS_PASSWORD - per ADR-025 Section 2's mapping table these are
#      "shared where the backing system requires a single credential" (one
#      config-server basic-auth password, one Eureka basic-auth password, one
#      Redis password), generated per environment rather than committed, and
#      NOT unique per service - the value is the same wherever it is written,
#      only the Vault *path* (and therefore the Vault *policy* controlling who
#      can read it) is per-service.
#   4. Writes ENCRYPT_KEY to secret/config-server/encrypt-key and
#      CUSTOMER_AES_KEY to secret/customer-service/aes-key (each its own
#      dedicated path per ADR-025 Section 2, not the shared "app" path).
#   5. Writes CONFIG_SERVER_PASSWORD / EUREKA_PASSWORD / REDIS_PASSWORD to
#      every service's own secret/<service>/app path, restricted to exactly
#      the fields that service's deploy/helm/values/<service>.yaml
#      `vault.secretKeys` declares (discovery-server only takes
#      EUREKA_PASSWORD; config-server takes CONFIG_SERVER_PASSWORD +
#      EUREKA_PASSWORD but not REDIS_PASSWORD; the rest take all three).
#   6. (Feature 18.5.1) For every PostgreSQL-backed service, generates a
#      fresh real password, writes it to secret/<service>/db-credentials
#      alongside that service's existing Postgres role name, AND rotates
#      the live Postgres role's password to match (ALTER USER) so the
#      credential is actually usable end to end, not just Vault-side.
#      Requires the postgres-0 pod to be reachable in the same namespace.
set -euo pipefail

NAMESPACE="${VAULT_NAMESPACE:-telco}"
VAULT_POD="${VAULT_POD:-vault-0}"

: "${VAULT_ROOT_TOKEN:?Set VAULT_ROOT_TOKEN to a Vault token with write access to secret/ (e.g. the init root token)}"

kexec() {
  kubectl -n "$NAMESPACE" exec -i "$VAULT_POD" -- env VAULT_TOKEN="$VAULT_ROOT_TOKEN" "$@"
}

echo "== Generating fresh credential material (not printed to stdout except where noted) =="
# NOTE (bug found + fixed during 18.4.3 live verification): `tr -d '=+/\n'`
# alone leaves a trailing '\r' on Windows/Git Bash (openssl's output line ends
# CRLF there), silently corrupting every password with an invisible
# control character - Basic Auth then fails 401 even though `kubectl exec ...
# -- env` LOOKS identical on both sides. Strip '\r' explicitly too.
ENCRYPT_KEY="$(openssl rand -hex 32 | tr -d '\r\n')"
CUSTOMER_AES_KEY="$(openssl rand -base64 32 | tr -d '\r\n')"
CONFIG_SERVER_PASSWORD="$(openssl rand -base64 24 | tr -d '=+/\r\n')"
EUREKA_PASSWORD="$(openssl rand -base64 24 | tr -d '=+/\r\n')"
REDIS_PASSWORD="$(openssl rand -base64 24 | tr -d '=+/\r\n')"

echo "== 18.4.1: dedicated single-credential paths =="
kexec vault kv put secret/config-server/encrypt-key ENCRYPT_KEY="$ENCRYPT_KEY"
kexec vault kv put secret/customer-service/aes-key CUSTOMER_AES_KEY="$CUSTOMER_AES_KEY"

echo "== 18.4.1: shared-credential paths, one per service (same value, per-service path) =="

# service -> space-separated subset of {CONFIG_SERVER_PASSWORD EUREKA_PASSWORD REDIS_PASSWORD}
# must match each deploy/helm/values/<service>.yaml's vault.secretKeys exactly.
declare -A SERVICE_FIELDS=(
  [api-gateway]="CONFIG_SERVER_PASSWORD EUREKA_PASSWORD REDIS_PASSWORD"
  [billing-service]="CONFIG_SERVER_PASSWORD EUREKA_PASSWORD REDIS_PASSWORD"
  [campaign-service]="CONFIG_SERVER_PASSWORD EUREKA_PASSWORD REDIS_PASSWORD"
  [config-server]="CONFIG_SERVER_PASSWORD EUREKA_PASSWORD"
  [customer-service]="CONFIG_SERVER_PASSWORD EUREKA_PASSWORD REDIS_PASSWORD"
  [discovery-server]="EUREKA_PASSWORD"
  [dispute-service]="CONFIG_SERVER_PASSWORD EUREKA_PASSWORD"
  [fraud-service]="CONFIG_SERVER_PASSWORD EUREKA_PASSWORD"
  [identity-service]="CONFIG_SERVER_PASSWORD EUREKA_PASSWORD REDIS_PASSWORD"
  [notification-service]="CONFIG_SERVER_PASSWORD EUREKA_PASSWORD REDIS_PASSWORD"
  [order-service]="CONFIG_SERVER_PASSWORD EUREKA_PASSWORD REDIS_PASSWORD"
  [payment-service]="CONFIG_SERVER_PASSWORD EUREKA_PASSWORD REDIS_PASSWORD"
  [product-catalog-service]="CONFIG_SERVER_PASSWORD EUREKA_PASSWORD REDIS_PASSWORD"
  [subscription-service]="CONFIG_SERVER_PASSWORD EUREKA_PASSWORD REDIS_PASSWORD"
  [ticket-service]="CONFIG_SERVER_PASSWORD EUREKA_PASSWORD REDIS_PASSWORD"
  [usage-service]="CONFIG_SERVER_PASSWORD EUREKA_PASSWORD REDIS_PASSWORD"
)

for svc in "${!SERVICE_FIELDS[@]}"; do
  args=()
  for field in ${SERVICE_FIELDS[$svc]}; do
    case "$field" in
      CONFIG_SERVER_PASSWORD) args+=("CONFIG_SERVER_PASSWORD=$CONFIG_SERVER_PASSWORD") ;;
      EUREKA_PASSWORD) args+=("EUREKA_PASSWORD=$EUREKA_PASSWORD") ;;
      REDIS_PASSWORD) args+=("REDIS_PASSWORD=$REDIS_PASSWORD") ;;
    esac
  done
  echo "-- secret/$svc/app: ${SERVICE_FIELDS[$svc]}"
  kexec vault kv put "secret/$svc/app" "${args[@]}"
done

echo "== 18.5.1: per-service DB credentials (secret/<service>/db-credentials) =="
# Every PostgreSQL-backed service per docs/architecture/service-catalog.md
# Section 5 (all 13 services except api-gateway, discovery-server,
# config-server, which have no primary store), plus notification-service's
# PostgreSQL outbox DB even though its primary store is MongoDB.
#
# Username: kept as the existing per-service Postgres role name
# (deploy/helm/dependencies/files/postgres/01-create-databases.sql already
# gives each service its own non-shared role, e.g. "customer" owns
# customer_db - that per-service isolation predates this feature, ADR-006).
# Renaming the role would require reassigning database ownership for no
# security benefit this feature is scoped to deliver; NOT done here.
#
# Password: a fresh, real, random value - replacing the committed
# "<service>"/"<service>" dev-default password (e.g. "customer"/"customer")
# ADR-025's Context section flags as the gap. Written to BOTH Vault KV v2
# (the path each service's existing 18.2.2 policy, secret/data/<service>/*,
# already covers - no new policy needed) AND the live Postgres role itself
# (ALTER USER ... PASSWORD), so the two stay in sync and the credential is
# real end-to-end, not just real-looking in Vault. These populate the
# CUSTOMER_DB_USER/CUSTOMER_DB_PASSWORD-style keys the 18.5.2 `dev,k8s`
# profile (microservices/configs/<service>/application-k8s.yml) reads from
# the environment, replacing the docker profile's plaintext
# `customer`/`customer`-style DB block.
#
# Requires the postgres-0 pod (deploy/helm/dependencies) to be reachable;
# set POSTGRES_POD / POSTGRES_SUPERUSER to override the defaults below.
POSTGRES_POD="${POSTGRES_POD:-postgres-0}"
POSTGRES_SUPERUSER="${POSTGRES_SUPERUSER:-telco}"

pexec() {
  kubectl -n "$NAMESPACE" exec -i "$POSTGRES_POD" -- "$@"
}

# service -> Postgres role name (01-create-databases.sql; "order" is
# double-quoted because ORDER is a SQL reserved word).
declare -A DB_ROLE=(
  [identity-service]="identity"
  [customer-service]="customer"
  [product-catalog-service]="product_catalog"
  [order-service]='"order"'
  [subscription-service]="subscription"
  [usage-service]="usage"
  [billing-service]="billing"
  [payment-service]="payment"
  [notification-service]="notification"
  [ticket-service]="ticket"
  [campaign-service]="campaign"
  [dispute-service]="dispute"
  [fraud-service]="fraud"
)

for svc in "${!DB_ROLE[@]}"; do
  role="${DB_ROLE[$svc]}"
  db_password="$(openssl rand -base64 24 | tr -d '=+/\r\n')"
  echo "-- secret/$svc/db-credentials (Postgres role: $role)"
  kexec vault kv put "secret/$svc/db-credentials" \
    username="$(echo "$role" | tr -d '"')" \
    password="$db_password"
  pexec env PGPASSWORD="${POSTGRES_SUPERUSER_PASSWORD:-telco}" \
    psql -U "$POSTGRES_SUPERUSER" -d postgres -v ON_ERROR_STOP=1 \
    -c "ALTER USER $role WITH PASSWORD '$db_password';"
done

echo
echo "Done. Verify (values are real - do not paste output into a commit, log, or chat):"
echo "  kubectl -n $NAMESPACE exec -it $VAULT_POD -- env VAULT_TOKEN=\$VAULT_ROOT_TOKEN vault kv get secret/config-server/encrypt-key"
echo "  kubectl -n $NAMESPACE exec -it $VAULT_POD -- env VAULT_TOKEN=\$VAULT_ROOT_TOKEN vault kv get secret/customer-service/aes-key"
echo "  kubectl -n $NAMESPACE exec -it $VAULT_POD -- env VAULT_TOKEN=\$VAULT_ROOT_TOKEN vault kv get secret/customer-service/app"
echo "  kubectl -n $NAMESPACE exec -it $VAULT_POD -- env VAULT_TOKEN=\$VAULT_ROOT_TOKEN vault kv get secret/customer-service/db-credentials"
