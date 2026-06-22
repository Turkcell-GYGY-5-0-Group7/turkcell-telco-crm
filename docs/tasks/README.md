# Telco CRM Platform - Implementation Backlog

This directory is the implementation-ready development backlog for the Telco CRM Platform MVP.
It is derived from `telco-crm-microservices-mvp.docx` (analysis and design), reconciled with the
product requirements (`docs/product/requirements.md`), the event and service catalogs
(`docs/architecture/`), and the platform ADRs (`architecture/adr/`).

An AI coding agent can execute the entire MVP using only the files in this directory. Each sprint is
a directory: a `README.md` holds the sprint objective, epics, cross-cutting constraints, deliverables,
and exit criteria, and one file per top-level feature (`X.Y-*.md`) holds that feature's subtasks. Each
subtask carries its ID, description, business purpose, inputs, outputs, objective and testable
acceptance criteria, dependencies, and complexity. The original analysis document is not required
during implementation.

---

## 1. How to Use This Backlog

1. Execute sprints in numerical order. Earlier sprints unblock later sprints.
2. Within a sprint, respect the `Dependencies` field on each task. Tasks with no in-sprint
   dependency may run in parallel.
3. A task is complete only when every acceptance criterion is objectively met (compiles, tests
   pass, endpoint returns the specified contract).
4. Read Section 3 (Global Conventions) once. Every task inherits it; conventions are not repeated
   per task.

---

## 2. Sprint Index

| Sprint | File | Epic | Theme | Unblocks |
| --- | --- | --- | --- | --- |
| 01 | [sprint-01-foundation/](sprint-01-foundation/README.md) | 1 | Repository, build, local infrastructure, CI skeleton | All |
| 02 | [sprint-02-platform-core/](sprint-02-platform-core/README.md) | 2 | platform-core libraries (common, cqrs, mediator, outbox, inbox) | 03+ |
| 03 | [sprint-03-platform-starters-and-events/](sprint-03-platform-starters-and-events/README.md) | 3 | Spring Boot starters, Avro event contracts, service template | 04+ |
| 04 | [sprint-04-platform-infrastructure-services/](sprint-04-platform-infrastructure-services/README.md) | 4 | config-server, discovery-server, api-gateway | 05+ |
| 05 | [sprint-05-security-and-identity/](sprint-05-security-and-identity/README.md) | 5 | identity-service, JWT, RBAC, gateway auth | 06+ |
| 06 | [sprint-06-customer-domain/](sprint-06-customer-domain/README.md) | 6 | customer-service (registration, KYC, PII) | 08, 09 |
| 07 | [sprint-07-product-catalog-domain/](sprint-07-product-catalog-domain/README.md) | 7 | product-catalog-service (tariffs, addons, versioning) | 08 |
| 08 | [sprint-08-order-and-payment/](sprint-08-order-and-payment/README.md) | 8 | order-service, payment-service (mock PSP) | 09 |
| 09 | [sprint-09-subscription-and-onboarding-saga/](sprint-09-subscription-and-onboarding-saga/README.md) | 9 | subscription-service, saga, compensation (AC-01) | 11 |
| 10 | [sprint-10-usage-metering/](sprint-10-usage-metering/README.md) | 10 | usage-service, CDR ingestion, quota, thresholds (AC-03) | 11 |
| 11 | [sprint-11-billing/](sprint-11-billing/README.md) | 11 | billing-service, bill-run, invoice PDF (AC-02) | 12 |
| 12 | [sprint-12-notifications-and-ticketing/](sprint-12-notifications-and-ticketing/README.md) | 12 | notification-service, ticket-service | 13 |
| 13 | [sprint-13-observability-and-resilience/](sprint-13-observability-and-resilience/README.md) | 13 | tracing, metrics, logging, Resilience4j rollout | 14 |
| 14 | [sprint-14-testing-and-hardening/](sprint-14-testing-and-hardening/README.md) | 14 | integration/contract tests, security, performance, AC validation | 15 |
| 15 | [sprint-15-deployment/](sprint-15-deployment/README.md) | 15 | Dockerfiles, Kubernetes, HPA, CI/CD, rollback | - |

Task IDs are hierarchical: `epic.feature.task` (for example `6.2.3`); subtasks add a fourth level
(`6.2.3.1`). The epic number equals the sprint number.

---

## 3. Global Conventions (apply to every task)

These are mandated by the analysis document, `docs/product/requirements.md`, and the ADRs. Do not
restate them in code reviews; enforce them.

