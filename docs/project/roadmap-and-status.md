# Roadmap & Status

Full documents: [Product Roadmap](../product/roadmap.md) (product-level phases and milestones)
and [Status Dashboard](../tasks/STATUS.md) (the live, single source of truth for delivery
progress - updated every time a feature changes state).

## Delivery phases

| Phase | Theme | Exit criterion |
| --- | --- | --- |
| P0 | Platform foundation | Service template generates a compliant service; outbox/inbox proven |
| P1 | Identity and master data | Authenticated customer registration and catalog browsing work |
| P2 | Onboarding saga | AC-01 New Subscriber Onboarding passes end to end |
| P3 | Revenue cycle | AC-02 Billing and AC-03 Quota Exhaustion pass end to end |
| P4 | Engagement and support | Notifications dispatch on domain events; ticketing with SLA works |
| P5 | Hardening and release | NFR targets met; observability, security, K8s deployment validated |
| P6 | Post-MVP depth (Sprints 16-23) | Each sprint's own exit criteria; independently schedulable |

**P0-P5 are the MVP and are complete.** P6 is eight independently-scoped sprints extending the
platform with capabilities the MVP deliberately deferred, plus three new domain services:

| Sprint | Capability | ADR |
| --- | --- | --- |
| 16 | Web frontend + Web BFF (SvelteKit, Keycloak PKCE) | [ADR-022](../adr/ADR-022-frontend-and-bff-strategy.md) |
| 17 | Distributed locking (`starter-lock`, Redisson) | [ADR-024](../adr/ADR-024-distributed-lock-strategy.md) |
| 18 | Secret management (HashiCorp Vault) | [ADR-025](../adr/ADR-025-secrets-and-key-management.md) |
| 19 | Service mesh and mTLS (Linkerd) | [ADR-026](../adr/ADR-026-service-mesh-and-mtls.md) |
| 20 | Chaos engineering (Chaos Mesh) | extends ADR-012/ADR-013 |
| 21 | Campaign / catalog validation (`campaign-service`) | [ADR-027](../adr/ADR-027-campaign-and-catalog-validation.md) |
| 22 | Invoice dispute / chargeback (`dispute-service`) | [ADR-028](../adr/ADR-028-dispute-and-chargeback.md) |
| 23 | SIM-swap / fraud detection (`fraud-service`) | [ADR-029](../adr/ADR-029-fraud-detection-mvp-scope.md) |

Note: `docs/product/TELCO-CRM-ADVANCED.md` independently defines its own forward-looking
`P6`-`P11` enterprise themes. That is a **different phase sequence that happens to share the
label P6** for different content - this roadmap's P6 draws narrow, buildable slices from several
of ADVANCED's themes without being equivalent to any one of them. See the roadmap's own
phase-numbering note before assuming the two P6 labels mean the same thing.

## Current snapshot

As of the latest `STATUS.md` entry, Sprints 17, 21, and 23 are fully **DONE**; Sprint 19 (Service
Mesh and mTLS) reached full formal closure after live-cluster verification found and fixed real
NetworkPolicy/mesh-routing gaps; Sprint 22 (dispute-service) is code-complete with its
cross-service acceptance tests written but not yet run against a live Docker environment; Sprint
20 (Chaos Engineering) is feature-complete in authored form with its live-cluster exit criteria
still open. This summary will drift - **always check
[`docs/tasks/STATUS.md`](../tasks/STATUS.md) directly** for the current state; it is updated on
every status change and carries a detailed changelog explaining exactly what was verified versus
merely authored, including honest call-outs when a claimed status turned out to be wrong on
re-verification.

## Post-MVP candidates not yet scheduled

Tracked but with no sprint or ADR yet: prepaid top-up and real-time charging, number portability
(MNP), corporate/fleet customer management, BTK regulatory reporting, roaming usage tracking, a
production mobile application, and an analytics service. See the roadmap's Section 5.2 for the
full list and `docs/product/BRD.md` Section 7.2 for the business rationale behind each deferral.

## How this backlog is organized

Each sprint has its own directory under `docs/tasks/sprint-NN-*/` with a `README.md` (scope,
features table, exit criteria) and one file per feature. The per-sprint READMEs are in this
site's nav under **Delivery & Status > Sprints**; the individual feature files are not (there are
roughly 150 of them) but remain fully searchable on this site and are cross-linked extensively
from their sprint's README and from `STATUS.md`.
