# CLAUDE.md - reference-service

Service-level operating notes. Inherits the platform rules in the root `CLAUDE.md` and the ADRs.

## Identity

- Architecture Mode: CQRS + MEDIATOR (ADR-004).
- Base package: `com.telco.reference`.
- Owns the `reference` PostgreSQL database (database-per-service, ADR-006).

## Layout

- `api/` - thin controllers (HTTP -> mediator -> ApiResult).
- `application/` - commands, queries, handlers, DTOs, versioned event payloads.
- `domain/` - aggregates (JPA entities here for brevity).
- `infrastructure/` - repositories and adapters.

## Rules for this service

- Depend ONLY on platform starters (ADR-018).
- Commands mutate state and publish domain events via the outbox (`OutboxService`); the mediator
  TransactionBehavior makes the DB write and the outbox row atomic. Do NOT publish to Kafka directly.
- The `aggregateType` passed to `outboxService.publish(...)` is the Debezium ROUTING key and MUST be
  the lowercase domain (e.g. `reference`) - it becomes the `<domain>.events` topic. It is NOT the
  PascalCase DDD/audit entity-type. Use a SEPARATE PascalCase constant for audit entity-types and a
  distinct lowercase constant for outbox routing (never share one symbol across both). See
  `platform/PLATFORM-SPEC.md` Section 5.
- Event types follow `domain.event.v1` (ADR-009, ADR-019).
- Queries never change state; missing resources raise `ResourceNotFoundException` (-> 404).
- External APIs use `/api/v1` and return `ApiResult<T>` (ADR-015).
- Schema changes ship as Flyway migrations under `db/migration`; platform tables come from
  `classpath:db/migration/platform`.
- No emojis.
