# Getting Started

This page gets a new machine from zero to a running slice of the platform.

## Prerequisites

| Tool | Version | Needed for |
| --- | --- | --- |
| Java | 21 (JDK) | Building and running every Spring Boot module |
| Maven | 3.9+ | The platform and microservices reactor builds |
| Docker Desktop (or equivalent) | recent | The local infrastructure stack (Postgres, Kafka, Redis, Keycloak, ...) |
| Node.js | 20 LTS | The `frontend/web` SvelteKit app |
| `make` | any | The root `Makefile` is the single entry point for almost everything |

The root `Makefile` pins `JAVA_HOME` to a Java 21 install so builds are reproducible even if a
different JDK is first on `PATH`.

## First-time setup

```bash
# Create infra/docker/.env from the template, then build and install the platform
# BOM + core modules + starters into the local Maven repo
make setup
```

`infra/docker/.env` holds local-only credentials and image tags (Postgres, Redis, Keycloak admin,
an `ENCRYPT_KEY` placeholder you should replace with `openssl rand -hex 32` for anything beyond a
throwaway sandbox). It is gitignored; only `.env.example` is committed.

## Daily development loop

```bash
# Start the minimum dev stack: core infra (Postgres, Redis, Kafka, Schema Registry,
# Kafka Connect, MinIO) + Keycloak
make dev

# In a second terminal, run one service against that stack
cd microservices
mvn spring-boot:run -pl customer-service -Dspring-boot.run.profiles=dev
```

Run any other service the same way, swapping `-pl <service-name>`. Each service reads its
configuration from `microservices/configs/<service-name>/` via Spring Cloud Config (dev profile);
see [ADR-010](../adr/ADR-010-service-discovery-and-configuration-strategy.md).

## Bringing up more of the stack

| `make` target | What comes up |
| --- | --- |
| `infra-up` | Core only: Postgres, Redis, Kafka, Schema Registry, Kafka Connect, MinIO |
| `infra-platform` | Core + `config-server`, `discovery-server`, `api-gateway` (built as Docker images first via `infra-platform-build`) |
| `infra-auth` | Core + Keycloak (this is what `make dev` uses) |
| `infra-observability` | Core + Prometheus, Grafana, Loki, Tempo, the OTel Collector |
| `infra-tools` | Core + Kafka UI |
| `infra-apps` | Core + platform + all 13 domain services as Docker images (`infra-apps-build` first) |
| `infra-up-all` | Everything above at once |
| `infra-up-full-stack` | The full acceptance stack: core + auth + platform + apps |
| `infra-down` | Stop all containers, keep data volumes |
| `infra-destroy` | Stop all containers **and delete data volumes** (full reset) |
| `infra-connectors` | Register the Debezium CDC connectors once `kafka-connect` is healthy |
| `infra-logs S=<container>` | Tail logs for one container, e.g. `make infra-logs S=telco-kafka` |

```bash
# Build every module, then bring up the full stack including observability
make full
```

!!! warning "Memory budget"
    The full stack (auth + platform + all 13 domain services + observability) is heavy - plan on
    a Docker VM with roughly 12 GiB or more. Every container has an explicit JVM heap cap so an
    uncapped JVM cannot silently size itself off host RAM and hang Docker Desktop. If you are RAM
    constrained, bring up only the profiles you need (`infra-auth` + one or two services is
    usually enough for feature work).

## Ports cheat-sheet

| Component | Port |
| --- | --- |
| api-gateway | 8080 |
| discovery-server (Eureka) | 8761 |
| config-server | 8888 |
| identity-service | 9001 |
| customer-service | 9002 |
| product-catalog-service | 9003 |
| order-service | 9004 |
| subscription-service | 9005 |
| usage-service | 9006 |
| billing-service | 9007 |
| payment-service | 9008 |
| notification-service | 9009 |
| ticket-service | 9010 |
| campaign-service | 9011 |
| dispute-service | 9012 |
| fraud-service | 9013 |
| web-bff | 9020 |
| frontend/web (Vite dev server) | 3000 |
| Keycloak | 8085 (admin console) |
| Postgres | 5432 |
| Redis | 6379 |
| Kafka (host) | 29092 |
| Schema Registry | 8081 |
| Kafka Connect | 8083 |
| Kafka UI (`infra-tools`) | 8088 |
| MinIO S3 API / console | 9000 / 9090 |
| Grafana | 3000 (only when the frontend dev server is not also running) |
| Prometheus | 9090 |

## Running the web frontend

```bash
cd frontend/web
cp .env.example .env          # PUBLIC_BFF_BASE_URL, Keycloak PKCE settings
npm ci
npm run dev                   # http://localhost:3000
```

The app authenticates against Keycloak with Authorization Code + PKCE (client `telco-web`) and,
per [ADR-022](../adr/ADR-022-frontend-and-bff-strategy.md), may call the API Gateway directly for
thin slices while `web-bff` grows to compose the rest. There is no Dockerfile for the frontend in
this repo - it runs as a Node process, not a container, in local development.

## Build and test

```bash
make build-platform   # Install platform BOM, core modules, and starters
make build-services   # Compile all microservices
make build             # Both of the above
make test              # Full microservice test suite (JaCoCo skipped locally; enforced in CI)
```

## API Explorer (Postman)

Two collections live in `postman/`: `Telco-CRM-Via-Gateway` (everything through the gateway at
`http://api.localhost:8080`, including chained end-to-end **Journeys**) and
`Telco-CRM-Direct-Services` (each service on its own port, bypassing the gateway). Both
auto-fetch and refresh Keycloak JWTs.

```bash
# One-time: map api.localhost to 127.0.0.1
make postman-hosts-mac     # macOS / Linux
make postman-hosts-win     # Windows PowerShell, run as Administrator
```

Import `postman/collections/` and `postman/environments/`, pick the matching environment, fill in
`keycloak_username` / `keycloak_password`, and run. Full guide: `postman/README.md`.

## Verifying the stack is healthy

- Every Spring Boot service exposes `/actuator/health` - `curl http://localhost:9002/actuator/health`.
- Eureka dashboard (dev/local service discovery): `http://localhost:8761`.
- Kafka UI (`make infra-tools`): `http://localhost:8088`.
- Keycloak admin console: `http://localhost:8085` (bootstrap admin from `infra/docker/.env`).
- Grafana (`make infra-observability`): dashboards for the platform overview and
  circuit-breaker state - see [Observability](observability.md).

## Next steps

- [Development Workflow](development-workflow.md) for the coding patterns (CQRS + Mediator,
  service template, Flyway, tests) you will actually use day to day.
- [Deployment & Operations](deployment-and-operations.md) for Kubernetes/Helm and CI/CD.
- [FAQ](faq.md) for the questions every new engineer asks in the first week.
