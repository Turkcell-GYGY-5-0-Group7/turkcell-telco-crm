#!/usr/bin/env bash
# Registers every connector JSON in ./connectors with the local Kafka Connect (Debezium) instance.
# Usage: ./register-connectors.sh [connect-url]   (default http://localhost:8083)
# Files ending in .example.json are skipped; copy them to <service>-outbox-connector.json first.

set -euo pipefail

CONNECT_URL="${1:-http://localhost:8083}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONNECTORS_DIR="${SCRIPT_DIR}/connectors"

echo "Waiting for Kafka Connect at ${CONNECT_URL} ..."
until curl -fsS "${CONNECT_URL}/connectors" >/dev/null 2>&1; do
  sleep 3
done

shopt -s nullglob
found=0
for file in "${CONNECTORS_DIR}"/*.json; do
  case "${file}" in
    *.example.json) continue ;;
  esac
  found=1
  name="$(basename "${file}")"
  echo "Registering ${name} ..."
  curl -fsS -X POST -H "Content-Type: application/json" \
    --data @"${file}" "${CONNECT_URL}/connectors" \
    || curl -fsS -X PUT -H "Content-Type: application/json" \
        --data @"${file}" "${CONNECT_URL}/connectors/$(basename "${file}" .json)/config"
  echo
done

if [[ "${found}" -eq 0 ]]; then
  echo "No connector files found (only *.example.json). Copy an example and edit it first."
fi

echo "Current connectors:"
curl -fsS "${CONNECT_URL}/connectors" || true
echo
