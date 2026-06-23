# Sprint 15 - Deployment

| Status | Progress | Last updated |
| --- | --- | --- |
| TODO | 0/5 | 2026-06-22 |

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
| 15.1 | Containerization | TODO | [15.1-containerization.md](15.1-containerization.md) |
| 15.2 | Kubernetes Manifests | TODO | [15.2-kubernetes-manifests.md](15.2-kubernetes-manifests.md) |
| 15.3 | Autoscaling and Resilience | TODO | [15.3-autoscaling-and-resilience.md](15.3-autoscaling-and-resilience.md) |
| 15.4 | CI/CD Pipeline and Rollback | TODO | [15.4-ci-cd-pipeline-and-rollback.md](15.4-ci-cd-pipeline-and-rollback.md) |
| 15.5 | Release Documentation | TODO | [15.5-release-documentation.md](15.5-release-documentation.md) |

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
</content>
