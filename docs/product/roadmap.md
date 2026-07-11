# Product Roadmap

## Telco CRM Platform

| Field | Value |
| --- | --- |
| Document | Product Roadmap |
| Version | 1.0 |
| Status | Draft for review |
| Owner | product-owner agent (with tech-lead approval) |
| Parent | [BRD.md](./BRD.md) |
| Execution detail | [`docs/tasks/`](../tasks/) and [`docs/tasks/STATUS.md`](../tasks/STATUS.md) |
| Last updated | 2026-07-11 |

This roadmap is the product-level view of delivery. The execution-level breakdown (epics,
sprints, tasks, live status) lives in [`docs/tasks/`](../tasks/), rolled up in
[`docs/tasks/STATUS.md`](../tasks/STATUS.md). Only the product-owner and tech-lead agents may
modify roadmap documents.

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
| P6 | Post-MVP depth | web-bff/frontend, `starter-lock`, Vault, Linkerd, Chaos Mesh, campaign-service, dispute-service, fraud-service | Sprints 16-23 documented (README + Proposed ADR each, per-sprint exit criteria); each sprint's own exit criteria met once scheduled and built |

> **Phase-numbering note.** This roadmap's phases are P0-P5 (MVP, complete) and now P6 (documented,
> not yet built - see Section 3). `docs/product/TELCO-CRM-ADVANCED.md` Section 10 independently defines
> its own forward-looking phases **P6-P11** for large, not-yet-scheduled enterprise themes (its P6 =
> "Channels and corporate", P7 = "Real-time charging", P8 = "Zero-trust and compliance", P9 = "Scale and
> resilience", P10 = "Data and intelligence", P11 = "Growth"). **These are two distinct phase sequences
> that happen to share the label "P6" for different content** - this roadmap's P6 is an
> execution-scoped increment (Sprints 16-23, already in `docs/tasks/`) that draws narrow, buildable
> slices from several of ADVANCED's P6/P8/P9/P10/P11 themes without being equivalent to any one of
> them (see the per-sprint cross-references in Section 3 below). When the remainder of ADVANCED's
> P6-P11 scope eventually enters delivery, it continues in *this* roadmap as P7, P8, ... - this
> roadmap never reuses ADVANCED's P6-P11 labels for different content, per ADVANCED.md's own
> no-silent-override governance rule (its header, "Nothing here overrides an existing ADR without a
> superseding note").

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

- Keycloak realm (token issuance) + identity-service: user/roles/permissions management (FR-IAM-01..05)
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

### P6 - Post-MVP Depth

Goal: extend the platform with hardening capabilities the MVP deliberately deferred, and a small set
of new domain services, as scoped, documented, design-reviewed sprints. **As of 2026-07-11, all of
Phase P6 is documentation and design only - no code has been written, no ADR has been ratified, and no
sprint has started build.** See the phase-numbering note in Section 2: this P6 is a roadmap-local
execution increment, distinct from `docs/product/TELCO-CRM-ADVANCED.md`'s own P6-P11 forward-looking
phases, even though both use the same "P6" label for different content.

- Sprint 16 - Web Frontend + Web BFF: SvelteKit UI and a `web-bff` composing domain APIs, Keycloak
  Authorization Code + PKCE login (ADR-022, Accepted). Narrow slice of ADVANCED Section 10 P6
  ("Channels and corporate") - web channel only, no corporate/fleet accounts or e-Fatura.
- Sprint 17 - Distributed Locking: a new `starter-lock` platform module (Redisson-backed) adopted by
  subscription-service's MSISDN reaper and billing-service's bill-run coordination (ADR-024 Proposed).
  Not mapped to a specific ADVANCED phase - a platform-capability gap closure
  (`docs/architecture/platform-capabilities.md` Section 3).
- Sprint 18 - Secret Management: HashiCorp Vault (Kubernetes auth method + Secrets Store CSI driver)
  replacing the Sprint 15 committed-default K8s Secrets (ADR-025 Proposed). Partial slice of ADVANCED
  Section 4.3 (Cryptography and Secrets) / Section 10 P8 ("Zero-trust and compliance") - envelope
  encryption and key-id rotation stay out of scope.
- Sprint 19 - Service Mesh and mTLS: Linkerd sidecar mesh + default-deny NetworkPolicies, closing the
  mTLS deferral in `docs/architecture/security-posture.md` Section 8 (ADR-026 Proposed). Delivers the
  mesh/mTLS half of ADVANCED Section 4.1 / Section 10 P8 ("Zero-trust and compliance"); OPA
  policy-as-code remains future work. Sequenced after Sprint 18 for operational reasons only, not a
  hard technical dependency.
- Sprint 20 - Chaos Engineering: Chaos Mesh fault injection (pod-kill, latency, network-partition) and
  a game-day runbook on the existing Kind/Helm cluster. Extends ADR-012/ADR-013 directly (tech-lead
  ruling: no new ADR). Narrower, single-cluster slice of ADVANCED Section 3.4 / Section 10 P9 ("Scale
  and resilience") - multi-region cells, cross-region DR drills, and RPO/RTO targets stay out of scope.
- Sprint 21 - Campaign / Catalog Validation: a new `campaign-service` (port 9011 proposed) validated
  synchronously by order-service at order time, for dynamic pricing and redemption-limit enforcement
  (ADR-027 Proposed). Buildable subset of the campaign/promotion engine in ADVANCED Section 2.4 /
  Section 10 P11 ("Growth") - no segment/data-platform dependency.
- Sprint 22 - Invoice Dispute / Chargeback: a new `dispute-service` (port 9012 proposed, Domain
  Orchestration) coordinating billing-service and payment-service for dispute/chargeback resolution
  (ADR-028 Proposed). Genuinely new scope - not previously listed in this roadmap or in ADVANCED.md.
- Sprint 23 - SIM-Swap / Fraud Detection: a new `fraud-service` (port 9013 proposed) doing rule-based
  velocity checks off existing Kafka events (ADR-029 Proposed). Deliberately narrowed, rule-based
  subset of the streaming/ML fraud-service in ADVANCED Section 4.4 / Section 10 P10 ("Data and
  intelligence").

Exit: each sprint's own exit criteria (its README under `docs/tasks/sprint-1{6-9}*/` and
`docs/tasks/sprint-2{0-3}*/`) are met, and each Proposed ADR (024-029) is ratified Accepted by
tech-lead before that sprint's build work starts. Phase P6 as a whole has no single exit criterion -
its eight sprints are independently schedulable except where a README states an explicit sequencing
note (Sprint 19 after Sprint 18).

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
| M6 | Post-MVP depth documented (Sprints 16-23 scoped, ADRs 022/024-029 authored); nothing built yet | P6 |

---

## 5. Post-MVP Candidates

### 5.1 Scheduled (Phase P6, Sprints 16-23)

These capabilities have moved from "tracked candidate" to a documented sprint with a Features
breakdown and (except Sprint 20) a Proposed ADR - see Section 3 "P6 - Post-MVP Depth" for the full
per-sprint detail and ADVANCED.md cross-references. **All eight are TODO** - documentation/design only,
nothing built:

| Capability | Sprint | ADR |
| --- | --- | --- |
| Web frontend + Web BFF | [Sprint 16](../tasks/sprint-16-web-frontend/README.md) | ADR-022 (Accepted) |
| Distributed locking (`starter-lock`) | [Sprint 17](../tasks/sprint-17-distributed-locking/README.md) | ADR-024 (Proposed) |
| Secret management (Vault) | [Sprint 18](../tasks/sprint-18-secret-management/README.md) | ADR-025 (Proposed) |
| Service mesh / mTLS (Linkerd) | [Sprint 19](../tasks/sprint-19-service-mesh-mtls/README.md) | ADR-026 (Proposed) |
| Chaos engineering (Chaos Mesh) | [Sprint 20](../tasks/sprint-20-chaos-engineering/README.md) | extends ADR-012/ADR-013 |
| Campaign / promotion engine (buildable subset) | [Sprint 21](../tasks/sprint-21-campaign-catalog-validation/README.md) | ADR-027 (Proposed) |
| Invoice dispute / chargeback | [Sprint 22](../tasks/sprint-22-dispute-chargeback/README.md) | ADR-028 (Proposed) |
| SIM-swap / fraud detection (rule-based MVP subset) | [Sprint 23](../tasks/sprint-23-sim-swap-fraud/README.md) | ADR-029 (Proposed) |

### 5.2 Unscheduled Candidates

Tracked but with no sprint or ADR yet (see BRD Section 7.2):

- Prepaid top-up and real-time charging
- Number portability (MNP) state machine (FR-16)
- Corporate customer and fleet management
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
