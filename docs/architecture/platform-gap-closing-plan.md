# Platform Gap-Closing Plan

| Field | Value |
| --- | --- |
| Status | Plan (Tier 1-3 items below still unbuilt); one post-MVP follow-up has shipped outside this tier sequencing - see "Follow-up" |
| Authority | ADR-007 (platform library), ADR-018 (starter model); tech-lead rulings 2026-06-23 |
| Goal | Build the missing cross-cutting capabilities BEFORE domain services so they are not re-implemented per service |
| Last updated | 2026-07-12 |

Gaps and consumer index: [`platform-capabilities.md`](platform-capabilities.md). The horizontal
mechanics (ApiResult, mediator, outbox/inbox, context, masking) are already done; this plan closes the
persistence + cross-cutting plumbing tier.

## Sequencing principle

Close each capability before the first sprint that consumes it. Tier 1 blocks Sprint 05/06.

| Tier | Capability | Module(s) | Consumed first by | Risk |
| --- | --- | --- | --- | --- |
| 1 | BaseEntity + JPA auditing + soft-delete | `starter-persistence` | S05 identity, S06 customer | MED |
| 1 | PII at-rest encryption (AES-GCM converter + key provider) | `starter-persistence` (or `starter-crypto`) | S06 customer, S08 payment | MED |
| 1 | Object storage (MinIO put/get + pre-signed URLs) | `starter-storage` | S06 customer (KYC), S11 billing (PDF) | LOW |
| 1 | Keycloak JWKS validation (ruling 3) | `starter-security` (amend) | S04 gateway, S05 identity | MED |
| 2 | Resilience4j defaults (CB/retry/bulkhead + Feign base) | `starter-resilience` | S08 order/payment | MED |
| 2 | Shared OpenAPI/Springdoc config | `starter-api` (amend) or `starter-openapi` | all services (ARC-08) | LOW |
| 2 | HTTP Idempotency-Key handling on POST | `starter-api` or `starter-inbox` | S08 order/payment | MED |
| 3 | Domain audit (ruling 1) | `platform-core/audit` + `starter-audit` | S05 identity, S06 customer | MED |
| 3 | Avro <-> outbox bridge (ruling 2) | `platform-event-contracts` + `starter-outbox` | every event producer (S06+) | HIGH |
| 3 | Test support (Testcontainers fixtures) | `platform-test` | all services | LOW |

## Tech-lead rulings (2026-06-23) and their implied changes

### Ruling 1 - Domain audit = platform capability
- New `platform-core/audit` (`com.telco.platform.audit`, Spring-free): `AuditEvent` record, `AuditWriter`
  port, `@Audited` annotation, `AuditBehavior` at new `PipelineOrder.AUDIT = 450` (after TRANSACTION,
  before PERFORMANCE) so the audit row commits inside the command transaction.
- New `starter-audit`: `JdbcAuditWriter`, `AuditAutoConfiguration`, `AuditProperties`
  (`telco.platform.audit.*`), Flyway `V901__platform_audit.sql` (note: V901 currently used by inbox -
  pick the next free Vxxx at build time).
- Audit rows retain real values (NOT masked) - they are durable business records, not telemetry
  (ADR-021 masking does not apply).
- Actions: new **ADR-023 Domain Audit Strategy**; amend PLATFORM-SPEC (new audit section + add
  `AUDIT=450` to the PipelineOrder table); BOM unaffected.

### Ruling 2 - Avro/outbox = store the Avro EventEnvelope (option a) [HIGH risk]
- Bridge `EventEnvelopeFactory.from(SpecificRecord v1, eventType, eventId, CorrelationContext)` in
  `platform-event-contracts` (it already owns `EventEnvelope` + the `*V1` records).
- New `AvroEnvelopeEventSerializer` in `starter-outbox`, default serializer; keep `JacksonEventSerializer`
  behind `telco.platform.outbox.serialization=avro|json` (default `avro`).
