# Sprint 14 — Task 14.1.1 (infrastructure half) — Resume Notes

Full-stack, gateway-driven acceptance test run. **DevOps agent half only.**
A parallel **qa** agent authors `microservices/acceptance-tests` (Maven module). We do NOT touch
that module; it does NOT touch compose/CI. Stable contract between the two halves:

- Gateway reachable at `http://localhost:8080`.
- Acceptance suite reads base URLs + a Keycloak token endpoint from env, with localhost defaults.

## Current status: RESEARCH COMPLETE, NOTHING WRITTEN YET

The repo is clean. No files created/edited. All design decisions below are ready to implement.

## Scope (only these files may change)
- `infra/docker/compose.yml`
- `infra/Makefile`
- root `Makefile`
- `.github/workflows/acceptance.yml` (NEW — do NOT edit existing `.github/workflows/ci.yml`)
- `infra/docker/kafka-connect/**` (connector defs)

Do NOT edit `microservices/**/src`, poms, existing workflows, or STATUS.md/sprint README. No emojis.

## Build notes (this machine)
- mvn wrapper: `/Users/winkoffice/.m2/wrapper/dists/apache-maven-3.9.15/9925cc1d/bin/mvn` (mvn NOT on PATH).
- Docker running; core infra (postgres/redis/kafka/schema-registry/kafka-connect/minio) is UP + healthy.
- Dockerfiles already handle `-Dschema.registry.skip=true`. All 10 domain Dockerfiles exist and
  `EXPOSE` correct ports (verified 9001-9010).
- Do NOT run a full 10-image build to completion; validating `docker compose config` + CI wiring is enough.

---

## Deliverable 1 — compose.yml `apps` profile (10 domain services)

Mirror the existing `api-gateway` block. Add near the top (after `x-restart: &restart`) shared
YAML anchors, and 10 service blocks at the end of the `services:` map.

Anchors to add:
```yaml
x-app-env: &app-env
  CONFIG_SERVER_URI: http://config-server:8888
  CONFIG_SERVER_USER: ${CONFIG_SERVER_USER:-config}
  CONFIG_SERVER_PASSWORD: ${CONFIG_SERVER_PASSWORD:-config}
  SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE:-dev},docker
  EUREKA_USERNAME: ${EUREKA_USERNAME:-eureka}
  EUREKA_PASSWORD: ${EUREKA_PASSWORD:-eureka}
  REDIS_PASSWORD: ${REDIS_PASSWORD:-telco}

x-app-depends: &app-depends
  config-server:
    condition: service_healthy
  discovery-server:
    condition: service_healthy
  kafka:
    condition: service_healthy
  postgres:
    condition: service_healthy

x-app: &app-common
  restart: *restart
  profiles: [apps]
  environment: *app-env
  depends_on: *app-depends
  networks: [telco]
```

Each of the 10 services (identity 9001, customer 9002, product-catalog 9003, order 9004,
subscription 9005, usage 9006, billing 9007, payment 9008, notification 9009, ticket 9010):
```yaml
  identity-service:
    <<: *app-common
    build:
      context: ../../
      dockerfile: microservices/identity-service/Dockerfile
    image: telco-identity-service:local
    container_name: telco-identity-service
    ports:
      - "${IDENTITY_SERVICE_PORT:-9001}:9001"
    healthcheck:
      test: ["CMD-SHELL", "curl -fsS http://localhost:9001/actuator/health | grep -q UP || exit 1"]
      interval: 15s
      timeout: 10s
      retries: 15
      start_period: 90s
```
Repeat for all 10, changing name/dockerfile/image/container_name/port (in ports mapping AND
healthcheck URL). image = `telco-<svc>:local`. Add matching `*_SERVICE_PORT` defaults to
`infra/docker/.env.example` (optional but consistent).

Notes verified during research:
- Services with a per-service `configs/<svc>/application-docker.yml` hardcode their DB
  user/pass inline and keycloak jwks-uri, so NO DB env vars are needed on the container — env
  matches api-gateway exactly. GAP: `identity, customer, subscription, billing, notification,
  ticket` currently have NO `application-docker.yml` (only product-catalog, order, usage, payment
  do). Those 6 will fall back to `application-dev.yml` (127.0.0.1) and likely fail to reach
  postgres/redis inside Docker. This is a CONFIG gap OUTSIDE our scope (microservices/configs).
  **Report it to the parent / qa / tech-lead**; the live boot the user drives will surface it.
  (Do not fix by editing configs unless scope is expanded.)
