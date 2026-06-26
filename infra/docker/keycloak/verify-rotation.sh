#!/usr/bin/env bash
# Verifies refresh-token rotation and reuse detection (FR-IAM-05, feature 5.4.2).
#
# Prerequisites: Keycloak running at localhost:8085 (make auth from infra/).
# Run: bash infra/docker/keycloak/verify-rotation.sh
#
# Expected outcome:
#   Step 2 -> SUCCESS (first refresh works, issues a new token)
#   Step 3 -> ERROR   (reused rotated token rejected, session revoked)
#   Step 4 -> ERROR   (second refresh token also invalid; session is gone)

set -euo pipefail

KC="http://localhost:8085"
REALM="telco-crm"
CLIENT="telco-gateway"
SECRET="local-dev-secret"
USER="admin@telco.local"
PASS="admin"

TOKEN_EP="$KC/realms/$REALM/protocol/openid-connect/token"
EVENTS_URL="$KC/admin/master/console/#/telco-crm/events?type=REFRESH_TOKEN_ERROR"

json_field() { python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('$1',''))" ; }

echo "=== Step 1: password grant - initial token pair ==="
INIT=$(curl -sf -X POST "$TOKEN_EP" \
  -d "grant_type=password&client_id=$CLIENT&client_secret=$SECRET&username=$USER&password=$PASS")
RT1=$(echo "$INIT" | json_field refresh_token)
echo "Got refresh_token_1 (prefix): ${RT1:0:32}..."

echo ""
echo "=== Step 2: use refresh_token_1 (must succeed, issues refresh_token_2) ==="
RESP2=$(curl -s -X POST "$TOKEN_EP" \
  -d "grant_type=refresh_token&client_id=$CLIENT&client_secret=$SECRET&refresh_token=$RT1")
RT2=$(echo "$RESP2" | json_field refresh_token)
if [ -n "$RT2" ]; then
  echo "PASS: new refresh_token_2 issued (prefix): ${RT2:0:32}..."
else
  echo "FAIL: expected a new refresh token but got: $RESP2"
  exit 1
fi

echo ""
echo "=== Step 3: reuse refresh_token_1 (must fail and revoke session) ==="
RESP3=$(curl -s -X POST "$TOKEN_EP" \
  -d "grant_type=refresh_token&client_id=$CLIENT&client_secret=$SECRET&refresh_token=$RT1")
ERR3=$(echo "$RESP3" | json_field error)
if [ "$ERR3" = "invalid_grant" ]; then
  echo "PASS: reuse rejected with 'invalid_grant' - session revoked"
else
  echo "FAIL: expected invalid_grant but got: $RESP3"
  exit 1
fi

echo ""
echo "=== Step 4: try refresh_token_2 (session revoked, must also fail) ==="
RESP4=$(curl -s -X POST "$TOKEN_EP" \
  -d "grant_type=refresh_token&client_id=$CLIENT&client_secret=$SECRET&refresh_token=$RT2")
ERR4=$(echo "$RESP4" | json_field error)
if [ "$ERR4" = "invalid_grant" ]; then
  echo "PASS: refresh_token_2 also rejected - full session revocation confirmed"
else
  echo "FAIL: expected invalid_grant but got: $RESP4"
  exit 1
fi

echo ""
echo "=== All checks passed ==="
echo "Keycloak recorded REFRESH_TOKEN_ERROR events (audit) in the Events log:"
echo "  $EVENTS_URL"