### 3.1 Stack and layout
- Java 21. Spring Boot and Spring Cloud versions are pinned by `platform-bom` (ADR-003); never
  hardcode versions in a service POM - inherit from the BOM.
- Maven multi-module. Platform modules live under `platform/`; microservices under
  `microservices/<service-name>/`.
- GroupId `com.telco` for services, `com.telco.platform` for platform modules. Service base package
  `com.telco.<service>` (for example `com.telco.customer`).
- Per-service PostgreSQL schema/database (database-per-service, NFR-15). No cross-service DB access.

### 3.2 Architecture (ADR-004, ADR-008)
- ADR-004 defines three application architecture modes; each service MUST declare exactly one in its
  `README.md`/`CLAUDE.md` (ARC-01). CQRS is a tool used only inside modes 2 and 3 - it is not
  mandatory for every service.
  - SIMPLE SERVICE LAYER (Controller -> Service -> Repository): CRUD-oriented, trivial business
    logic, no orchestration. Mediator/CQRS not required.
  - CQRS + MEDIATOR (Controller -> Mediator -> Command/Query Handler -> Domain -> Repository): the
    default domain mode where business rules exist, events are emitted, and pipelines are needed.
  - DOMAIN ORCHESTRATION (Controller -> Mediator -> Application Service -> Domain Services ->
    Aggregates): multi-aggregate, saga-style workflows with event-driven coordination and
    compensation.
  Per-service mode assignments below follow the authoritative `docs/architecture/service-catalog.md`
  Section 3. Infrastructure services (gateway/config/discovery) carry no application mode (N/A).
- Controllers contain no business logic (ARC-02). All domain operations flow through the `Mediator`
  as `Command`/`Query` objects with dedicated handlers (ARC-03).
- Domain layer is framework-independent. Services depend ONLY on platform starters, never on
  `platform-core` directly (ARC-04, ADR-018).
- Mandatory starters per service: `starter-api`, `starter-security`, `starter-observability`.
  Optional: `starter-mediator`, `starter-outbox`, `starter-inbox`.

### 3.3 API standards (ADR-015)
- External REST under `/api/v1`. Plural resource names (`customers`, `orders`).
- All external responses wrapped in `ApiResult<T>` (NFR-14). Errors use `ApiError` (RFC 7807-aligned:
  code, message, details, traceId).
- Pagination: offset via Spring `Pageable` (`?page=0&size=20&sort=createdAt,desc`) returning
  `PageResult<T>`; cursor (`CursorPage<T>`) for high-volume reads.
- `Idempotency-Key` header supported on POST commands (mandatory for Payment and Order).
- `X-Correlation-Id` injected by the gateway and logged by every service (NFR-13).
- Dates ISO-8601 UTC. Money as `BigDecimal` with a separate currency code (TRY).
- Each service exposes its own Springdoc OpenAPI/Swagger UI (ARC-08).

### 3.4 Eventing (ADR-009, ADR-019)
- Events are immutable, Avro-encoded, versioned `domain.event.v1`, registered in Schema Registry.
- DB write plus event publish is atomic via the transactional outbox; consumers are idempotent via
  the inbox (ARC-05, NFR-11). Debezium delivers outbox rows to Kafka.
- Canonical event names and producer/consumer wiring are defined in
  `docs/architecture/event-catalog.md` and restated per producing sprint.

### 3.5 Security (ADR-011)
- identity-service issues JWT (access + refresh). The gateway validates JWT on every request and
  forwards `X-User-Id` / `X-User-Roles` downstream; services trust the gateway (gateway-behind-trust).
- Authorization via `@PreAuthorize` / mediator `AuthorizationRule` on admin and privileged endpoints.
- PII at rest (TCKN, card number) encrypted with AES-GCM; key from K8s Secret/Vault (NFR-06).
- PII masked in logs/telemetry (ADR-021). Audit log mandatory in identity, customer, payment,
  subscription (NFR-12).
- Gateway rate limit: Redis-backed, 100 req/min per user default (NFR-18).

### 3.6 Quality gates (ADR-013, ADR-014)
- Every service: Flyway migrations (ARC-07), unit tests, and Testcontainers integration tests.
- No emojis in code, comments, commits, or docs (ARC-09).
- CI must build, test, run static analysis, and gate merges (NFR-17).

### 3.7 Service registry (ports, from the analysis document)