- depends_on config/discovery are in `platform` profile; compose auto-starts them as deps of
  started `apps` services, so `--profile apps` pulls them. `up-full-stack` enables profiles
  explicitly anyway.

## Deliverable 2 — Makefile targets

`infra/Makefile` add:
```make
.PHONY: apps-build
apps-build: init ## Build Docker images for all 10 domain services (apps profile)
	$(COMPOSE) --profile apps build

.PHONY: apps
apps: init ## Start core + platform + all 10 domain services (apps profile)
	$(COMPOSE) --profile platform --profile apps up -d

.PHONY: up-apps
up-apps: apps ## Alias for 'apps'

.PHONY: up-full-stack
up-full-stack: init ## Start core + auth + platform + all domain services (full acceptance stack)
	$(COMPOSE) --profile auth --profile platform --profile apps up -d
```
Also add `--profile platform --profile apps` to the existing `down` and `destroy` targets so full
teardown/rollback stops the app + platform containers too.

Root `Makefile` add (delegating, `infra-` prefix per existing style):
```make
.PHONY: infra-apps-build
infra-apps-build: ## Build Docker images for all 10 domain services
	$(INFRA) apps-build

.PHONY: infra-apps
infra-apps: ## Start core + platform + all 10 domain services
	$(INFRA) apps

.PHONY: infra-up-full-stack
infra-up-full-stack: ## Start the full acceptance stack (core + auth + platform + apps)
	$(INFRA) up-full-stack
```

## Deliverable 3 — kafka-connect connectors (one per service, all 10 publish events)

All 10 domain services publish domain events (verified via service-catalog.md). Each owns its DB
with an `outbox_event` table (starter-outbox V900). The example
`connectors/outbox-connector.example.json` stays as-is (skipped by the script). Add 10 REAL
`<svc>-outbox-connector.json` files (identity, customer, product-catalog, order, subscription,
usage, billing, payment, notification, ticket).

Per-service values (service : dbname : prefix):
- identity : identity_db : identity
- customer : customer_db : customer
- product-catalog : product_catalog_db : product_catalog
- order : order_db : order
- subscription : subscription_db : subscription
- usage : usage_db : usage
- billing : billing_db : billing
- payment : payment_db : payment
- notification : notification_db : notification   (Mongo primary, but PostgreSQL outbox lives here)
- ticket : ticket_db : ticket

Each file is a copy of the example with:
- `name`: `<svc>-outbox-connector`
- `database.dbname`: `<dbname>`
- `topic.prefix`: `<prefix>` (underscores, no dashes)
- `slot.name`: `<prefix>_outbox_slot`
- `publication.name`: `<prefix>_outbox_pub`
- keep `database.user/password` = debezium/debezium (per .env DEBEZIUM_DB_USER/PASSWORD)
- keep EventRouter mapping EXACTLY: id / aggregate_id (key) / event_type (value.type) /
  payload (value) / `event_type:header:eventType` / route.by.field=aggregate_type /
  route.topic.replacement=`${routedByValue}.events`. This matches infra/README outbox columns.

The generation loop (rejected last time — re-run it) writes all 10; note `${DEBEZIUM_*}` and
`${routedByValue}` must be escaped `\${...}` inside the heredoc so the shell does not expand them.

`register-connectors.sh` already POSTs every non-example `*.json`. Connectors can only be created
AFTER each service booted and ran Flyway (outbox_event must exist) — so in CI register connectors
AFTER services are healthy.

## Deliverable 4 — .github/workflows/acceptance.yml (NEW)

CI home for the FULL AC-01/02/03 matrix (reduced-locally, full-in-CI). Steps:
1. checkout (actions/checkout@v7), setup-java@v5 temurin 21 (cache maven) — matches ci.yml style.
2. `export ENCRYPT_KEY=$(openssl rand -hex 32)` (shell env beats .env; config-server needs it).
   `cp infra/docker/.env.example infra/docker/.env`.
3. Build images: `docker compose --env-file infra/docker/.env -f infra/docker/compose.yml
   --profile platform --profile apps build` (or `make -C infra apps-build platform-build`).
4. Bring up full stack: core + auth + platform + apps
   (`docker compose ... --profile auth --profile platform --profile apps up -d`
   or `make -C infra up-full-stack`). ENCRYPT_KEY must be exported for these commands too.
5. Wait for gateway + all services healthy: poll `curl -fsS http://localhost:8080/actuator/health`
   and each service actuator (host ports 9001-9010), with a timeout loop.
