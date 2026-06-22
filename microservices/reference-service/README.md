# reference-service

**Architecture Mode: CQRS + MEDIATOR** (ADR-004)

A working example that exercises the full platform end to end, so you can see how the pieces fit:

- Thin controller -> `Mediator` -> command/query handlers (ADR-008)
- JPA aggregate persisted to its own PostgreSQL database (ADR-006)
- Domain event published through the **transactional outbox**; the JPA insert and the outbox row
  commit atomically under the mediator TransactionBehavior, then Debezium delivers it (ADR-005, ADR-009)
- `ApiResult` responses with traceId/correlationId via `ApiResponseFactory` (ADR-015)
- Flyway migrations: service schema + platform outbox/inbox tables (ADR-016)
- `starter-inbox` on the classpath for idempotent consumers

## Endpoints

| Method | Path | Description |
| --- | --- | --- |
| POST | `/api/v1/demo-items` | Body `{"name":"widget"}`; creates an item, publishes `demoitem.created.v1` |
| GET | `/api/v1/demo-items/{id}` | Fetch by id (404 -> `ApiResult` error) |
| GET | `/api/v1/demo-items` | List all |
| GET | `/actuator/health` | Liveness |

## Run (needs the infra stack)

```bash
# 1) start infra
cd infra && make up

# 2) ensure the database exists (added to infra/docker/postgres/initdb for fresh installs)
docker exec telco-postgres psql -U telco -d postgres -c "CREATE DATABASE reference;" 2>/dev/null || true

# 3) build platform + run the service
cd ../platform && mvn -q install
cd ../microservices && mvn -pl reference-service -am spring-boot:run

# 4) exercise it
curl -X POST localhost:8102/api/v1/demo-items -H 'Content-Type: application/json' -d '{"name":"widget"}'
curl localhost:8102/api/v1/demo-items
```

After a create, the event lands in the `outbox_event` table (status `NEW`); register the Debezium
connector (see `infra/docker/kafka-connect`) to route it to Kafka.

## Notes

- Depends ONLY on platform starters (ADR-018).
- Security is left off for the demo (`telco.platform.security.enabled=false`); production enables it
  and adds `spring-boot-starter-security` (ADR-011).
- Datasource/port are env-overridable (`DB_HOST`, `DB_NAME`, `SERVER_PORT`, ...).
