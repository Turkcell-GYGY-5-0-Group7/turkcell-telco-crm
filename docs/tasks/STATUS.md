# Telco CRM - Backlog Status Dashboard

Cross-sprint rollup of the implementation backlog. This is the **single source of truth** for
delivery progress and program structure. Update the relevant sprint `README.md` (status header +
Features table) and this table together whenever a feature changes state.

## Status Legend

| Status | Meaning |
| --- | --- |
| DONE | Completed and verified (builds/tests pass or acceptance met) |
| IN PROGRESS | Actively being worked; some tasks complete |
| TODO | Planned, not started |
| BLOCKED | Cannot proceed until a dependency is resolved |
| DEFERRED | Intentionally postponed (for example, needs infrastructure not yet stood up) |

Last updated: 2026-07-01 (Sprint 09 DONE — full onboarding saga (AC-01 built); 9.5 real-broker de-risk proved the Debezium eventType header and caught+fixed a saga-breaking platform-wide bug (PascalCase outbox aggregate_type routed to the wrong topic); 123 saga tests green + a routing regression gate. Full-system AC-01 acceptance is Sprint 14. Also fixed a latent platform inbox-atomicity bug + an unauth sync-hop dead-loop earlier in the sprint)

## Sprint Rollup

| Sprint | Theme | Status | Progress |
| --- | --- | --- | --- |
| [01](sprint-01-foundation/README.md) | Foundation, build, infra, CI skeleton | DONE | 4/4 |
| [02](sprint-02-platform-core/README.md) | platform-core libraries | DONE | 6/6 |
| [03](sprint-03-platform-starters-and-events/README.md) | Starters, Avro contracts, service template | DONE | 4/4 |
| [04](sprint-04-platform-infrastructure-services/README.md) | config, discovery, gateway | DONE | 3/3 |
| [05](sprint-05-security-and-identity/README.md) | identity-service, JWT, RBAC | DONE | 7/7 |
| [06](sprint-06-customer-domain/README.md) | customer-service | DONE | 4/4 |
| [07](sprint-07-product-catalog-domain/README.md) | product-catalog-service | DONE | 5/5 |
| [08](sprint-08-order-and-payment/README.md) | order-service, payment-service | DONE | 6/6 |
| [09](sprint-09-subscription-and-onboarding-saga/README.md) | subscription-service, saga (AC-01) | DONE | 5/5 |
| [10](sprint-10-usage-metering/README.md) | usage-service, CDR (AC-03) | TODO | 0/7 |
| [11](sprint-11-billing/README.md) | billing-service (AC-02) | TODO | 0/6 |
| [12](sprint-12-notifications-and-ticketing/README.md) | notification-service, ticket-service | TODO | 0/6 |
| [13](sprint-13-observability-and-resilience/README.md) | tracing, metrics, logging, resilience | TODO | 0/4 |
| [14](sprint-14-testing-and-hardening/README.md) | acceptance, security, performance | TODO | 0/3 |
| [15](sprint-15-deployment/README.md) | containers, Kubernetes, CI/CD | TODO | 0/5 |
| [16](sprint-16-web-frontend/README.md) | web frontend + web-bff (**post-MVP**) | TODO | 0/5 |

Totals (MVP, Sprints 01-15): 9 sprints DONE, 0 IN PROGRESS, 6 TODO. Features: 44 DONE / 0 IN PROGRESS
/ 31 TODO (75 total). Sprint 16 is post-MVP (ADR-022) and excluded from the MVP totals.
EPIC-006 (Onboarding Saga, Sprints 08-09) complete; AC-01 built (full-system acceptance in Sprint 14).

## Epics and Phases

Program-increment view. Phases align with [`docs/product/roadmap.md`](../product/roadmap.md);
requirement IDs (FR/NFR) are in [`docs/product/requirements.md`](../product/requirements.md). The
sprint tables above are authoritative for status; this is the coarse rollup.

| Epic | Phase | Goal | Sprint(s) |
| --- | --- | --- | --- |
| EPIC-001 Platform Core Foundation | P0 | Framework-agnostic platform-core | 02 |
| EPIC-002 Spring Boot Starter System | P0 | Expose platform-core as starters (ADR-018) | 03 (3.1, 3.2, 3.4) |
| EPIC-003 Event-Driven System | P0 | Kafka + Avro ecosystem (ADR-009, ADR-019) | 01 (infra), 03 (3.3) |
| EPIC-004 Microservice Standardization | P0 | Service template (ADR-017) | 03 (3.4) |
| EPIC-005 Identity and Master Data | P1 | Authenticated access + master data | 04, 05, 06, 07 |
| EPIC-006 Onboarding Saga | P2 | End-to-end new-line activation (AC-01) | 08, 09 |
| EPIC-007 Revenue Cycle | P3 | Usage-driven billing (AC-02, AC-03) | 10, 11 |
| EPIC-008 Engagement and Support | P4 | Notifications and ticketing | 12 |
| EPIC-009 Hardening and Release | P5 | NFR targets, security, Kubernetes | 13, 14, 15 |

## How to Update Status

1. Change the feature row in the owning sprint `README.md` Features table.
2. Recompute that sprint's header `Progress` (DONE features / total) and `Status`.
3. Update the matching row in the Sprint Rollup table above and the `Last updated` dates.
4. If the change closes out an epic, update the Epics and Phases table above.
