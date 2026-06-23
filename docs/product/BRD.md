# Business Requirements Document (BRD)

## Telco CRM Platform

| Field | Value |
| --- | --- |
| Document | Business Requirements Document |
| Product | Telco CRM Platform |
| Version | 1.0 |
| Status | Draft for review |
| Owner | Product Owner Agent |
| Technical authority | Tech Lead Agent (see `/architecture/adr/`) |
| Last updated | 2026-06-19 |
| Source of business scope | [`TELCO-CRM-MVP.md`](./TELCO-CRM-MVP.md) (MVP analysis and design brief) |
| Source of technical scope | ADR-001 through ADR-020 |

> Authority rule: This BRD defines **what** the platform must do and **why**. Where this
> document and the Architecture Decision Records disagree on **how** (technology, patterns,
> protocols), the **ADRs are authoritative** per `CLAUDE.md` Section 3. Technology references
> here are summaries that point back to the canonical ADR.

---

## 1. Executive Summary

Telco CRM Platform is an event-driven microservices system that manages the full subscriber
lifecycle for a GSM operator: customer registration and KYC, product and tariff catalog,
ordering, subscription activation, usage and quota tracking, billing, payment, notification,
and customer support ticketing.

The reference operator for this product is **TelcoX**, a fictional mobile network operator
migrating from a legacy monolithic CRM to a distributed, cloud-native platform. The MVP
digitizes the end-to-end postpaid subscriber lifecycle, automates monthly invoicing, and
opens self-service channels (web and mobile back end via API).

The platform is built on Java 21 and Spring Boot 4.1.x, uses a database-per-service model on
PostgreSQL, communicates asynchronously over Apache Kafka with an Avro schema registry, and
deploys to Kubernetes. Internal application structure follows a governed hybrid architecture
(ADR-004) with a custom CQRS and Mediator framework (ADR-008).

---

## 2. Business Context and Vision

### 2.1 Vision

Provide TelcoX with a scalable, observable, and regulation-aware CRM ecosystem that manages
every customer touchpoint (sales, service, support, billing) through independently
deployable microservices, replacing the legacy monolith incrementally and safely.

### 2.2 Business Drivers

| ID | Driver | Description |
| --- | --- | --- |
| BD-01 | Legacy decommissioning | Replace the monolithic CRM in safe, bounded increments without a big-bang cutover. |
| BD-02 | Time-to-market | Launch and change tariffs, addons, and campaigns faster through an isolated catalog service. |
| BD-03 | Self-service | Enable web and mobile self-service to reduce call-center load and operational cost. |
| BD-04 | Billing automation | Automate monthly bill-run and overage charging to reduce manual effort and revenue leakage. |
| BD-05 | Scalability | Handle high-volume CDR ingestion and bill-run for large subscriber bases via horizontal scaling. |
| BD-06 | Compliance | Meet KVKK/GDPR and operator regulation (BTK) obligations with auditability by design. |

### 2.3 Business Goals and Success Metrics

| ID | Goal | Metric | MVP Target |
| --- | --- | --- | --- |
| BG-01 | Digitize onboarding | New subscriber onboarded end to end without manual back-office steps | < 5 minutes from order to active line |
| BG-02 | Automate billing | Monthly bill-run completes for full active base | 100K subscribers in < 30 minutes |
| BG-03 | Reliable payments | Payment processing is idempotent and retried on failure | 0 double charges; auto-retry at 24/72/168h |
| BG-04 | Proactive usage alerts | Subscribers notified before and at quota exhaustion | Alerts at 80% and 100% usage |
| BG-05 | Service reliability | Platform availability during MVP | 99.5% uptime |
| BG-06 | Observability | Every request is traceable end to end | 100% of requests carry traceId + correlationId |

### 2.4 Out-of-Scope Business Outcomes (MVP)

Prepaid real-time charging, number portability (MNP), corporate/fleet management, campaign
and promotion engine, regulatory reporting (BTK), roaming, and a production mobile app are
explicitly deferred. See Section 7.

---

## 3. Domain Overview: Telco CRM

A telecom CRM manages every customer touchpoint of an operator (sales, service, support,
billing). Like banking, it is regulated (BTK, KVKK/GDPR), high-volume, and real-time
(continuous CDR stream). The following domain concepts are first-class in the platform.

