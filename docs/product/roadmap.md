# Product Roadmap

## Telco CRM Platform

| Field | Value |
| --- | --- |
| Document | Product Roadmap |
| Version | 1.0 |
| Status | Draft for review |
| Owner | Product Owner Agent (modifications restricted per CLAUDE.md Section 11) |
| Parent | [BRD.md](./BRD.md) |
| Execution detail | [`/.claude/roadmap/`](../../.claude/roadmap/) |
| Last updated | 2026-06-19 |

This roadmap is the product-level view of delivery. The execution-level breakdown (epics,
sprints, tasks, agent assignments) lives in [`/.claude/roadmap/roadmap.md`](../../.claude/roadmap/roadmap.md).
Only the Product Owner Agent and Tech Lead Agent may modify roadmap documents.

---

## 1. Delivery Strategy

The platform is delivered in phases. Phase 0 builds the internal platform foundation that all
services depend on (the custom CQRS/Mediator framework, starters, event system, and service
template). Feature phases then deliver vertical slices of business capability, each ending in
a demonstrable end-to-end scenario.

Sequencing rationale: services are ordered so that each phase unlocks the next saga step.
Identity and the master-data services (customer, catalog) come first; ordering, subscription,
and payment enable the onboarding saga; usage and billing enable the revenue cycle;
notification and ticketing complete engagement and support.

---

## 2. Phase Overview

| Phase | Theme | Primary services | Exit criterion |
| --- | --- | --- | --- |
| P0 | Platform foundation | platform-core, starters, event system, service template | Service template generates a compliant service; outbox/inbox proven |
| P1 | Identity and master data | identity, customer, product-catalog, api-gateway, discovery, config | Authenticated customer registration and catalog browsing work |
| P2 | Onboarding saga | order, subscription, payment | AC-01 New Subscriber Onboarding passes end to end |
| P3 | Revenue cycle | usage, billing | AC-02 Billing and AC-03 Quota Exhaustion pass end to end |
| P4 | Engagement and support | notification, ticket | Notifications dispatched on domain events; ticketing with SLA works |
| P5 | Hardening and release | all (cross-cutting) | NFR targets met; observability, security, K8s deployment validated |

---

## 3. Phase Detail

### P0 - Platform Foundation

Goal: build the internal platform so services are generated consistently.

- Custom CQRS and Mediator engine (ADR-008)
- Pipeline behaviors: validation, security, logging, transaction, performance
- Transactional outbox and inbox (ADR-005, ADR-009)
- Spring Boot starters: mediator, security, outbox, observability (ADR-018)
- Platform BOM and platform-core module structure (ADR-020)
- Event versioning and schema governance (ADR-019)
- Service template standard (ADR-017)

Aligns with execution epics EPIC-001 through EPIC-004.

### P1 - Identity and Master Data

Goal: authenticated access plus the master data needed to order.

- identity-service: login, JWT issuance, roles/permissions (FR-IAM-01..05)
- api-gateway: JWT validation, header propagation, rate limiting
- customer-service: registration, KYC, address/document management (FR-01..04)
- product-catalog-service: tariff/addon/VAS catalog with versioning (FR-05..08)
- discovery-server and config-server (dev mode per ADR-010)

Exit: a customer can register, complete KYC, and browse tariffs through the gateway.

### P2 - Onboarding Saga

Goal: end-to-end new-line activation.

- order-service: order intake and saga orchestration (FR-09..12)
- payment-service: mock PSP, idempotency, retry (FR-25..27)
- subscription-service: activation, MSISDN allocation, lifecycle (FR-13..15)
- Compensation flows and saga state persistence

Exit: AC-01 passes end to end including compensation on failure.

### P3 - Revenue Cycle

Goal: usage-driven billing.

- usage-service: CDR ingestion, quota tracking, threshold events, overage aggregation (FR-17..20)
- billing-service: monthly bill-run, invoice generation, PDF rendering (FR-21..24)
- CDR simulator for test data

Exit: AC-02 and AC-03 pass end to end.

### P4 - Engagement and Support

Goal: customer communication and support.

- notification-service: SMS/email/push, templates, preferences (FR-28..30)
- ticket-service: ticketing, SLA-based assignment, notifications (FR-31..33)

Exit: domain events trigger notifications; tickets are created and assigned by SLA.

### P5 - Hardening and Release

Goal: meet non-functional targets and prepare for production.

- Performance validation (NFR-01, NFR-02) including bill-run at scale
- Full observability rollout (NFR-07..09)
- Security hardening: mTLS, PII encryption, audit logging (NFR-05, NFR-06, NFR-12)
- Kubernetes deployment, HPA, rollback validation (ADR-014)
- Resilience validation: circuit breaker, retry, bulkhead (NFR-10)

Exit: all MVP acceptance criteria pass and NFR targets are demonstrably met.

---

## 4. Milestones

| Milestone | Description | Depends on |
| --- | --- | --- |
| M0 | Platform foundation ready; first service scaffolded from template | P0 |
| M1 | Authenticated onboarding base (register, KYC, catalog) | P1 |
| M2 | New subscriber onboarding saga live (AC-01) | P2 |
| M3 | Revenue cycle live (AC-02, AC-03) | P3 |
| M4 | Engagement and support live | P4 |
| M5 | MVP release candidate; NFR targets met | P5 |

---

## 5. Post-MVP Candidates

Tracked but not scheduled for the MVP (see BRD Section 7.2):

- Prepaid top-up and real-time charging
- Number portability (MNP) state machine (FR-16)
- Corporate customer and fleet management
- Campaign / promotion engine
- BTK regulatory reporting
- Roaming usage tracking
- Production mobile application
- Analytics service (event-sourced projections)

---

## 6. Dependencies Across Phases

```text
P0 ──► P1 ──► P2 ──► P3 ──► P4 ──► P5
              │             ▲
              └── payment ──┘ (saga prerequisite reused by billing auto-pay)
```

- P1 master data is required by P2 ordering and subscription.
- P2 subscription activation is required by P3 billing (active base) and usage (quota owner).
- P3 invoice/usage events are required by P4 notification templates.

---

Document end.