- Outbox `payload` column migrates `jsonb -> bytea` (new `Vxxx__outbox_payload_bytea.sql`); update
  `JdbcOutboxStore`, `OutboxRecord.payload`, and drop the `::jsonb` cast.
- `eventId` moves into the envelope; inbox idempotency keys read the envelope field, not a JSON property.
- Actions: amend ADR-009 (outbox payload is Avro bytes) and ADR-019 (EventEnvelopeFactory is the only
  sanctioned bridge); amend PLATFORM-SPEC sections 5, 7, 10. **Highest-risk item - touches the event
  backbone and a live table; do it before any service emits events (i.e. before S06).**

### Ruling 3 - Keycloak JWKS = Spring oauth2-resource-server
- `starter-security`: add `spring-security-oauth2-resource-server`; build `JwtDecoder` from
  `telco.platform.security.jwt.issuer-uri`/`jwks-uri`; `JwtAuthenticationConverter` maps Keycloak
  `realm_access.roles`/`roles` into `UserContext`.
- `JwtProperties.Jwt`: add `issuerUri`, `jwksUri`; deprecate `secret`/`publicKey`/`expirySeconds`.
- **Retire `JwtService.issue()`** (platform must not issue tokens, ADR-011); reduce `JwtService` to a
  claims/roles extractor or replace with the converter. Keep gateway-trust mode.
- Actions: amend PLATFORM-SPEC 9.3 (remove "identity-issuing services"); add an implementation note to
  ADR-011 (jwks-uri validation); BOM: ensure oauth2-resource-server is managed (Spring Boot BOM covers).

## BOM additions (`platform-bom`)

Pin before the consuming module is built: `spring-data-mongodb` (notification), MinIO SDK (storage),
`springdoc-openapi` (OpenAPI), `mapstruct` (DTO mapping). `resilience4j-bom` and `avro` already pinned;
`spring-security-oauth2-resource-server` comes via the Spring Boot BOM.

## ADR actions summary

| ADR | Action |
| --- | --- |
| ADR-006 | Done (Infrastructure Profile, MinIO, event-emitter=relational) |
| ADR-007 | Reference: "new capability = new module" governs all additions here |
| ADR-009 | Amend: outbox payload is Avro `EventEnvelope` bytes |
| ADR-019 | Amend: `EventEnvelopeFactory` is the only sanctioned `*V1` -> envelope bridge |
| ADR-011 | Add implementation note: validate via oauth2-resource-server jwks-uri |
| ADR-023 | New: Domain Audit Strategy |

## Recommended build order

1. **Avro/outbox bridge (ruling 2)** - highest risk, must land before any service emits events.
2. **starter-persistence + PII crypto + starter-security JWKS** - Tier 1, unblock S05/S06.
3. **starter-storage** - before S06 KYC / S11 PDF.
4. **starter-audit (ADR-023)** - before S05/S06 audit requirements.
5. **starter-resilience, OpenAPI, idempotency** - before S08.
6. **platform-test** - alongside, to DRY the integration tests.

Each module follows ADR-017/PLATFORM-SPEC conventions, ships unit/wiring tests (close BL-02), and is
added to the capability catalog Section 1 when done.

## Follow-up (shipped after the Tier plan)

Capabilities identified and built after this plan's original Tier 1-3 sequencing was authored, outside
that sequencing (post-MVP, not blocking any Sprint 05-15 MVP work):

| Capability | Module(s) | First consumers | Risk |
| --- | --- | --- | --- |
| Distributed locking (ADR-024, Sprint 17) | `platform-core/lock` + `starter-lock` | `subscription-service` MSISDN reservation-expiry reaper (17.3), `billing-service` bill-run run-level lock (17.4) | MED |

Distributed locking was not part of this plan's original gap analysis (no service needed cross-instance
coordination until the Sprint 15 HPA made it a real requirement) and needed no amendment to this
document's Tier table - it is recorded here as a follow-up entry instead, per the same "added to the
capability catalog Section 1 when done" close-out this plan already requires of every Tier item.