| Term | Definition |
| --- | --- |
| MSISDN | Mobile Subscriber ISDN. The subscriber's phone number. Acts as a system-wide unique identifier. |
| IMSI | International Mobile Subscriber Identity. Unique identity on the SIM card. |
| ICCID | The SIM card serial number. |
| Subscription | A customer's active membership to a specific tariff or plan. |
| Tariff / Plan | The minutes, SMS, and data package offered to a subscriber. Postpaid or prepaid. |
| VAS | Value Added Services (caller tunes, cloud, insurance, etc.). |
| CDR | Call Detail Record. Each call/SMS/data usage event. The basis for billing. |
| Top-up | Balance loading for prepaid lines (out of MVP scope). |
| MNP | Mobile Number Portability, i.e. operator change / number transfer (out of MVP scope). |
| BSCS / OCS | Billing and Charging System. The real-operator billing engine. |

Full bilingual glossary: see [glossary.md](./glossary.md).

### 3.1 Event Storming Summary

The following domain events flow across services. They are versioned (`domain.event.v1`),
Avro-encoded, and published through the transactional outbox (ADR-005, ADR-009, ADR-019).
The authoritative list is maintained in [../architecture/event-catalog.md](../architecture/event-catalog.md).

- `CustomerRegistered`, `CustomerKYCApproved`, `CustomerKYCRejected`
- `MSISDNAllocated`, `MSISDNReleased`
- `OrderCreated`, `OrderConfirmed`, `OrderCancelled`
- `SubscriptionActivated`, `SubscriptionSuspended`, `SubscriptionTerminated`
- `TariffChanged`, `AddonPurchased`
- `UsageRecorded`, `QuotaThresholdReached`, `QuotaExceeded`
- `InvoiceGenerated`, `PaymentReceived`, `PaymentFailed`
- `TicketOpened`, `TicketAssigned`, `TicketResolved`
- `NotificationDispatched`

---

## 4. Stakeholders, Actors, and Personas

### 4.1 Business Stakeholders

| Stakeholder | Interest |
| --- | --- |
| Product Owner | Roadmap, scope, prioritization, acceptance. |
| Tech Lead / Architecture | Architectural integrity, ADR compliance, technical risk. |
| Billing/Finance | Revenue assurance, correct and timely invoicing. |
| Compliance/Legal | KVKK/GDPR, BTK obligations, audit trail. |
| Operations/SRE | Availability, observability, incident response. |

### 4.2 Actors and Personas

| Actor | Role | Typical operations |
| --- | --- | --- |
| Subscriber (Customer) | End-user subscriber | Registration, ordering, invoice viewing, top-up, plan change, ticket creation |
| Call Center Agent | Customer support agent | Resolve tickets, view subscriber info, manual plan change |
| Field Dealer | Dealer / retailer | New subscriber activation, SIM sale, KYC data entry |
| Marketing Manager | Marketing manager | Campaign definition, segmentation (post-MVP) |
| System Administrator | Admin | Tariff/product catalog management, user authorization |
| Billing Operator | Billing operator | Monitor monthly bill-run jobs, invoice cancellation |
| Internal Service | Service-to-service | Event publish/consume, scheduled jobs, CDR mediation |

Detailed personas with goals and pain points: see [personas.md](./personas.md).

---

## 5. Scope: Business Capabilities (MVP)

The MVP delivers the following capabilities. Each maps to one or more services (Section 8)
and to functional requirements in [requirements.md](./requirements.md).

| Capability | Description | Primary service(s) |
| --- | --- | --- |
| Identity and access | Login, token issuance, role/permission-based authorization | identity-service, api-gateway |
| Customer management | Individual customer registration, KYC, contact/address/document management | customer-service |
| Product catalog | Tariff, addon, and VAS management with versioning and effective dates | product-catalog-service |
| Ordering | New-line, plan-change, and addon orders orchestrated via saga | order-service |
| Subscription lifecycle | Activation, suspension, reactivation, termination, MSISDN allocation | subscription-service |
| Usage and quota | CDR ingestion, quota tracking, threshold alerts, overage aggregation | usage-service |
| Billing | Monthly bill-run, invoice generation, PDF rendering | billing-service |
| Payment | Card payment via mock PSP, idempotency, retry | payment-service |
| Notification | SMS/email/push dispatch with templates and preferences | notification-service |
| Ticketing | Customer complaints/requests, SLA-based assignment | ticket-service |

---

## 6. Business Process Flows

### 6.1 New Subscriber Onboarding (happy path)

