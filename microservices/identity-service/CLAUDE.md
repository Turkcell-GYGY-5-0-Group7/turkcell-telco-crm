# CLAUDE.md - identity-service

Service-level operating notes. Inherits the platform rules in the root `CLAUDE.md` and the ADRs.

## Identity

- Architecture Mode: CQRS + MEDIATOR (ADR-004).
- Base package: `com.telco.identity`.
- Infrastructure Profile: PostgreSQL (ADR-006); owns the `identity` database (database-per-service).
- Authentication boundary: Keycloak is the identity provider and issues tokens (ADR-011). This service
  does NOT mint JWTs. It administers users/roles via the Keycloak Admin API and owns the app-specific
  authorization data (a domain projection of users, roles, permissions) and the audit log.
  `starter-security` validates Keycloak JWTs.

## Layout

- `api/` - thin controllers (HTTP -> mediator -> ApiResult).
- `application/` - commands, queries, handlers, DTOs, versioned event payloads.
- `domain/` - aggregates (JPA entities here for brevity).
- `infrastructure/` - repositories and adapters.

## Rules for this service

- Depend ONLY on platform starters (ADR-018). Never import `platform-core` modules directly.
- Controllers are thin: translate HTTP to commands/queries, dispatch via `Mediator`, return
  `ApiResult` via `ApiResponseFactory`. No business logic in controllers (ADR-008).
- Commands mutate state and publish domain events via the outbox (`OutboxService`); the mediator
  TransactionBehavior makes the DB write and the outbox row atomic. Do NOT publish to Kafka directly.
- Event types follow `domain.event.v1` (ADR-009, ADR-019); this service publishes `user.created.v1`.
- Queries never change state; missing resources raise `ResourceNotFoundException` (-> 404).
- External APIs use `/api/v1` and return `ApiResult<T>` (ADR-015).
- `users` is a domain projection - credentials live in Keycloak, so there is no `password_hash` and
  no `refresh_tokens` table.
- Audit logging is mandatory: every change is written to the `audit_log` table (ADR-021, NFR-12).
- Schema changes ship as Flyway migrations under `db/migration`; platform tables come from
  `classpath:db/migration/platform`.
- No emojis.