6. Register Debezium connectors: `make -C infra register-connectors` (or run the script).
7. Install platform to local repo: `mvn -f platform/pom.xml install -DskipTests
   -Dspotbugs.skip=true -Dschema.registry.skip=true` (CI has mvn on PATH via setup-java).
8. Run acceptance module. **ASSUMED CONTRACT (put in a comment to reconcile with qa):**
   `mvn -f microservices/pom.xml -pl acceptance-tests -am -Pacceptance verify`
   Pass env for the suite with localhost defaults, e.g.:
   `GATEWAY_BASE_URL=http://localhost:8080`,
   `KEYCLOAK_TOKEN_URI=http://localhost:8085/realms/telco-crm/protocol/openid-connect/token`
   (host Keycloak port is 8085 per .env). Note these var names are a CONTRACT to reconcile with qa.
9. Teardown ALWAYS (`if: always()`): `make -C infra destroy` (or `docker compose ... down -v`).
   Rollback path = full teardown; every deploy reproducible + reversible (golden rule).
10. Trigger: `workflow_dispatch` + `pull_request` (label-gated or path-filtered is fine); heavy
    job so likely NOT on every push. Keep `permissions: contents: read`.

## VERIFY (must pass before done)
```
docker compose -f infra/docker/compose.yml --profile apps config
docker compose -f infra/docker/compose.yml --profile auth --profile platform --profile apps config
```
Both must render valid YAML with all `${...}` resolved (needs infra/docker/.env present — run
`make -C infra init` first, and export ENCRYPT_KEY or it will error on config-server).
Fix any errors. Do NOT need the full stack to boot.

## Report back to parent (final message content)
- Files changed (absolute paths).
- apps-profile service matrix (10 svc -> port -> image).
- Connector coverage: example was customer-only; ADDED 10 real connectors (list). Confirm routing
  matches outbox columns aggregate_type/aggregate_id/event_type/payload -> `<aggregate_type>.events`.
- Exact CI command contract assumed for acceptance module (see step 8).
- `docker compose config` validation result.
- FLAG the missing `application-docker.yml` gap for 6 services (see Deliverable 1 notes).

---

## PROMPT TO PASTE INTO A NEW CHAT

> You are the **devops** agent continuing Sprint 14, task 14.1.1 (infrastructure half): make the
> whole Telco CRM platform runnable via Docker Compose and wire a CI job that boots it and runs the
> acceptance suite. A parallel qa agent owns `microservices/acceptance-tests` — you do NOT touch it.
> Stable contract: gateway at `http://localhost:8080`; the suite reads base URLs + a Keycloak token
> endpoint from env with localhost defaults.
>
> Research is already done and captured in `/Users/winkoffice/Desktop/turkcell-telco-crm/todo2.md`.
> Read that file first, then implement all four deliverables exactly as specified there:
> (1) an `apps` profile in `infra/docker/compose.yml` with all 10 domain services (identity 9001 …
> ticket 9010) mirroring the api-gateway block via shared YAML anchors;
> (2) `apps-build` / `apps` / `up-apps` / `up-full-stack` targets in `infra/Makefile` + delegating
> `infra-apps-build` / `infra-apps` / `infra-up-full-stack` in the root `Makefile`, and add
> `--profile platform --profile apps` to `down`/`destroy`;
> (3) 10 real Debezium `<svc>-outbox-connector.json` files in
> `infra/docker/kafka-connect/connectors/` (keep the `.example.json`), routing outbox columns to
> `<aggregate_type>.events`;
> (4) a NEW `.github/workflows/acceptance.yml` that builds platform + apps images, brings up
> core+auth+platform+apps, registers connectors, waits for gateway+services healthy, runs
> `mvn -f microservices/pom.xml -pl acceptance-tests -am -Pacceptance verify` (note this as a
> contract comment), then tears down with `if: always()`.
>
> SCOPE: only compose.yml, infra/Makefile, root Makefile, .github/workflows/acceptance.yml,
> infra/docker/kafka-connect/**. Do NOT edit microservices/**/src, poms, existing workflows, or
> STATUS.md/sprint README. No emojis.
>
> VERIFY with `docker compose -f infra/docker/compose.yml --profile apps config` and the all-profiles
> variant (run `make -C infra init` and export ENCRYPT_KEY first). Do not boot the full stack — the
> user drives the live boot. Then report: files changed, service matrix, connector coverage, the
> assumed acceptance mvn command contract, the compose-config result, and FLAG the missing
> `application-docker.yml` for identity/customer/subscription/billing/notification/ticket.
