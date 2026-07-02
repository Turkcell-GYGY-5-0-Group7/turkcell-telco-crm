#!/usr/bin/env bash
# Adds api.localhost -> 127.0.0.1 to /etc/hosts so the API gateway can be reached
# at http://api.localhost:8080 without a port conflict on any running service.
# Requires sudo on macOS and Linux.

set -euo pipefail

ENTRY="127.0.0.1  api.localhost"
HOSTS="/etc/hosts"

if grep -qF "api.localhost" "$HOSTS"; then
  echo "api.localhost is already in $HOSTS — nothing to do."
  exit 0
fi

echo "Adding '$ENTRY' to $HOSTS (requires sudo)..."
echo "$ENTRY" | sudo tee -a "$HOSTS" > /dev/null
echo "Done. Verify with: ping -c1 api.localhost"
