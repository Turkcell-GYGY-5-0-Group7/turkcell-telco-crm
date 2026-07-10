#!/usr/bin/env bash
# ===========================================================================
# Telco CRM post-deploy smoke test (Sprint 15.4.3, ADR-014 / NFR-04).
#
# Purpose: prove a fresh deploy is actually serving before it takes traffic,
# and exit non-zero with a clear message on any failure so the CI deploy job
# can trigger a rollback (15.4.2).
#
# It is REUSABLE:
#   * CI runs it after deploying to the ephemeral Kind cluster.
#   * A developer runs it locally against the existing Kind stack, unchanged.
#
# What it checks:
#   1. Gateway /actuator/health through the Ingress => HTTP 200 and status UP.
#   2. Readiness of a few key service Deployments (via kubectl, if available).
#   3. One happy-path auth flow: fetch a real Keycloak token (realm telco-crm)
#      for the seeded SUBSCRIBER user, then call one authenticated READ endpoint
#      through the gateway and assert the expected status (200).
#
# Dependencies: curl + jq (required). kubectl is used ONLY when it is needed to
# reach the ClusterIP-only Keycloak (auto port-forward) or to check Deployment
# readiness; set KEYCLOAK_URL to skip the port-forward and stay kubectl-free.
#
# Every setting is overridable via environment variables (see below), so the
# same script drives both the CI Kind cluster and a local one.
# ===========================================================================
set -euo pipefail

# --------------------------------------------------------------------------- #
# Configuration (all overridable via the environment).
# --------------------------------------------------------------------------- #
NAMESPACE="${NAMESPACE:-telco}"

# Gateway reached THROUGH the Ingress. In Kind, host 18080 maps to the ingress
# controller's port 80 (deploy/kind/kind-cluster.yaml); the gateway Ingress
# routes host "telco.local", sent here as an explicit Host header.
GATEWAY_URL="${GATEWAY_URL:-http://localhost:18080}"
INGRESS_HOST="${INGRESS_HOST:-telco.local}"

# Keycloak (ClusterIP-only). If KEYCLOAK_URL is set it is used as-is (no
# kubectl needed). Otherwise the script port-forwards svc/keycloak.
KEYCLOAK_URL="${KEYCLOAK_URL:-}"
KEYCLOAK_LOCAL_PORT="${KEYCLOAK_LOCAL_PORT:-8085}"
KEYCLOAK_SVC_PORT="${KEYCLOAK_SVC_PORT:-8080}"

# Realm / client / seeded user - reused verbatim from the acceptance suite
# (microservices/acceptance-tests .../support/AcceptanceConfig.java + TokenProvider.java):
# public client "telco-web" with directAccessGrantsEnabled, ROPC password grant.
REALM="${REALM:-telco-crm}"
CLIENT_ID="${CLIENT_ID:-telco-web}"
SUBSCRIBER_USERNAME="${SUBSCRIBER_USERNAME:-subscriber@telco.local}"
SUBSCRIBER_PASSWORD="${SUBSCRIBER_PASSWORD:-subscriber}"

# The authenticated READ used as the happy-path probe. GET /api/v1/tariffs
# (product-catalog TariffController.listTariffs) has no @PreAuthorize, so any
# authenticated caller - including the seeded SUBSCRIBER - gets 200 with a page
# body. It exercises the full edge chain: Ingress -> gateway -> JWT validation
# (ADR-011) -> Eureka route -> product-catalog-service -> its DB.
READ_PATH="${READ_PATH:-/api/v1/tariffs}"
EXPECTED_READ_STATUS="${EXPECTED_READ_STATUS:-200}"

# Key services whose Deployment readiness is asserted (best-effort, kubectl only).
READINESS_SERVICES="${READINESS_SERVICES:-config-server discovery-server api-gateway identity-service product-catalog-service}"

# Retry tuning.
HEALTH_RETRIES="${HEALTH_RETRIES:-30}"
TOKEN_RETRIES="${TOKEN_RETRIES:-30}"
RETRY_DELAY="${RETRY_DELAY:-5}"
CURL_MAX_TIME="${CURL_MAX_TIME:-10}"
READINESS_TIMEOUT="${READINESS_TIMEOUT:-300s}"

PF_PID=""

# --------------------------------------------------------------------------- #
# Helpers.
# --------------------------------------------------------------------------- #
log()  { printf '[smoke] %s\n' "$*" >&2; }
ok()   { printf '[smoke] OK: %s\n' "$*" >&2; }
fail() { printf '[smoke] FAIL: %s\n' "$*" >&2; exit 1; }

cleanup() {
  if [ -n "$PF_PID" ] && kill -0 "$PF_PID" 2>/dev/null; then
    kill "$PF_PID" 2>/dev/null || true
    wait "$PF_PID" 2>/dev/null || true
  fi
}
trap cleanup EXIT

require() {
  command -v "$1" >/dev/null 2>&1 || fail "required tool '$1' not found on PATH"
}

# curl the gateway with the Ingress Host header; echoes HTTP status, writes body
# to the file named by $1. Extra curl args follow.
gateway_status() {
  out_file="$1"; shift
  curl -sS -o "$out_file" -w '%{http_code}' \
    --max-time "$CURL_MAX_TIME" \
    -H "Host: ${INGRESS_HOST}" \
    "$@"
}

# --------------------------------------------------------------------------- #
# Preflight.
# --------------------------------------------------------------------------- #
require curl
require jq

