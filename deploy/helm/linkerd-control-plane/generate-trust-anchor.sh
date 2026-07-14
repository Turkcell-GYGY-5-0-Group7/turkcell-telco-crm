#!/usr/bin/env bash
# Generates Linkerd's self-managed mTLS trust anchor (root CA) and issuer
# certificate (Feature 19.1.1, ADR-026 Section 4: self-managed trust anchor,
# no cert-manager, no external CA, no dependency on deploy/helm/vault's PKI).
#
# This is a ONE-TIME, per-environment operator step - the same posture
# deploy/RUNBOOK.md Section 3 already documents for Vault's Shamir unseal
# keys: real key material, generated locally, NEVER committed to this
# repository, NEVER pasted into a values file, ConfigMap, commit, or CI log.
#
# Uses plain `openssl` (no `step` CLI dependency) to produce the same shape
# Linkerd's own docs describe via `step`: an ECDSA (P-256) self-signed root CA
# ("root.linkerd.cluster.local") and an ECDSA intermediate issuer cert
# ("identity.linkerd.cluster.local") signed by that root, marked CA:true so
# Linkerd's identity component can mint short-lived leaf certs for each meshed
# pod from the issuer key.
#
# Usage:
#   ./generate-trust-anchor.sh <output-dir>
#   ./generate-trust-anchor.sh /tmp/linkerd-trust
#
# Produces (in <output-dir>, 0600-permissioned): ca.crt, ca.key, issuer.crt,
# issuer.key. Feed ca.crt / issuer.crt / issuer.key into the control-plane
# install via --set-file - see deploy/helm/linkerd-control-plane/values.yaml
# and deploy/RUNBOOK.md's Linkerd install step.
set -euo pipefail

OUT_DIR="${1:?usage: generate-trust-anchor.sh <output-dir>}"
mkdir -p "$OUT_DIR"
chmod 700 "$OUT_DIR"
cd "$OUT_DIR"

if [[ -f ca.crt || -f issuer.crt ]]; then
  echo "Trust anchor material already exists in $OUT_DIR - refusing to overwrite." >&2
  echo "Remove it first if you intend to regenerate (this rotates the mesh root, re-meshing every pod)." >&2
  exit 1
fi

echo "Generating root CA (root.linkerd.cluster.local)..."
openssl ecparam -name prime256v1 -genkey -noout -out ca.key
openssl req -x509 -new -nodes -key ca.key -sha256 -days 3650 \
  -subj "/CN=root.linkerd.cluster.local" \
  -addext "basicConstraints=critical,CA:true" \
  -addext "keyUsage=critical,keyCertSign,cRLSign" \
  -out ca.crt

echo "Generating issuer certificate (identity.linkerd.cluster.local), signed by the root..."
openssl ecparam -name prime256v1 -genkey -noout -out issuer.key
openssl req -new -key issuer.key -subj "/CN=identity.linkerd.cluster.local" -out issuer.csr
printf 'basicConstraints=critical,CA:true,pathlen:0\nkeyUsage=critical,keyCertSign,cRLSign\n' > issuer.ext
openssl x509 -req -in issuer.csr -CA ca.crt -CAkey ca.key -CAcreateserial \
  -days 365 -sha256 \
  -extfile issuer.ext \
  -out issuer.crt

rm -f issuer.csr ca.srl issuer.ext
chmod 600 ca.key ca.crt issuer.key issuer.crt

echo
echo "Wrote ca.crt / ca.key / issuer.crt / issuer.key to $OUT_DIR"
echo "ca.key is only needed if you intend to re-sign a new issuer cert later (e.g. issuer rotation" \
     "before the 365-day expiry above) - it is NOT passed to helm install. Keep it, and issuer.key," \
     "out of version control (same handling as Vault's unseal keys, deploy/RUNBOOK.md Section 3)."
