# Sprint 002 - Starters, Event System, Service Template

| Field | Value |
| --- | --- |
| Sprint | 002 |
| Epic | EPIC-002 Starter System, EPIC-003 Event-Driven System, EPIC-004 Service Standardization |
| Phase | P0 - Platform Foundation |
| Status | IN PROGRESS |
| Progress | 7/9 |
| Started | 2026-06-20 |
| Completed | - |

## Goal

Expose platform-core as Spring Boot starters (ADR-018), stand up the event-driven foundation
(ADR-009, ADR-019), and standardize service creation (ADR-017).

## Tasks

| ID | Task | Status | Notes |
| --- | --- | --- | --- |
| T-006 | Build Spring Boot starters (api, mediator, security, outbox, inbox, observability) | DONE | All six starters build with auto-configuration and imports |
| T-007 | Transactional outbox + inbox write-side (JDBC stores, Flyway V900/V901, Debezium-friendly schema) | DONE | `starter-outbox`, `starter-inbox`; optional polling relay disabled by default |
| T-008 | Avro event contracts + versioning convention (`domain.event.v1`) | DONE | `platform-event-contracts`: EventEnvelope + 5 MVP schemas, generated and compiled |
| T-009 | ApiResult wrapper + global exception handler starter (ADR-015) | DONE | `starter-api`: ApiResult/ApiError/ApiMeta mapping, no stack-trace leakage |
| T-010 | Schema Registry runtime integration (Confluent serdes + registry wiring) | TODO | Needs the local infra stack (BL-01); contracts ready |
| T-011 | Debezium CDC delivery deployment (outbox connector -> Kafka) | DEFERRED | Needs Kafka Connect + Debezium in the infra stack (BL-01) |
| T-012 | Service template + generator support (ADR-017) | DONE | `microservices/service-template` (copyable skeleton) + `microservices/reference-service` (full showcase); parent pom + `ApiResponseFactory`. Generator script/archetype optional (backlog). |
| T-038 | Outbox reliability: retry_count + error_message, eventId payload enrichment, stuck-sweeper | DONE | Sweeper on by default (monitoring); eventId aids inbox idempotency |
| T-039 | Optional DB log-persistence starter + ApiError.logId (local/test) | DONE | `starter-log-persistence`, disabled by default; Loki stays the prod default (ADR-012) |

## Definition of Done

- All starters auto-configure cleanly and are consumable via `platform-bom`.
- Event contracts versioned and registered; a service can publish via the outbox and a consumer
  deduplicates via the inbox end to end (requires BL-01 infra for the full path).
- A new service can be scaffolded from the ADR-017 template with correct dependencies.

## Outcome (so far)

Full reactor `mvn install` green across 17 modules. Remaining: Schema Registry runtime (T-010),
Debezium deployment (T-011, deferred to infra), and the ADR-017 service template (T-012).

## Dependencies

- T-010 and T-011 depend on BL-01 (local infrastructure stack).

## Agent Assignments

- Platform Engineer Agent -> starters, outbox/inbox
- Event Integration Agent -> Kafka, Avro, Schema Registry, Debezium
- Architecture Agent -> validation, service template
- Tech Lead Agent -> final approval
