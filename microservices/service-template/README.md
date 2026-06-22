# service-template

**Architecture Mode: CQRS + MEDIATOR** (ADR-004)

The canonical ADR-017 starting point for a new microservice. It is intentionally minimal and has no
persistence, so it compiles and runs standalone. Copy it, rename, then add the pieces your domain
needs (JPA, Flyway, outbox) following `reference-service`.

## What it demonstrates

- Platform wiring through starters only (ADR-018): `starter-api`, `starter-mediator`,
  `starter-security`, `starter-observability`.
- A thin controller that dispatches via the `Mediator` and returns `ApiResult` (ADR-015).
- A query (`PingQuery`) and a command (`EchoCommand`) with their handlers, auto-registered by
  starter-mediator (resolved by generics).
- Bean-validation on the command, enforced by the mediator ValidationBehavior.

## Endpoints

| Method | Path | Description |
| --- | --- | --- |
| GET | `/api/v1/ping` | Returns service status (query path) |
| POST | `/api/v1/echo` | Body `{"message":"hi"}`; echoes it (command path) |
| GET | `/actuator/health` | Liveness |

## Run

```bash
# from the repo root, after `cd platform && mvn install`
cd microservices && mvn -pl service-template -am spring-boot:run
curl localhost:8101/api/v1/ping
curl -X POST localhost:8101/api/v1/echo -H 'Content-Type: application/json' -d '{"message":"hi"}'
```

## Creating a new service from this template

1. Copy `service-template/` to `microservices/<your-service>/`.
2. In `pom.xml`: change `<artifactId>` (and add the module to `microservices/pom.xml`).
3. Rename the base package `com.telco.template` -> `com.telco.<service>`.
4. Set `spring.application.name`, `server.port`, and the `Dockerfile` port/jar.
5. Declare your architecture mode in this README (keep or change to SIMPLE SERVICE LAYER).
6. Add capabilities as needed: persistence (JPA + Flyway), `starter-outbox` to publish events,
   `starter-inbox` for idempotent consumers, `starter-security` activation (add
   `spring-boot-starter-security`). See `reference-service` for the full pattern.

## Rules (must keep)

- Depend ONLY on platform starters, never on platform-core directly (ADR-018, ADR-020).
- Controllers contain no business logic; all operations go through the Mediator (ADR-004, ADR-008).
- External APIs use `/api/v1` and return `ApiResult<T>` (ADR-015).
- No emojis.
