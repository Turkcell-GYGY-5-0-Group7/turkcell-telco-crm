# telco-dependencies Helm chart

Dev-grade, in-cluster stateful dependencies for the Telco CRM platform (task 15.2.3),
mirroring the local Docker Compose stack in `infra/docker/compose.yml`. Target cluster:
Kind. This is the foundation layer the per-service app charts (15.2.1 / 15.2.2) build on.

## What it provisions

All objects land in the `telco` namespace. Every dependency Kubernetes Service is named
**identically to its Docker Compose service name**, so services running with the Spring
`docker` profile resolve them by the same short DNS names, unchanged:

| Service (DNS)     | Kind         | Ports                               | Purpose |
| ----------------- | ------------ | ----------------------------------- | ------- |
| `postgres`        | StatefulSet  | 5432                                | Primary store; per-service DBs + Debezium role via init SQL |
| `redis`           | StatefulSet  | 6379                                | Cache / idempotency |
| `mongo`           | StatefulSet  | 27017                               | notification-service document store (ADR-006) |
| `kafka`           | StatefulSet  | 9092 (broker), 9093 (controller)    | KRaft single node (ADR-009) |
| `schema-registry` | Deployment   | 8081                                | Avro contract governance |
| `kafka-connect`   | Deployment   | 8083                                | Debezium CDC worker |
| `minio`           | StatefulSet  | 9000 (S3), 9090 (console)           | Object store (KYC docs, invoice PDFs) |
| `keycloak`        | Deployment   | 8080 (http), 9000 (management)      | IdP / JWT issuer; realm imported |
| `otel-collector`  | Deployment   | 4317 (grpc), 4318 (http), 8889      | OTLP ingest / metrics scrape |
| `tempo`           | Deployment   | 3200 (http), 4317 (otlp)            | Trace storage |
| `loki`            | Deployment   | 3100                                | Log storage |
| `prometheus`      | Deployment   | 9090                                | Metrics + SLO alerts |
| `grafana`         | Deployment   | 3000                                | Dashboards |

## Secrets

All credentials render into Kubernetes Secrets with dev-only defaults (mirroring the
compose `${X:-telco}` defaults). No real secret is committed. Override at install time,
or let the 15.2.2 secrets layer supply pre-created Secrets.

| Secret                  | Keys |
| ----------------------- | ---- |
| `postgres-credentials`  | `POSTGRES_USER`, `POSTGRES_PASSWORD` |
| `redis-credentials`     | `REDIS_PASSWORD` |
| `minio-credentials`     | `MINIO_ROOT_USER`, `MINIO_ROOT_PASSWORD` |
| `keycloak-credentials`  | `KC_BOOTSTRAP_ADMIN_USERNAME`, `KC_BOOTSTRAP_ADMIN_PASSWORD`, `KC_DB_USERNAME`, `KC_DB_PASSWORD` |
| `grafana-credentials`   | `GF_SECURITY_ADMIN_PASSWORD` |

Per-service Postgres DB users/passwords (identity/identity, customer/customer, ...) are
created by the init SQL in `files/postgres/`, reused verbatim from the compose initdb -
dev-only, unchanged so the compose and cluster environments stay identical.

## Validate (no install)

    helm lint deploy/helm/dependencies
    helm template telco-deps deploy/helm/dependencies --namespace telco

Do not `helm install` or create a Kind cluster from CI author steps; live verification
is handled separately.
