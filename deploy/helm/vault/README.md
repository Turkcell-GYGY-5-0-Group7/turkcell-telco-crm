# telco-vault Helm chart

HashiCorp Vault, in-cluster, as the platform's centralized secret store (ADR-025,
task 18.1.1). Wraps the official `hashicorp/vault` chart as a dependency (see
`Chart.yaml`), pinned to:

- **Standalone server, single node** (`server.standalone.enabled: true`,
  `server.ha.enabled: false`) - not the chart's replicated HA mode.
- **Integrated Storage (Raft)** as the storage backend
  (`server.ha.raft.enabled: true`, overriding the chart's default file-storage
  config; see the comment in `values.yaml` for why the Raft config key lives
  under `server.ha.raft` even though HA replication itself is off).
- **Vault Agent sidecar injector disabled** (`injector.enabled: false`) - ADR-025
  Section 1 rejects the injector in favor of the Secrets Store CSI Driver +
  Vault CSI provider (Feature 18.3). Nothing in this release configures or
  consumes Vault-sourced secrets yet.

Singleton posture (deploy/helm/README.md "HPA / PDB"): one replica, no HPA, no
PodDisruptionBudget - identical reasoning to config-server/discovery-server.

## This is a manual, non-automated MVP step

A freshly-installed Vault pod is **sealed** and unusable until an operator runs
the one-time `vault operator init` + `vault operator unseal` procedure. This is
a deliberate MVP posture (ADR-025 Section 1: "a single-node Vault with
Shamir-sealed manual unseal is an accepted MVP posture"), not an oversight -
auto-unseal is explicitly out of scope at this platform's current scale. The
full procedure is documented in
[`deploy/RUNBOOK.md`](../../RUNBOOK.md#3-vault-initialization-and-unseal).

**Unseal keys and the root token are never committed to this repository.**
Store them the same way any other production secret is handled operationally
(a password manager / sealed-secret store outside version control) - see the
RUNBOOK section above for the full guidance.

## Validate (no cluster needed)

```sh
helm dependency update deploy/helm/vault
helm lint deploy/helm/vault
helm template vault deploy/helm/vault -n telco
```

## Install

```sh
helm install vault deploy/helm/vault -n telco
# `rollout status` does not work here (OnDelete update strategy); `wait
# --for=condition=ready` hangs until after unseal (readiness probe runs
# `vault status`, which fails while sealed). Wait on Initialized instead.
kubectl -n telco wait --for=condition=Initialized pod -l app.kubernetes.io/name=vault --timeout=180s
```

Then follow the init/unseal procedure in `deploy/RUNBOOK.md`.
