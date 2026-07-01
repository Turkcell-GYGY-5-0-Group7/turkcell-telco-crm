#!/usr/bin/env bash
# Registers all Avro schemas with the Schema Registry.
# Run once after `make up` to seed the registry before compatibility checks matter.
# Usage: ./register-schemas.sh [SCHEMA_REGISTRY_URL]
# Default URL: http://localhost:8081
set -euo pipefail

SR_URL="${1:-${SCHEMA_REGISTRY_URL:-http://localhost:8081}}"
AVRO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../src/main/avro" && pwd)"

register() {
    local subject="$1"
    local file="$2"
    local body
    body=$(jq -Rs '{schema: ., schemaType: "AVRO"}' "$file")
    local http_code
    http_code=$(curl -s -o /dev/null -w "%{http_code}" \
        -X POST \
        -H "Content-Type: application/vnd.schemaregistry.v1+json" \
        -d "$body" \
        "$SR_URL/subjects/$subject/versions")
    if [[ "$http_code" == "200" ]] || [[ "$http_code" == "409" ]]; then
        echo "  OK  $subject (HTTP $http_code)"
    else
        echo "  ERR $subject (HTTP $http_code)"
        exit 1
    fi
}

echo "Registering Avro schemas to $SR_URL ..."
register "event.envelope.v1"         "$AVRO_DIR/EventEnvelope.avsc"
register "customer.registered.v1"    "$AVRO_DIR/customer-registered.avsc"
register "order.created.v1"          "$AVRO_DIR/order-created.avsc"
register "payment.completed.v1"      "$AVRO_DIR/payment-completed.avsc"
register "subscription.activated.v1"    "$AVRO_DIR/subscription-activated.avsc"
register "invoice.generated.v1"        "$AVRO_DIR/invoice-generated.avsc"
register "cdr.recorded.v1"             "$AVRO_DIR/cdr-recorded.avsc"
register "usage.recorded.v1"           "$AVRO_DIR/usage-recorded.avsc"
register "quota.threshold-reached.v1"  "$AVRO_DIR/quota-threshold-reached.avsc"
register "quota.exceeded.v1"           "$AVRO_DIR/quota-exceeded.avsc"
register "usage.aggregated.v1"         "$AVRO_DIR/usage-aggregated.avsc"
echo "Done."