1. Customer applies (`POST /api/v1/customers`).
2. KYC document is uploaded and approved by an admin or dealer.
3. Customer selects a postpaid tariff and places an order.
4. Mock PSP payment succeeds.
5. Subscription is automatically activated and an MSISDN is allocated.
6. A welcome SMS (mock channel) is sent to the customer.

This flow is implemented as a saga; see Section 9.2 and ADR-004 Domain Orchestration mode.

### 6.2 Monthly Billing

1. The bill-run job is triggered (scheduled, or manually by a billing operator).
2. The last billing period usage of active subscribers is aggregated.
3. An invoice is generated per subscriber and a PDF is produced.
4. `InvoiceGenerated` triggers notification-service to email the invoice.
5. When the customer pays, `InvoicePaid` (PaymentReceived) is emitted.

### 6.3 Quota Exhaustion

1. A CDR simulator produces usage events onto Kafka.
2. usage-service decrements quotas.
3. At 80% usage, a warning SMS is sent.
4. At 100% usage, an addon-recommendation SMS is sent.
5. Post-exhaustion usage is forwarded to billing as overage.

---

## 7. MVP Boundaries

### 7.1 In Scope

- Individual customer registration and KYC
- Postpaid tariff ordering and activation
- Monthly billing (fixed fee + overage)
- Card payment via mock PSP
- SMS and email notifications (mock channels)
- Quota viewing and threshold alerts
- Basic customer ticketing
- Product catalog CRUD for the admin panel

### 7.2 Out of Scope (post-MVP)

- Prepaid top-up and real-time charging
- Number portability (MNP)
- Corporate customer and fleet management
- Campaign / promotion engine
- BTK regulatory reports
- Roaming usage tracking
- Production mobile application (back end plus Swagger UI only in MVP)

### 7.3 Assumptions

- PSP, SMS, and email providers are mocked in the MVP and abstracted behind ports.
- CDR is produced by a simulator rather than a live mediation feed.
- A single currency (TRY) and locale baseline (tr-TR) are assumed for the MVP.

### 7.4 Constraints

- All technology choices are bound by ADR-003 and related ADRs.
- Services may depend only on platform starters, never on platform-core directly (ADR-018, `CLAUDE.md` Section 9).
- External APIs must use `/api/v1` and return `ApiResult<T>` (ADR-015).
- Events must be versioned and Avro-schema-driven (ADR-009, ADR-019).

---

## 8. Solution Architecture Summary

The platform is a set of independently deployable microservices, each owning its PostgreSQL
schema (database-per-service). See [../architecture/service-catalog.md](../architecture/service-catalog.md)
for the authoritative catalog, including ports, bounded contexts, aggregates, and architecture
mode per service.

| Service | Port | Bounded context | Architecture mode (ADR-004) |
| --- | --- | --- | --- |
| api-gateway | 8080 | Edge routing | N/A (infrastructure) |
| discovery-server | 8761 | Service registry | N/A (infrastructure) |
| config-server | 8888 | Centralized config | N/A (infrastructure) |
| identity-service | 9001 | Identity and authorization | CQRS + Mediator |
| customer-service | 9002 | Customer management | CQRS + Mediator |
| product-catalog-service | 9003 | Product catalog | CQRS + Mediator |
| order-service | 9004 | Order orchestration | Domain Orchestration |
| subscription-service | 9005 | Subscription lifecycle | CQRS + Mediator |
| usage-service | 9006 | Usage and quota | CQRS + Mediator |
| billing-service | 9007 | Invoice generation | Domain Orchestration |
| payment-service | 9008 | Payment | Domain Orchestration |
| notification-service | 9009 | Notification | Simple Service Layer |
| ticket-service | 9010 | Customer requests | CQRS + Mediator |

### 8.1 Infrastructure Components

- PostgreSQL 17 (separate schema or instance per service) (ADR-006)
- Apache Kafka 4.x in KRaft mode as the domain event bus (ADR-009)
- Debezium for change data capture and transactional outbox delivery (ADR-005)
- Redis 8 for cache, rate limiting, and idempotency keys
- Keycloak for OAuth2 / OIDC (ADR-011)
- OpenSearch for search use cases (ADR-003)
- Object storage (MinIO or equivalent) for invoice PDFs and documents
- Observability stack: OpenTelemetry, Prometheus, Grafana, Loki, Tempo (ADR-012)

