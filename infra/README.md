# Telco CRM - Local Infrastructure

Docker Compose stack for running the platform dependencies locally: PostgreSQL, Redis, Kafka
(KRaft), Schema Registry, Kafka Connect (Debezium), and optional Keycloak, Kafka UI, and the
observability stack (Prometheus, Grafana, Loki, Tempo, OpenTelemetry Collector). Versions follow
[ADR-003](../architecture/adr/ADR-003-technology-stack.md).

This stack satisfies backlog item BL-01 and unblocks the event-driven path (Schema Registry,
Debezium) for Sprint 002 T-010/T-011.

## Layout

```text
infra/
├── Makefile                      convenience targets (run from infra/)
├── README.md
└── docker/
    ├── compose.yml               all services, grouped by profile
    ├── .env.example              image tags, ports, local credentials (copy to .env)
    ├── postgres/initdb/          database-per-service bootstrap (ADR-006)
    ├── kafka-connect/
    │   ├── connectors/           Debezium connector definitions (+ example)
    │   └── register-connectors.sh
    ├── keycloak/realm/           optional realm import (auth profile)
    └── observability/
        ├── prometheus/prometheus.yml
        ├── loki/loki-config.yml
        ├── tempo/tempo.yml
        ├── otel-collector/otel-collector-config.yaml
        └── grafana/provisioning/ datasources + dashboard provider
```

## Quick start

```bash
cd infra
make init     # creates docker/.env from the template
make up       # start core infra (postgres, redis, kafka, schema-registry, kafka-connect)
make ps       # check status
```

Add optional stacks (composable):

```bash
make observability   # + prometheus, grafana, loki, tempo, otel-collector
make tools           # + kafka-ui
make auth            # + keycloak
make up-all          # everything at once
```

Tear down:

```bash
make down      # stop containers, keep data
make destroy   # stop containers and delete data volumes (full reset)
```

Prefer raw compose? Run from `infra/docker`:

```bash
cp .env.example .env
docker compose up -d
docker compose --profile observability up -d
```

## Profiles

| Profile | Services | Default |
| --- | --- | --- |
| (core) | postgres, redis, kafka, schema-registry, kafka-connect | starts by default |
| `tools` | kafka-ui | opt-in |
| `observability` | prometheus, grafana, loki, tempo, otel-collector | opt-in |
| `auth` | keycloak | opt-in |

## Endpoints (default ports)

| Service | URL / host port |
| --- | --- |
| PostgreSQL | `localhost:5432` (user/pass from `.env`) |
| Redis | `localhost:6379` (password from `.env`) |
| Kafka (host listener) | `localhost:29092` (in-cluster: `kafka:9092`) |
| Schema Registry | http://localhost:8081 |
| Kafka Connect | http://localhost:8083 |
| Kafka UI (tools) | http://localhost:8088 |
| Keycloak (auth) | http://localhost:8085 |
| Prometheus | http://localhost:9090 |
| Grafana | http://localhost:3000 (admin / `.env` password) |
| Loki | http://localhost:3100 |
| Tempo | http://localhost:3200 |
| OTLP (collector) | grpc `localhost:4317`, http `localhost:4318` |

## How services connect

- Each microservice uses its own database (see `postgres/initdb`), for example
  `jdbc:postgresql://localhost:5432/customer`.
- Services publish through the transactional outbox (`starter-outbox`); Debezium captures the
  `outbox_event` table and routes events to Kafka. Register a connector per service:

  ```bash
  cd infra/docker/kafka-connect/connectors
  cp outbox-connector.example.json customer-outbox-connector.json   # edit dbname/slot
  cd ../../.. && make register-connectors
  ```

  The example maps the platform outbox columns (`aggregate_type`, `aggregate_id`, `event_type`,
  `payload`) via the Debezium `EventRouter`, routing to `<aggregate_type>.events` topics.
- For tracing/metrics/logs, point services at the OTLP collector endpoint `localhost:4317`.

## Notes

- `.env` holds local-only credentials and is gitignored; only `.env.example` is committed.
- Image tags are centralized in `.env` so they are easy to bump. If a specific tag is unavailable
  for your host architecture, adjust it there.
- PostgreSQL runs with `wal_level=logical` so Debezium CDC works out of the box (ADR-005).
- This stack is for local development only; production uses Kubernetes (ADR-014).
