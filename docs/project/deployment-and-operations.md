# Deployment & Operations

## Local: Docker Compose

Single compose file, `infra/docker/compose.yml`, driven through `infra/Makefile` (and the root
`Makefile` wrappers - see [Getting Started](getting-started.md#bringing-up-more-of-the-stack)).
Services are grouped by Compose profile:

| Profile | Contents |
| --- | --- |
| *(none, core)* | postgres, redis, mongo, kafka, schema-registry, kafka-connect, minio |
| `platform` | config-server, discovery-server, api-gateway |
| `auth` | keycloak, keycloak-config |
| `observability` | prometheus, loki, tempo, otel-collector, grafana |
| `tools` | kafka-ui |
| `apps` | all 13 domain services + web-bff |

Every container that runs a JVM has an explicit heap cap - an uncapped JVM sizes itself from host
RAM and can hang Docker Desktop, so this is deliberate, not an oversight. A curated 18-container
subset (`SPRINT16_SERVICES` in `infra/Makefile`) exists specifically because the full stack does
not comfortably fit in a typical developer laptop's Docker VM; `make infra-sprint16-e2e` starts
only that subset.

MinIO's console runs on port **9090**, not the more obvious 9001, specifically to avoid clashing
with identity-service's port 9001.

### Change data capture

`infra/docker/kafka-connect/connectors/` holds one Debezium connector definition per service.
Each connects to that service's own `<service>_db` as a dedicated `debezium` user, reads via
PostgreSQL logical replication (`pgoutput`), and routes outbox rows to `<aggregate_type>.events`
topics via Debezium's `EventRouter` transform. `make infra-connectors` registers all of them once
`kafka-connect` reports healthy; `make -C infra register-connectors C="customer order"` registers
a subset.

## Kubernetes: Helm

`deploy/helm/` holds the production-shaped charts, developed and validated against a local Kind
cluster (`deploy/kind/kind-cluster.yaml`):

| Chart | Purpose |
| --- | --- |
| `dependencies` | In-cluster stateful dependencies: postgres, redis, mongo, kafka, schema-registry, kafka-connect, minio, keycloak, and the full observability stack |
| `telco-service` | One reusable chart that renders a single Spring Boot service (Deployment, Service, ConfigMap, Secret/SecretProviderClass, HPA, PDB, Ingress, NetworkPolicies, Linkerd `Server`/`AuthorizationPolicy`) |
| `values/*.yaml` | One values file per service, layered onto `telco-service` - this is how 16 independent Helm releases share one chart |
| `vault` | HashiCorp Vault (standalone, Raft storage) - [ADR-025](../adr/ADR-025-secrets-and-key-management.md) |
| `csi-driver` | Secrets Store CSI Driver (wraps the upstream chart) that syncs Vault secrets into native Kubernetes Secrets |
| `linkerd-crds`, `linkerd-control-plane` | Service mesh - [ADR-026](../adr/ADR-026-service-mesh-and-mtls.md) |

Each service deploys as its **own independent Helm release**, so any one service can be upgraded
or rolled back without touching the others. Non-secret configuration goes through a per-service
ConfigMap; secret configuration goes through a per-service Secret that is either static
(local-dev only) or synced from Vault. HPA (`minReplicas: 2`, `maxReplicas: 5`, target CPU 75%)
and a PodDisruptionBudget (`minAvailable: 1`) are on by default for every service except the two
infrastructure singletons (`config-server`, `discovery-server`), which disable both. Ingress is
nginx, host `telco.local`, and only `api-gateway` is exposed.

Operational entry points: [`deploy/RUNBOOK.md`](https://github.com/turkcell-gygy-5-0-group7/turkcell-telco-crm/blob/master/deploy/RUNBOOK.md)
(deploy/scale/rollback/observe) and [`deploy/ROLLBACK.md`](https://github.com/turkcell-gygy-5-0-group7/turkcell-telco-crm/blob/master/deploy/ROLLBACK.md)
(the per-service Helm rollback procedure).

## Service mesh, secrets, chaos (Sprints 18-20)

- **Vault** ([ADR-025](../adr/ADR-025-secrets-and-key-management.md)): one Vault policy per
  service scoped to that service's own KV path, authenticated via Vault's Kubernetes auth
  method - least privilege, and no application code change since services keep reading secrets
  via `envFrom.secretRef` regardless of whether the Secret behind it is static or CSI-synced.
- **Linkerd** ([ADR-026](../adr/ADR-026-service-mesh-and-mtls.md)): automatic sidecar mTLS on
  every in-cluster hop, paired with default-deny NetworkPolicies delivered in the same sprint.
  Pinned to the `edge` release channel deliberately - the last `stable` OSS release did not
  enforce Layer-1 `AuthorizationPolicy`.
- **Chaos Mesh** (`deploy/chaos/`, extends ADR-012/013, no new ADR): fault-injection experiments
  (pod-kill, latency, network partition) plus a game-day runbook, run against the same Kind
  cluster used for Helm chart development.

## CI/CD (GitHub Actions)

Four workflows, each scoped tightly so unrelated changes don't trigger unrelated pipelines:

**`ci.yml`** - runs on every push/PR to `master`. Builds and unit-tests `platform/`, enforces
Checkstyle + SpotBugs as a static-analysis job, runs a CodeQL security scan, then builds and tests
every microservice with a **hard 70% JaCoCo line-coverage gate per module** (posts the coverage
diff as a PR comment). On an actual merge to `master`, a final job diffs which services changed
and builds+pushes only those Docker images to GHCR, tagged by commit SHA, reactor version, and
`latest`.

**`deploy.yml`** - triggered by a successful `ci.yml` run on `master`, or manually. Gated behind a
required-reviewer GitHub Environment. Spins up a throwaway Kind cluster with Calico (for real
NetworkPolicy enforcement) and ingress-nginx, installs the `dependencies` chart, then every
service as its own Helm release in dependency order, runs a smoke test
(`deploy/smoke/smoke-test.sh`), and attempts a Helm rollback on any failure.

**`acceptance.yml`** - manual, or automatically on PRs touching `microservices/**`, `platform/**`,
or `infra/docker/**`. Brings up the entire Docker Compose stack (auth + platform + apps), waits
for every service's `/actuator/health` to report `UP`, registers the Debezium connectors, runs
the real Schema Registry compatibility check against all registered subjects, and then runs the
full `acceptance-tests` Maven module against the live gateway. Always tears the stack down
afterward (`make -C infra destroy`) so no run leaks state into the next one.

**`frontend-web-ci.yml`** - path-filtered to `frontend/web/**` only, so no backend change ever
triggers a Node build and no frontend change ever triggers a Maven build. Lints (Prettier +
ESLint), type-checks (`svelte-check`), unit-tests (Vitest), and builds the SvelteKit app.

## This documentation site's own pipeline

`.github/workflows/docs.yml` builds this MkDocs site on every PR touching `docs/**`,
`architecture/adr/**`, or `mkdocs.yml` (build-only, catches broken links before merge), and
publishes it to GitHub Pages on every push to `master`.