### 8.2 Reference Logical Topology

```text
[ Web / Mobile Client ]
        |
        v
+-----------------+
|   API Gateway   |   JWT validation, rate limit, routing, correlationId injection
+-----------------+
        |
   +-----------------------------+
   | Discovery (dev) | Config    |
   +-----------------------------+
        |
        v   (REST in / gRPC service-to-service)
identity  customer  catalog  order  subscription  usage
   |        |         |        |         |          |
   +--------+---------+--------+---------+----------+
                          |
                     [ Kafka Bus + Schema Registry ]
                          |
   +---------+-----------+-----------+----------+
   |         |           |           |          |
   v         v           v           v          v
 billing   payment   notification  ticket   analytics (future)
```

---

## 9. Service Interaction and Event Flow

### 9.1 Synchronous vs Asynchronous Decisions

| Scenario | Communication | Rationale |
| --- | --- | --- |
| Customer check during order | Synchronous (gRPC/REST) | Immediate validation required |
| Catalog price during order | Synchronous + cache | Price snapshot must be captured |
| Order to subscription activation | Asynchronous (Kafka) | Reversible, eventual consistency |
| Subscription to billing | Asynchronous (Kafka) | Loose coupling |
| CDR stream to usage | Asynchronous (Kafka) | High volume, replayable |
| Invoice to notification | Asynchronous (Kafka) | Invoice remains issued even if notification fails |
| Payment authorization | Synchronous (PSP REST) | Immediate customer feedback required |

Communication standards are defined in ADR-005. Internal service-to-service calls prefer gRPC;
external clients use REST through the gateway.

### 9.2 Saga: New-Line Order

```text
1. Customer  -- POST /orders -->  Order Service
2. Order Service: OrderCreated  ==> Kafka
3. Payment Service consumes OrderCreated
   -> charge attempt
   -> PaymentCompleted  ==> Kafka
4. Subscription Service consumes PaymentCompleted
   -> allocate MSISDN
   -> create Subscription
   -> SubscriptionActivated  ==> Kafka
5. Order Service consumes SubscriptionActivated
   -> mark order FULFILLED
6. Notification Service consumes SubscriptionActivated
   -> send welcome SMS

Compensation: if subscription activation fails
   -> Subscription Service: SubscriptionActivationFailed
   -> Payment Service: refund triggered
   -> Order Service: order moves to CANCELLED
```

### 9.3 Outbox and Idempotency (mandatory)

A database write and a Kafka publish must be atomic. Each service maintains an outbox table;
Debezium-based CDC delivers outbox rows to Kafka. Consumers apply inbox-based deduplication.
The transactional outbox plus idempotent consumer combination is mandatory in the MVP
(ADR-005, ADR-009).

---

## 10. High-Level Data Model

Each service owns its aggregates. The detailed entity-relationship diagrams are maintained
under [`docs/erd/`](../erd/) (per-service PDFs). High-level aggregates per service are listed
in [../architecture/service-catalog.md](../architecture/service-catalog.md). Summary:

- customer-service: Customer, Address, Document
- product-catalog-service: Tariff, Addon, ProductOffering
- order-service: Order, OrderItem, SagaState
- subscription-service: Subscription, MsisdnPool, SimCard
- usage-service: Quota, UsageRecord, CdrEvent
- billing-service: Invoice, InvoiceLine, BillCycle
- payment-service: Payment, PaymentAttempt, Wallet
- notification-service: NotificationTemplate, Notification, Channel
- ticket-service: Ticket, TicketComment, SLA
- identity-service: User, Role, Permission

---

## 11. Non-Functional Requirements (summary)

The full, testable NFR list is in [requirements.md](./requirements.md) (NFR-XX). Summary of
targets:

| Category | Requirement | Target |
| --- | --- | --- |
| Performance | API response time (p95) | < 300 ms |
| Performance | Bill-run duration | 100K subscribers < 30 min |
| Scalability | Horizontal scaling | Stateless services, Kubernetes HPA auto-scale |
| Availability | Service uptime | 99.5% (MVP) |
| Security | Authentication | OAuth2 / JWT, validated at gateway (ADR-011) |
| Security | Data protection | PII fields encrypted at rest (TCKN, card number) |
| Observability | Distributed tracing | OpenTelemetry + Tempo (ADR-012) |
| Observability | Logging | Structured JSON, centralized (Loki) |
| Observability | Metrics | Prometheus + Grafana |
| Resilience | Circuit breaker | Resilience4j on all outbound calls |
| Consistency | Consistency model | Eventual consistency (outbox pattern) |
| Compliance | Regulation | KVKK / GDPR, mandatory audit log |

