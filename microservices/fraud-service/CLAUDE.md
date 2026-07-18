# CLAUDE.md - fraud-service

Service-level operating notes. Inherits the platform rules in the root `CLAUDE.md` and the ADRs.

## Identity

- Architecture Mode: CQRS + MEDIATOR (ADR-004, ADR-029 Decision Section 2).
- Base package: `com.telco.fraud`.
- Port: 9013.
- Owns the `fraud` PostgreSQL database (`fraud-db`, database-per-service, ADR-006). No other service
  accesses `fraud-db` directly.
- Infrastructure Profile (ADR-006): **PostgreSQL (fraud-db, primary store, event-emitting service);
  Redis optional cache for hot per-customer velocity counters, explicitly not source of truth**
  (design-note.md Section 8). No cache is present in the Feature 23.1 scaffold.

## Bounded context (ADR-029 Section 1)

fraud-service is **read-only relative to subscription-service**: it consumes subscription-service's
already-published events via the inbox and **never accesses `subscription-db` directly** (ADR-006
cross-service data rule). Fraud-detection logic lives here rather than inside subscription-service,
to keep that service's operational, latency-sensitive lifecycle state machine free of a
security-analytics workload with a different scaling/retention profile.

## Layout

- `domain/` - `MsisdnLifecycleSignal`, `FraudRule`, `FraudSignal`, `FraudCase` aggregates plus the
  `MsisdnLifecycleEventType`, `FraudRuleCode`, `FraudSeverity`, `FraudCaseStatus` enums. Bare JPA
  entities only as of Feature 23.1 - no domain behavior methods yet (rule evaluation lands in 23.2).
- `infrastructure/persistence/` - `MsisdnLifecycleSignalRepository`, `FraudRuleRepository`,
  `FraudSignalRepository`, `FraudCaseRepository`, including the rolling-window query methods.
- `infrastructure/config/` - `FraudSecurityConfig` (JWT filter chain wiring, ADR-011).
- `api/` - thin controllers (HTTP -> mediator -> ApiResult). Not populated yet (Feature 23.3).
- `application/` - commands, queries, handlers, DTOs, inbox consumers, versioned event payloads. Not
  populated yet (Features 23.2-23.4).

## Rules for this service

- Depend ONLY on platform starters (ADR-018). Never import `platform-core` modules directly.
- Controllers are thin: translate HTTP to commands/queries, dispatch via `Mediator`, return
  `ApiResult` via `ApiResponseFactory`. No business logic in controllers (ADR-004, ADR-008).
- Commands and queries are immutable records implementing `Command<R>` / `Query<R>`; one handler
  each (`@Component`), resolved by generics. Queries do not change state.
- Consume subscription-service events idempotently via `starter-inbox` (`msisdn.allocated.v1`,
  `msisdn.released.v1`, `subscription.activated.v1`, `subscription.suspended.v1`) - never read
  `subscription-db`. `MSISDN_CHURN_VELOCITY` keys on `customerId`; `msisdn.released.v1` gains a
  BACKWARD-compatible nullable `customerId` in Feature 23.2 (ADR-029 Amendment 1), with a defensive
  fallback that resolves it from the most recent prior `MSISDN_ALLOCATED` row for the same MSISDN.
- `SUSPEND_REACTIVATE_VELOCITY` evaluation SHOULD exclude `reason=NON_PAYMENT` suspensions to
  suppress dunning-cycle false positives (ADR-029 Amendment 3) - handled in 23.2.
- When adding persistence writes: publish domain events through `starter-outbox` (`OutboxService`),
  never directly to Kafka. The `aggregateType` passed to `outboxService.publish(...)` is the
  Debezium routing key and MUST be the lowercase domain (`fraud`) - see `platform/PLATFORM-SPEC.md`
  Section 5. Event types follow `domain.event.v1` (ADR-009, ADR-019).
- Detect-and-alert only (ADR-029 Section 5): fraud-service publishes signal/case events and never
  automatically suspends a subscription. Any hold is a manual agent action via subscription-service's
  existing `POST /api/v1/subscriptions/{id}/suspend`.
- External APIs use `/api/v1` and return `ApiResult<T>` (ADR-015).
- Schema changes ship as Flyway migrations under `db/migration`; platform tables (outbox V900 / inbox
  V901) come from `classpath:db/migration/platform`.
- No emojis anywhere.

## When generating code here

- Put commands/queries/handlers/DTOs and inbox consumers under `application/`, controllers under
  `api/`, JPA entities under `domain/`, repositories under `infrastructure/persistence/`.
- Add focused unit tests for handlers (no Spring needed) plus `@DataJpaTest`/Testcontainers
  repository and Flyway-migration tests, following campaign-service's `CampaignRepositoryTest` /
  `CampaignSchemaMigrationTest` pattern (see the repo's `docs/tasks/lessons.md` Spring Boot 4 test
  slice notes before writing `@DataJpaTest`).
