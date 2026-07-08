#!/usr/bin/env bash
# Registers every connector JSON in ./connectors with the local Kafka Connect (Debezium) instance.
# Usage: ./register-connectors.sh [connect-url]   (default http://localhost:8083)
# Files ending in .example.json are skipped; copy them to <service>-outbox-connector.json first.
#
# Pre-creates each Postgres PUBLICATION (idempotently) as the target database's own
# OWNER role before registering the connector. Debezium's PostgresConnectorTask only
# runs CREATE PUBLICATION when the named publication does not already exist yet, so
# doing it here — using a role that already has full rights on its own database —
# avoids ever granting the shared `debezium` CDC role CREATE privilege on any database
# (least privilege; matches the tech-lead ruling to never broaden the CDC role beyond
# REPLICATION+CONNECT+SELECT). This must run here rather than in initdb, because the
# outbox_event table does not exist until each service's own Flyway migration (v900,
# platform outbox) has run — which only happens after the service is up, i.e. exactly
# the point in the boot sequence where this script itself is invoked.

set -euo pipefail

CONNECT_URL="${1:-http://localhost:8083}"
POSTGRES_CONTAINER="${POSTGRES_CONTAINER:-telco-postgres}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONNECTORS_DIR="${SCRIPT_DIR}/connectors"

echo "Waiting for Kafka Connect at ${CONNECT_URL} ..."
until curl -fsS "${CONNECT_URL}/connectors" >/dev/null 2>&1; do
  sleep 3
done

# Extracts a single string field from a connector JSON's "config" object without
# requiring jq (not guaranteed to be installed on every dev machine / CI runner).
json_config_field() {
  python3 -c "import json,sys; print(json.load(open(sys.argv[1]))['config'].get(sys.argv[2], ''))" "$1" "$2"
}

# PUT /connectors/<name>/config expects the bare config object (no {"name","config"}
# wrapper) — unlike POST /connectors, which expects the full wrapper. Re-registering
# an already-existing connector (the common local-dev "run it again" case) must use
# this bare form or Kafka Connect returns a 500 trying to deserialize the wrapper as
# a single string-valued config entry.
json_config_only() {
  python3 -c "import json,sys; print(json.dumps(json.load(open(sys.argv[1]))['config']))" "$1"
}

ensure_publication() {
  local db="$1" pub="$2" table="$3"
  # Database owner role name follows the fixed "<name>_db" -> "<name>" convention
  # from infra/docker/postgres/initdb/01-create-databases.sql. Connecting via -U
  # works for the reserved word "order" without quoting (only SQL identifiers used
  # as bare tokens inside a statement need quoting, not psql -U role name args).
  local owner="${db%_db}"
  echo "Ensuring publication ${pub} exists on ${db} (owner: ${owner}) ..."
  PGPASSWORD="${owner}" docker exec -e PGPASSWORD="${owner}" "${POSTGRES_CONTAINER}" \
    psql -v ON_ERROR_STOP=1 -U "${owner}" -d "${db}" -c \
    "DO \$\$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_publication WHERE pubname = '${pub}') THEN EXECUTE format('CREATE PUBLICATION %I FOR TABLE %s', '${pub}', '${table}'); END IF; END \$\$;"
}

shopt -s nullglob
found=0
for file in "${CONNECTORS_DIR}"/*.json; do
  case "${file}" in
    *.example.json) continue ;;
  esac
  found=1
  name="$(basename "${file}")"

  dbname="$(json_config_field "${file}" database.dbname)"
  pubname="$(json_config_field "${file}" publication.name)"
  table="$(json_config_field "${file}" table.include.list)"
  if [[ -n "${dbname}" && -n "${pubname}" && -n "${table}" ]]; then
    ensure_publication "${dbname}" "${pubname}" "${table}"
  fi

  echo "Registering ${name} ..."
  curl -fsS -X POST -H "Content-Type: application/json" \
    --data @"${file}" "${CONNECT_URL}/connectors" \
    || curl -fsS -X PUT -H "Content-Type: application/json" \
        --data "$(json_config_only "${file}")" \
        "${CONNECT_URL}/connectors/$(basename "${file}" .json)/config"
  echo
done

if [[ "${found}" -eq 0 ]]; then
  echo "No connector files found (only *.example.json). Copy an example and edit it first."
fi

echo "Current connectors:"
curl -fsS "${CONNECT_URL}/connectors" || true
echo