---

## 12. API and Integration Standards (summary)

Authoritative standard: ADR-015. Key rules:

- All external REST APIs use the `/api/v1` prefix; versioning is URI-based.
- Resource names are plural (`customers`, `orders`, `subscriptions`).
- All responses are wrapped in `ApiResult<T>` with a mandatory `meta` block (traceId,
  correlationId, timestamp, service, path).
- Errors are typed (no generic exceptions); no stack traces are exposed externally.
- Pagination is dual-mode: offset by default, cursor for large/streaming datasets.
- `Idempotency-Key` is supported on critical POST operations (Payment, Order).
- `Correlation-Id` is injected by the gateway and logged across all services.
- All timestamps are ISO-8601 UTC; money uses `BigDecimal` plus a separate currency code.

---

## 13. Security and Compliance (summary)

Authoritative standard: ADR-011. Key rules:

- Keycloak issues JWT (access + refresh) via OAuth2/OIDC (ADR-011); identity-service manages
  users/roles/permissions via the Keycloak Admin API.
- The API Gateway validates each request, extracts `userId`/`roles`, and forwards them
  downstream as `X-User-Id` and `X-User-Roles`.
- Internal traffic uses JWT plus mTLS.
- Refresh-token rotation: previous tokens are blacklisted in Redis; reuse revokes all active tokens.
- Authorization is role/permission based (`@PreAuthorize`), enforced especially on admin endpoints.
- PII (TCKN, card number) is encrypted with AES-GCM; keys are read from Vault/Kubernetes Secret.
- Audit logging is mandatory in identity, customer, payment, and subscription services.
- Gateway rate limiting is Redis-backed; default 100 requests/minute per user.

---

## 14. Acceptance Criteria

The MVP is accepted when the following scenarios run end to end. Detailed Given/When/Then
criteria are in [requirements.md](./requirements.md) Section "Acceptance Criteria".

1. New subscriber onboarding (Section 6.1) completes and a welcome SMS is logged.
2. Monthly billing (Section 6.2) generates invoices and emails them; payment emits `InvoicePaid`.
3. Quota exhaustion (Section 6.3) emits 80% and 100% alerts and forwards overage to billing.

---

## 15. Risks and Mitigations

| ID | Risk | Impact | Mitigation |
| --- | --- | --- | --- |
| RISK-01 | Distributed transaction complexity (saga/compensation) | High | Outbox + inbox, explicit compensation steps, contract tests |
| RISK-02 | CDR volume overwhelms usage-service | Medium | Partitioned Kafka topics, consumer scaling, backpressure |
| RISK-03 | Bill-run does not meet 30-minute target | High | Batch processing, partitioned runs, performance test in CI |
| RISK-04 | PII handling non-compliance (KVKK/GDPR) | High | Encryption at rest, soft-delete, audit logging, access control |
| RISK-05 | Schema evolution breaks consumers | Medium | Schema Registry compatibility rules (ADR-019), versioned events |
| RISK-06 | Over-engineering simple services | Medium | Architecture mode governance (ADR-004), code review agent |

---

## 16. Dependencies and Related Documents

- Architecture Decision Records: [`/architecture/adr/`](../../architecture/adr/)
- Functional and non-functional requirements: [requirements.md](./requirements.md)
- Product roadmap: [roadmap.md](./roadmap.md)
- Personas: [personas.md](./personas.md)
- Glossary: [glossary.md](./glossary.md)
- Service catalog: [../architecture/service-catalog.md](../architecture/service-catalog.md)
- Event catalog: [../architecture/event-catalog.md](../architecture/event-catalog.md)
- Entity-relationship diagrams: [`docs/erd/`](../erd/)
- Execution backlog and status: [`docs/tasks/`](../tasks/), [`docs/tasks/STATUS.md`](../tasks/STATUS.md)

---

## 17. Glossary (key terms)

A complete bilingual glossary is in [glossary.md](./glossary.md). Core terms: Bounded Context,
Saga, Outbox Pattern, Idempotency, CQRS, Circuit Breaker, CDR, MSISDN, MNP, KYC, VAS,
KVKK/GDPR, HPA.

---

Document end.
