# CLAUDE.md - subscription-service

Service-level operating notes. Inherits the platform rules in the root `CLAUDE.md` and the ADRs.

## Identity

- Architecture Mode: CQRS + MEDIATOR (ADR-004).
- Base package: `com.telco.subscription`.
- Infrastructure Profile: PostgreSQL primary store (ADR-006); owns the `subscription` database
  (database-per-service).
- Bounded context: subscription lifecycle plus number and SIM inventory. Subscription state machine
  (ACTIVE -> SUSPENDED -> ACTIVE, ACTIVE/SUSPENDED -> TERMINATED), MSISDN pool allocation
  (FREE -> RESERVED -> ALLOCATED, release back to FREE) and SIM-card assignment. Participates in the
  onboarding saga (AC-01). Covers FR-13, FR-15. Contract:
  `docs/api-contracts/subscription-service.md`.

## Layout

- `api/` - thin controllers (HTTP -> mediator -> ApiResult).
- `application/` - commands, queries, handlers, DTOs, versioned event payloads.
- `domain/` - aggregates (JPA entities here for brevity).
- `infrastructure/` - repositories and adapters (audit, inbox consumers).

## Rules for this service

- Depend ONLY on platform starters (ADR-018). Never import `platform-core` modules directly.
- Controllers are thin: translate HTTP to commands/queries, dispatch via `Mediator`, return
  `ApiResult<T>`. No business logic in controllers (ADR-008).
- Commands mutate state and publish domain events via the outbox (`OutboxService`); the mediator
  TransactionBehavior makes the DB write and the outbox row atomic. Do NOT publish to Kafka directly.
- Event types follow `domain.event.v1` (ADR-009, ADR-019). Saga events consumed from the order /
  payment / customer domains are processed idempotently via the inbox (ADR-005, AC-01).
- Queries never change state; missing resources raise `ResourceNotFoundException` (-> 404).
- Subscription state machine: ACTIVE <-> SUSPENDED, and ACTIVE/SUSPENDED -> TERMINATED. MSISDN pool
  state machine: FREE -> RESERVED -> ALLOCATED, with RESERVED bounded by `reserved_until`. Illegal
  transitions raise `BusinessRuleException`. (State-machine rules are delivered in Feature 9.2/9.3.)
- MSISDN allocation is atomic: a number is held (RESERVED) then committed (ALLOCATED) or released
  (FREE); no two subscriptions share a live MSISDN.
- Audit logging is mandatory for state-changing operations: write an `audit_log` row (ADR-021,
  NFR-12). subscription is one of the four audit-mandated services.
- Schema changes ship as Flyway migrations under `db/migration`; platform tables (outbox V900, inbox
  V901) come from `classpath:db/migration/platform`.
- The locally-built audit writer is flagged for migration to a platform starter per
  `docs/architecture/platform-capabilities.md` - keep it isolated for easy extraction.
- No emojis.

## Status

Feature 9.1 (scaffold + schema): service skeleton, Flyway schema (`subscriptions`, `msisdn_pool`,
`sim_cards`, `audit_log`) and MSISDN pool seed. Domain logic, handlers and the onboarding saga are
delivered by Features 9.2 / 9.3 (domain-engineer).