| Service | Port | Architecture Mode | Aggregates |
| --- | --- | --- | --- |
| api-gateway | 8080 | Edge (config-only) | - |
| discovery-server | 8761 | Registry (config-only) | - |
| config-server | 8888 | Config (config-only) | - |
| identity-service | 9001 | CQRS + Mediator | User, Role, Permission |
| customer-service | 9002 | CQRS + Mediator | Customer, Address, Document |
| product-catalog-service | 9003 | CQRS + Mediator | Tariff, Addon, ProductOffering |
| order-service | 9004 | Domain Orchestration | Order, OrderItem, SagaState |
| subscription-service | 9005 | CQRS + Mediator | Subscription, MSISDN, SimCard |
| usage-service | 9006 | CQRS + Mediator | UsageRecord, Quota, CdrEvent |
| billing-service | 9007 | Domain Orchestration | Invoice, InvoiceLine, BillCycle |
| payment-service | 9008 | Domain Orchestration | Payment, PaymentAttempt, Wallet |
| notification-service | 9009 | Simple Service Layer | Notification, Template, Channel |
| ticket-service | 9010 | CQRS + Mediator | Ticket, Comment, SLA |

---

## 4. Requirement Traceability

Every requirement in the analysis document maps to at least one sprint. Detailed per-task tracing
lives in each sprint file; this is the coverage summary.

### 4.1 Functional requirements

| Requirement | Sprint |
| --- | --- |
| FR-IAM-01..05 (identity, gateway auth) | 04, 05 |
| FR-01..04 (customer, KYC, soft-delete) | 06 |
| FR-05..08 (catalog, versioning) | 07 |
| FR-09..12 (order, saga, compensation) | 08, 09 |
| FR-13..16 (subscription lifecycle, MSISDN; MNP post-MVP) | 09 |
| FR-17..20 (usage, quota, thresholds, overage) | 10 |
| FR-21..24 (billing, bill-run, PDF, InvoicePaid) | 11 |
| FR-25..27 (payment, idempotency, retry) | 08 |
| FR-28..30 (notification channels, templates, preferences) | 12 |
| FR-31..33 (ticketing, SLA assignment, notify) | 12 |

### 4.2 Non-functional requirements

| Requirement | Sprint |
| --- | --- |
| NFR-01 p95 < 300ms | 13, 14 |
| NFR-02 bill-run 100K < 30min | 11, 14 |
| NFR-03 HPA auto-scale | 15 |
| NFR-04 99.5% uptime | 13, 15 |
| NFR-05 OAuth2/JWT at gateway | 04, 05 |
| NFR-06 PII AES-GCM at rest | 06, 14 |
| NFR-07 distributed tracing (OTel + Tempo) | 01, 03, 13 |
| NFR-08 structured JSON logging (Loki) | 01, 03, 13 |
| NFR-09 metrics (Prometheus + Grafana) | 01, 13 |
| NFR-10 circuit breaker (Resilience4j) | 13 |
| NFR-11 eventual consistency (outbox/inbox) | 02, 03 |
| NFR-12 KVKK/GDPR audit log | 05, 06, 14 |
| NFR-13 traceId/correlationId on every request | 03, 04 |
| NFR-14 ApiResult on all responses | 02, 03 |
| NFR-15 database-per-service | all domain sprints |
| NFR-16 versioned Avro events, compatibility | 03 |
| NFR-17 unit + integration tests gate | all + 14 |
| NFR-18 gateway rate limit 100 req/min | 04 |

### 4.3 Acceptance criteria

| AC | Scenario | Validated in |
| --- | --- | --- |
| AC-01 | New subscriber onboarding (register -> KYC -> order -> pay -> activate -> welcome SMS) | 09 (built), 14 (validated) |
| AC-02 | Monthly billing (bill-run -> invoice PDF -> notify -> pay) | 11 (built), 14 (validated) |
| AC-03 | Quota exhaustion (CDR -> quota -> 80%/100% SMS -> overage to billing) | 10 (built), 14 (validated) |

---

## 5. Dependency Overview (sprint level)

```text
01 Foundation
  -> 02 Platform Core
       -> 03 Starters + Event Contracts
            -> 04 Infra Services (config, discovery, gateway)
                 -> 05 Security + Identity
                      -> 06 Customer        -> 08 Order+Payment -> 09 Subscription+Saga
                      -> 07 Product Catalog  /                         |
                                                                       -> 11 Billing
                      05 -> 10 Usage Metering --------------------------/
                                09 + 10 + 11 -> 12 Notifications + Ticketing
                                                  -> 13 Observability + Resilience
                                                       -> 14 Testing + Hardening
                                                            -> 15 Deployment
```

No circular dependencies exist. Infrastructure and shared platform components are completed before
business services; security precedes secured endpoints; testing, observability, and deployment are
distributed across sprints rather than deferred to the end.
</content>
</invoke>