TMP_DIR="$(mktemp -d)"
trap 'cleanup; rm -rf "$TMP_DIR"' EXIT

log "namespace=$NAMESPACE gateway=$GATEWAY_URL host=$INGRESS_HOST read=$READ_PATH"

# --------------------------------------------------------------------------- #
# 1. Gateway health through the Ingress (expect 200 + status UP).
# --------------------------------------------------------------------------- #
log "checking gateway /actuator/health through the Ingress..."
health_ok=false
for i in $(seq 1 "$HEALTH_RETRIES"); do
  code="$(gateway_status "$TMP_DIR/health.json" "${GATEWAY_URL}/actuator/health" || true)"
  if [ "$code" = "200" ]; then
    status="$(jq -r '.status // empty' "$TMP_DIR/health.json" 2>/dev/null || true)"
    if [ "$status" = "UP" ]; then
      health_ok=true
      break
    fi
    log "attempt $i/$HEALTH_RETRIES: health 200 but status='$status' (want UP)"
  else
    log "attempt $i/$HEALTH_RETRIES: gateway health HTTP $code"
  fi
  sleep "$RETRY_DELAY"
done
[ "$health_ok" = true ] || fail "gateway /actuator/health did not report 200 UP through the Ingress"
ok "gateway health is UP"

# --------------------------------------------------------------------------- #
# 2. Key service readiness (best-effort; needs kubectl).
# --------------------------------------------------------------------------- #
if command -v kubectl >/dev/null 2>&1; then
  log "checking readiness of key service Deployments..."
  for svc in $READINESS_SERVICES; do
    if kubectl -n "$NAMESPACE" rollout status "deploy/$svc" --timeout="$READINESS_TIMEOUT" >/dev/null 2>&1; then
      ok "deployment/$svc is ready"
    else
      fail "deployment/$svc is not ready in namespace $NAMESPACE"
    fi
  done
else
  log "kubectl not found; skipping Deployment readiness checks (HTTP checks still enforced)"
fi

# --------------------------------------------------------------------------- #
# 3a. Resolve a reachable Keycloak URL (port-forward when not supplied).
# --------------------------------------------------------------------------- #
if [ -z "$KEYCLOAK_URL" ]; then
  command -v kubectl >/dev/null 2>&1 || \
    fail "KEYCLOAK_URL not set and kubectl unavailable to port-forward svc/keycloak"
  log "port-forwarding svc/keycloak ${KEYCLOAK_LOCAL_PORT}:${KEYCLOAK_SVC_PORT}..."
  kubectl -n "$NAMESPACE" port-forward "svc/keycloak" \
    "${KEYCLOAK_LOCAL_PORT}:${KEYCLOAK_SVC_PORT}" >/dev/null 2>&1 &
  PF_PID=$!
  KEYCLOAK_URL="http://localhost:${KEYCLOAK_LOCAL_PORT}"
  sleep 3
fi
TOKEN_URI="${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/token"

# --------------------------------------------------------------------------- #
# 3b. Fetch a subscriber JWT via the ROPC password grant (with retries).
# --------------------------------------------------------------------------- #
log "requesting a Keycloak token for '$SUBSCRIBER_USERNAME' (realm $REALM)..."
ACCESS_TOKEN=""
for i in $(seq 1 "$TOKEN_RETRIES"); do
  code="$(curl -sS -o "$TMP_DIR/token.json" -w '%{http_code}' \
    --max-time "$CURL_MAX_TIME" \
    -X POST "$TOKEN_URI" \
    -H 'Content-Type: application/x-www-form-urlencoded' \
    --data-urlencode 'grant_type=password' \
    --data-urlencode "client_id=${CLIENT_ID}" \
    --data-urlencode "username=${SUBSCRIBER_USERNAME}" \
    --data-urlencode "password=${SUBSCRIBER_PASSWORD}" \
    --data-urlencode 'scope=openid' || true)"
  if [ "$code" = "200" ]; then
    ACCESS_TOKEN="$(jq -r '.access_token // empty' "$TMP_DIR/token.json" 2>/dev/null || true)"
    [ -n "$ACCESS_TOKEN" ] && break
    log "attempt $i/$TOKEN_RETRIES: token endpoint 200 but no access_token in body"
  else
    log "attempt $i/$TOKEN_RETRIES: token endpoint HTTP $code"
  fi
  sleep "$RETRY_DELAY"
done
[ -n "$ACCESS_TOKEN" ] || fail "could not obtain a Keycloak access token from $TOKEN_URI"
ok "obtained a subscriber access token"

# --------------------------------------------------------------------------- #
# 3c. Authenticated READ through the gateway (expect $EXPECTED_READ_STATUS).
# --------------------------------------------------------------------------- #
log "calling authenticated READ ${READ_PATH} through the gateway..."
read_code="$(gateway_status "$TMP_DIR/read.json" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  "${GATEWAY_URL}${READ_PATH}" || true)"
if [ "$read_code" != "$EXPECTED_READ_STATUS" ]; then
  log "response body: $(head -c 500 "$TMP_DIR/read.json" 2>/dev/null || true)"
  fail "authenticated READ ${READ_PATH} returned HTTP $read_code (expected $EXPECTED_READ_STATUS)"
fi
ok "authenticated READ ${READ_PATH} returned HTTP $read_code"

log "all smoke checks passed"
exit 0
