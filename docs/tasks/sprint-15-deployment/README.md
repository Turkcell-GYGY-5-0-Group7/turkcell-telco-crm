# Sprint 15 - Deployment

| Status | Progress | Last updated |
| --- | --- | --- |
| DONE (features); exit-criteria follow-ups tracked | 5/5 | 2026-07-12 |

Legend: DONE / IN PROGRESS / TODO / BLOCKED / DEFERRED. Cross-sprint rollup: [../STATUS.md](../STATUS.md).

## Objective

Make the platform deployable to Kubernetes with full CI/CD: per-service container images,
Kubernetes manifests/Helm charts with config and secrets, horizontal pod autoscaling, a complete
build-test-scan-push-deploy pipeline, and a verified rollback path. This is the final MVP sprint.

Covers NFR-03 (HPA), NFR-04 (uptime), and the CI/CD/deployment requirements (ADR-014).

## Included Epics

- Epic 15: Containerization, Kubernetes, and CI/CD

## Features (one file per top-level task)

| ID | Feature | Status | File |
| --- | --- | --- | --- |
| 15.1 | Containerization | DONE | [15.1-containerization.md](15.1-containerization.md) |
| 15.2 | Kubernetes Manifests | DONE | [15.2-kubernetes-manifests.md](15.2-kubernetes-manifests.md) |
| 15.3 | Autoscaling and Resilience | DONE | [15.3-autoscaling-and-resilience.md](15.3-autoscaling-and-resilience.md) |
| 15.4 | CI/CD Pipeline and Rollback | DONE | [15.4-ci-cd-pipeline-and-rollback.md](15.4-ci-cd-pipeline-and-rollback.md) |
| 15.5 | Release Documentation | DONE | [15.5-release-documentation.md](15.5-release-documentation.md) |

## Sprint Deliverables

- Production container images for all 13 services pushed by CI.
- Kubernetes manifests/Helm charts with ConfigMaps/Secrets, in-cluster dependencies, HPA, PDBs, and
  a complete build-test-scan-push-deploy pipeline with verified rollback and post-deploy smoke tests.
- An operations runbook.

## Exit Criteria

- The full platform deploys to a Kubernetes cluster via CI/CD; services are stateless and autoscale
  (NFR-03); rolling deploys preserve availability (NFR-04).
- A bad deploy is caught by smoke tests and rolled back automatically.
- All MVP acceptance criteria (validated in Sprint 14) hold in the deployed environment; the platform
  is operable from the runbook.

## Exit-Criteria Follow-Ups (status)

The five features are DONE and individually verified (much of it live on Kind). Two of the three
tracked follow-ups that blocked the "AC hold in the deployed environment" exit criterion are now
RESOLVED; one remains.

- RESOLVED 2026-07-12 - schema-registry exit-1 in-cluster. Root cause was NOT the originally-inferred
  KafkaStore-init timeout; confirmed live as a Kubernetes service-link env collision (the Service named
  `schema-registry` injects `SCHEMA_REGISTRY_PORT`, which cp-schema-registry's entrypoint reads as the
  deprecated PORT setting and hard-exits 1 before contacting Kafka). Fix: `enableServiceLinks: false`
  on the schema-registry Deployment pod spec (`deploy/helm/dependencies/templates/schema-registry.yaml`).
  Verified live: Running 1/1, 0 restarts, `/subjects` serving.
- RESOLVED 2026-07-12 - product-catalog 500 on GET /api/v1/tariffs in-cluster. Environmental, not a
  code defect (the list endpoint is uncached; the earlier 500 occurred during a thrashing/partial-wave
  cluster state). Returned HTTP 200 with the correct ApiResult shape once the dependency layer was
  healthy. No code change.
- REMAINING - full 13-service in-cluster boot + deployed-environment acceptance run (AC-01/02/03). The
  local Kind cluster currently runs the dependency layer + config/discovery/gateway/product-catalog;
  the other 9 domain services are not yet imaged/deployed there, and the 10 Debezium outbox connectors
  are not registered. This is the always-deferred "full boot" and is the one item still standing
  between "feature-complete + deployable" and "AC proven green in Kubernetes". Tracked in
  [../todo.md](../todo.md) (Step 3).
</content>
