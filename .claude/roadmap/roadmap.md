# Platform Roadmap

## System Model

Epic -> Sprint -> Tasks (Hybrid Agile Execution Model)

Only the Product Owner Agent and Tech Lead Agent may modify roadmap documents (CLAUDE.md
Section 11). Product-level phases are in [`docs/product/roadmap.md`](../../docs/product/roadmap.md).

---

## Status Legend

| Status | Meaning |
| --- | --- |
| DONE | Completed and verified (builds/tests pass or acceptance met) |
| IN PROGRESS | Actively being worked; some tasks complete |
| TODO | Planned, not started |
| BLOCKED | Cannot proceed until a dependency is resolved |
| DEFERRED | Intentionally postponed (for example, needs infrastructure not yet stood up) |

---

## Current State

| Field | Value |
| --- | --- |
| Current phase | P0 - Platform Foundation |
| Active sprint | Sprint 002 - Starters, Event System, Service Template |
| Last completed | Sprint 001 - Platform Core Bootstrap (DONE) |
| Last updated | 2026-06-22 |

---

## Sprint Tracker

| Sprint | Title | Epic(s) | Phase | Status | Progress |
| --- | --- | --- | --- | --- | --- |
| [001](sprints/sprint-001.md) | Platform Core Bootstrap | EPIC-001 | P0 | DONE | 5/5 |
| [002](sprints/sprint-002.md) | Starters, Event System, Service Template | EPIC-002, EPIC-003, EPIC-004 | P0 | IN PROGRESS | 7/9 |
| [003](sprints/sprint-003.md) | Identity and Master Data | EPIC-005 | P1 | TODO | 0/5 |
| [004](sprints/sprint-004.md) | Onboarding Saga | EPIC-006 | P2 | TODO | 0/5 |
| [005](sprints/sprint-005.md) | Revenue Cycle | EPIC-007 | P3 | TODO | 0/5 |
| [006](sprints/sprint-006.md) | Engagement and Support | EPIC-008 | P4 | TODO | 0/5 |
| [007](sprints/sprint-007.md) | Hardening and Release | EPIC-009 | P5 | TODO | 0/5 |

---

## Platform Epics

### EPIC-001: Platform Core Foundation - DONE

Goal: build the framework-agnostic internal platform.

Includes: CQRS engine, Mediator engine, Outbox core, Inbox core, common API/exception/context.
Delivered in Sprint 001 (`platform-core/*`, builds green, 14 unit tests).

### EPIC-002: Spring Boot Starter System - DONE

Goal: expose platform-core as Spring Boot starters (ADR-018).

Includes: starter-api, starter-mediator, starter-security, starter-outbox, starter-inbox,
starter-observability. Delivered in Sprint 002.

### EPIC-003: Event-Driven System - IN PROGRESS

Goal: Kafka + Avro ecosystem (ADR-009, ADR-019).

Includes: Avro event contracts and versioning (DONE), transactional outbox write-side (DONE),
Schema Registry runtime integration (TODO), Debezium CDC delivery deployment (DEFERRED - needs
the local infrastructure stack).

### EPIC-004: Microservice Architecture Standardization - IN PROGRESS

Goal: enforce service templates (ADR-017).

Includes: ADR-017 service template (DONE - `microservices/service-template` + `reference-service`,
standardized project structure), service-generator agent/archetype support (TODO, backlog).

---

## Product Epics

Product epics deliver business capability on top of the platform. They map to the phases in
[`docs/product/roadmap.md`](../../docs/product/roadmap.md); requirement IDs (FR-XX) are in
[`docs/product/requirements.md`](../../docs/product/requirements.md).

### EPIC-005: Identity and Master Data (Phase P1) - TODO

Goal: authenticated access plus the master data required to order.
Includes: identity-service (FR-IAM-01..05); api-gateway JWT validation, header propagation, rate
limiting; customer-service registration and KYC (FR-01..04); product-catalog-service catalog and
versioning (FR-05..08); discovery-server and config-server (dev mode, ADR-010).
Exit: a customer can register, complete KYC, and browse tariffs through the gateway.

### EPIC-006: Onboarding Saga (Phase P2) - TODO

Goal: end-to-end new-line activation (AC-01).
Includes: order-service saga orchestration (FR-09..12); payment-service mock PSP, idempotency,
retry (FR-25..27); subscription-service activation, MSISDN allocation, lifecycle (FR-13..15);
compensation flows and saga state.
Exit: AC-01 passes end to end including failure compensation.

### EPIC-007: Revenue Cycle (Phase P3) - TODO

Goal: usage-driven billing (AC-02, AC-03).
Includes: usage-service CDR ingestion, quota tracking, threshold events (FR-17..20);
billing-service bill-run, invoice generation, PDF (FR-21..24); CDR simulator.
Exit: AC-02 and AC-03 pass end to end.

### EPIC-008: Engagement and Support (Phase P4) - TODO

Goal: customer communication and support.
Includes: notification-service channels, templates, preferences (FR-28..30); ticket-service
ticketing and SLA assignment (FR-31..33).
Exit: domain events trigger notifications; SLA-based ticketing works.

### EPIC-009: Hardening and Release (Phase P5) - TODO

Goal: meet non-functional targets and prepare for production.
Includes: performance validation (NFR-01, NFR-02); observability rollout (NFR-07..09); security
hardening - mTLS, PII storage encryption, PII telemetry masking (ADR-021), audit log (NFR-05,
NFR-06, NFR-12); Kubernetes deployment, HPA, rollback (ADR-014); resilience validation (NFR-10).
Exit: all MVP acceptance criteria pass and NFR targets are met.

---

## Backlog (not yet scheduled into a sprint)

| ID | Item | Rationale | Candidate sprint | Status |
| --- | --- | --- | --- | --- |
| BL-01 | Local infrastructure stack (Docker Compose: PostgreSQL, Kafka/KRaft, Schema Registry, Debezium Connect, Redis, observability) | Required to exercise the event system end to end | Pre-Sprint 003 enabler | DONE (`infra/`) |
| BL-02 | Starter integration tests (ApplicationContextRunner wiring tests per starter) | Starters are currently verified by compilation/wiring only | Sprint 002 stretch | TODO |
| BL-03 | platform-observability micrometer/OTel exporter wiring (Tempo) | Correlation filter is in place; tracer export still to wire | Sprint 007 | TODO |
| BL-04 | Wire Checkstyle + SpotBugs into the platform POMs and the CI static-analysis stage (ADR-014) | Done for the platform reactor (gates bound to verify, CI enforces); extending to the microservices reactor remains | Sprint 002/007 | DONE (platform); microservices TODO |

Note: BL-01 done unblocks Sprint 002 T-010 (Schema Registry runtime) and T-011 (Debezium delivery) -
the infrastructure now exists; the service-side wiring of those remains.
