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

Last updated: 2026-07-03 (Sprint 14 Wave A: 14.1.2 contract tests DONE (avsc-snapshot + provider API guards across all produced events), 14.1.3 coverage gate DONE (JaCoCo 70% line/module, warn-first), 14.2 Security Hardening DONE — PII-at-rest/masking/mTLS audits PASS; audit-log gaps fixed: payment-service audit stack added (V3 + AuditLog/Repository/Writer wired into charge/refund) and customer address handlers now audited; payment 8/8 + customer address 10/10 tests green. Remaining Sprint 14: 14.1.1 acceptance E2E + 14.3 performance (Wave B, need full Docker stack). Prior: Sprint 13 DONE — OTel tracing wired (micrometer-tracing-bridge-otel + opentelemetry-exporter-otlp) with Kafka span propagation; platform logback-spring.xml with LogstashEncoder JSON + loki4j appender + PII masking converters; Prometheus scrape targets for all 10 services; 3 Grafana dashboards (platform-overview, kafka-billing-ops, circuit-breakers); 5 Prometheus alert rules; @CircuitBreaker on identity/customer/billing/notification services; 5 new resilience unit tests. BUILD SUCCESS.)

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
| [10](sprint-10-usage-metering/README.md) | usage-service, CDR (AC-03) | DONE | 7/7 |
| [11](sprint-11-billing/README.md) | billing-service (AC-02) | DONE | 6/6 |
| [12](sprint-12-notifications-and-ticketing/README.md) | notification-service, ticket-service | DONE | 6/6 |
| [13](sprint-13-observability-and-resilience/README.md) | tracing, metrics, logging, resilience | DONE | 4/4 |
| [14](sprint-14-testing-and-hardening/README.md) | acceptance, security, performance | IN PROGRESS | 1/3 |
| [15](sprint-15-deployment/README.md) | containers, Kubernetes, CI/CD | TODO | 0/5 |
| [16](sprint-16-web-frontend/README.md) | web frontend + web-bff (**post-MVP**) | TODO | 0/5 |

Totals (MVP, Sprints 01-15): 13 sprints DONE, 1 IN PROGRESS, 1 TODO. Features: 68 DONE / 1 IN PROGRESS
/ 6 TODO (75 total). Sprint 16 is post-MVP (ADR-022) and excluded from the MVP totals.
EPIC-006 (Onboarding Saga, Sprints 08-09) complete; AC-01 built (full-system acceptance in Sprint 14).
EPIC-007 (Revenue Cycle, Sprints 10-11) complete; AC-02 and AC-03 built.
EPIC-008 (Engagement and Support, Sprint 12) complete; notification-service and ticket-service with full unit and integration test coverage.

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
