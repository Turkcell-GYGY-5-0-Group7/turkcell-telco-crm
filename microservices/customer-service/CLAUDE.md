# CLAUDE.md - customer-service

Service-level operating notes. Inherits the platform rules in the root `CLAUDE.md` and the ADRs.

## Identity

- Architecture Mode: CQRS + MEDIATOR (ADR-004).
- Base package: `com.telco.customer`.
- Infrastructure Profile: PostgreSQL primary store + MinIO for KYC document binaries (ADR-006); owns
  the `customer` database (database-per-service).
- Bounded context: customer master record. Registration with TCKN/VKN validation, KYC workflow
  (PENDING -> ACTIVE/REJECTED), address + document management, soft-delete (KVKK/GDPR), PII encryption
  at rest. Covers FR-01..FR-04. Contract: `docs/api-contracts/customer-service.md`.

## Layout

- `api/` - thin controllers (HTTP -> mediator -> ApiResult).
- `application/` - commands, queries, handlers, DTOs, versioned event payloads.
- `domain/` - aggregates (JPA entities here for brevity).
- `infrastructure/` - repositories and adapters (storage, audit).

## Rules for this service

- Depend ONLY on platform starters (ADR-018). Never import `platform-core` modules directly.
- Controllers are thin: translate HTTP to commands/queries, dispatch via `Mediator`, return
  `ApiResult<T>`. No business logic in controllers (ADR-008).
- Commands mutate state and publish domain events via the outbox (`OutboxService`); the mediator
  TransactionBehavior makes the DB write and the outbox row atomic. Do NOT publish to Kafka directly.
- Event types follow `domain.event.v1` (ADR-009, ADR-019); this service publishes
  `customer.registered.v1`, `customer.kyc-approved.v1`, `customer.kyc-rejected.v1`,
  `customer.updated.v1`.
- Queries never change state; missing resources raise `ResourceNotFoundException` (-> 404).
- KYC state machine PENDING -> ACTIVE / REJECTED; illegal transitions raise `BusinessRuleException`.
- PII: the identity number (TCKN/VKN) is encrypted at rest with AES-GCM (JPA `AttributeConverter`,
  NFR-06), returned only masked, and never logged (mark `@Sensitive`, ADR-021). Card-style raw values
  never appear in DTOs or logs.
- Soft-delete: `delete` sets `deleted_at`; default reads exclude soft-deleted rows; the row persists
  (FR-04).
- Audit logging is mandatory for state-changing operations: write an `audit_log` row (ADR-021,
  NFR-12).
- KYC document binaries live in MinIO; the `documents` row stores only the object reference
  (bucket, key, content-type, checksum). Downloads are served via time-limited pre-signed URLs
  (ADR-006). Raw bytes are never stored in the database.
- Schema changes ship as Flyway migrations under `db/migration`; platform tables (outbox) come from
  `classpath:db/migration/platform`.
- Locally-built cross-cutting pieces (AES-GCM converter, soft-delete support, MinIO adapter, audit
  writer) are flagged for migration to platform starters per
  `docs/architecture/platform-capabilities.md` Section 3 - keep them isolated for easy extraction.
- No emojis.
