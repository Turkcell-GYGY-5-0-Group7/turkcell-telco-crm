**TELCO CRM PLATFORM**

A Telecommunications CRM System on a Microservices Architecture

_MVP Analysis and Design Document_

Training Project - Student Roadmap

Java 21 - Spring Boot 4.x - Spring Cloud - PostgreSQL - Kafka - Docker - Kubernetes

Version 1.1 (English; canonical)

---

> **About this document.** This is the canonical English translation of the original Turkish brief
> (preserved verbatim at [`TELCO-CRM-MVP-TR.md`](TELCO-CRM-MVP-TR.md)). The MVP brief is a learning
> target, not a binding spec: where it disagrees with an Architecture Decision Record (ADR), **the
> ADRs win** (see `architecture/adr/`). Points where the delivered platform deliberately differs
> from the original brief are called out inline as **PLATFORM NOTE** blocks and summarised in
> Section 0. For the enterprise/senior-level evolution beyond this MVP, see
> [`TELCO-CRM-ADVANCED.md`](TELCO-CRM-ADVANCED.md).

---

# 0. Reconciliation with ADRs (delivered choices vs. the brief)

The original brief was written before the platform ADRs were ratified. The table below records every
material divergence so the document stays accurate without losing the original intent.

| Topic | Brief says | Delivered choice | Authority |
| --- | --- | --- | --- |
| Spring Boot | 3.x / 3.3.x | 4.1.0 | ADR-003 |
| Spring Cloud | 2023.0.x | Version aligned with Boot 4.x, pinned by `platform-bom` | ADR-003, ADR-020 |
| Architecture modes | "CQRS where complex" | Three explicit modes: Simple Service Layer, CQRS+Mediator, Domain Orchestration | ADR-004, ADR-008 |
| Distributed tracing | Zipkin / Jaeger | OpenTelemetry -> Tempo | ADR-012 |
| Log aggregation | ELK | Structured JSON -> Loki | ADR-012 |
| Service discovery | Eureka / Consul | discovery-server (Eureka in dev; Kubernetes-native in prod) | ADR-010 |
| Event encoding | PascalCase event names | Avro, Schema Registry, versioned `domain.event.v1` | ADR-009, ADR-019 |
| Outbox delivery | "a publisher worker" | Transactional outbox + Debezium CDC -> Kafka; inbox idempotency | ADR-019 |
| Delivery unit | "assignment week" (Hafta) | 15 numbered sprints under `docs/tasks/` | `docs/tasks/STATUS.md` |
| API contracts | inline in this doc | Authoritative per-service contracts in `docs/api-contracts/` | ADR-015 |

> **PLATFORM NOTE (sprint numbering).** The brief sequences work by "assignment week"; the delivered
> backlog sequences by sprint. They do not line up one-to-one. For example, identity and the gateway
> are **Sprint 04-05**, not "week 3"; the customer domain is **Sprint 06**. Always map a brief
> capability to its owning sprint via [`docs/tasks/STATUS.md`](../tasks/STATUS.md), not by week number.

---

# Table of Contents

1. Project Vision and Goals
2. Domain Analysis: What Is Telecom CRM?
3. Actors and Persona Definitions
4. Functional Requirements
5. Non-Functional Requirements
6. MVP Scope (Scope In / Scope Out)
7. Microservices Architecture - Service-Level Bounded Contexts
8. Service Details
9. Inter-Service Communication and Event Flow
10. Data Model - High-Level Entities
11. Technology Stack
12. API Design Standards
13. Security Architecture
14. Acceptance Criteria
15. Glossary

# 1. Project Vision and Goals

## 1.1 Vision

Telco CRM Platform is a scalable, event-driven CRM platform that manages a GSM operator's entire
subscriber lifecycle (customer registration, product ordering, billing, usage tracking, customer
support) on a single microservices ecosystem.

## 1.2 Training Goals

The project aims to build real-world competence in:

- Bounded-context extraction with Domain-Driven Design (DDD)
- Production-grade microservice development with Spring Boot
- Service-topology management with Spring Cloud (Config, Gateway, Discovery)
- Asynchronous, event-driven integration with Apache Kafka
- Synchronous communication and contract management with REST + OpenAPI
- Database-per-service with PostgreSQL
- Cache-aside and idempotency strategies with Redis
- Local orchestration with Docker Compose and production deployment with Kubernetes
- Distributed tracing (OpenTelemetry) and centralized logging
- API-gateway-level security with JWT + OAuth2
- Circuit breaker, retry, and bulkhead patterns with Resilience4j
- CI/CD pipeline setup (GitHub Actions / GitLab CI)

> **PLATFORM NOTE.** The brief lists "Spring Boot 3" and "Zipkin". The delivered platform standardises
> on **Spring Boot 4.x** (ADR-003) and **OpenTelemetry + Tempo** (ADR-012). The learning objectives
> are unchanged; only the concrete tools are newer.

## 1.3 Business Goal (Scenario)

Our fictional operator "TelcoX" wants to migrate its existing monolithic CRM to microservices,
piece by piece. The MVP targets an end-to-end digital subscriber lifecycle, automated invoice
generation, and the opening of self-service channels (mobile/web).

# 2. Domain Analysis: What Is Telecom CRM?

Telecom CRM is the set of systems that manage all customer touchpoints of an operator (sales,
service, support, billing). Like banking, it is a regulated (BTK), high-volume, real-time (CDR
stream) domain.

## 2.1 Key Telecom-Specific Concepts

| Concept | Description |
| --- | --- |
| MSISDN | Mobile Subscriber ISDN - the subscriber's phone number. Acts as a unique identifier in the system. |
| IMSI | International Mobile Subscriber Identity - the unique identity on the SIM card. |
| ICCID | The SIM card's serial number. |
| Subscription | A customer's active subscription to a specific tariff/package. |
| Tariff / Plan | The minutes, SMS, and GB package offered to the subscriber. Postpaid or prepaid. |
| VAS | Value Added Services - extras (caller tunes, cloud, insurance, etc.). |
| CDR | Call Detail Record - each call/SMS/data usage record. The basis of billing. |
| Top-up | Loading credit onto prepaid lines. |
| MNP | Mobile Number Portability - switching operator / porting a number. |
| BSCS / OCS | Billing & Charging System - the billing engine in a real operator. |

## 2.2 Event Storming Output (Summary)

The following domain events flow between services throughout the project:

- CustomerRegistered, CustomerKYCApproved, CustomerKYCRejected
- MSISDNAllocated, MSISDNReleased
- OrderCreated, OrderConfirmed, OrderCancelled
- SubscriptionActivated, SubscriptionSuspended, SubscriptionTerminated
- TariffChanged, AddonPurchased
- UsageRecorded, QuotaThresholdReached, QuotaExceeded
- InvoiceGenerated, PaymentReceived, PaymentFailed
- TicketOpened, TicketAssigned, TicketResolved
- NotificationDispatched

> **PLATFORM NOTE (event contracts).** These PascalCase names are the conceptual events. The delivered
> system encodes them as **Avro schemas registered in Schema Registry**, named `domain.event.v1`
> (e.g. `customer.registered.v1`), published via the transactional outbox and consumed idempotently
> via the inbox (ADR-009, ADR-019). The authoritative registry is
> [`docs/architecture/event-catalog.md`](../architecture/event-catalog.md).

# 3. Actors and Persona Definitions

| Actor | Role | Typical Operations |
| --- | --- | --- |
| Customer (Subscriber) | End-user subscriber | Registration, ordering, invoice viewing, top-up, plan change, raising tickets |
| Call-Center Agent | Customer support agent | Resolving tickets, viewing subscriber data, manual plan changes |
| Field Dealer | Dealer / retailer | New subscriber activation, SIM sales, KYC data entry |
| Marketing Manager | Marketing manager | Campaign definition, segment extraction |
| System Administrator | Admin | Tariff/product catalog management, user authorization |
| Billing Operator | Billing operator | Monitoring monthly bill-run jobs, invoice cancellation |
| System (Internal Service) | Service-to-service | Event publish/consume, scheduled jobs, CDR mediation |

# 4. Functional Requirements

Requirements are numbered with an FR-XX code. Each requirement notes its microservice and (in the
original brief) its assignment week.

> **PLATFORM NOTE.** Ignore the "assignment week" framing; every FR is mapped to a delivered sprint in
> [`docs/tasks/README.md`](../tasks/README.md) Section 4 (Requirement Traceability) and tracked in
> [`docs/tasks/STATUS.md`](../tasks/STATUS.md).

## 4.1 Customer Management (Customer Service)

- FR-01: The system supports individual and corporate customer registration (with TCKN / VKN validation).
- FR-02: After the KYC process the customer status transitions PENDING -> ACTIVE / REJECTED.
- FR-03: The customer can manage address, contact information, and identity documents.
- FR-04: Customer deletion is a soft-delete (GDPR/KVKK).

## 4.2 Product and Tariff Catalog (Product Catalog Service)

- FR-05: The system manages tariffs, packages, addons, and VAS products hierarchically.
- FR-06: Each product has validity dates (effectiveFrom / effectiveTo) and a target segment.
- FR-07: Products are classified as postpaid, prepaid, or hybrid.
- FR-08: Tariff changes are versioned; existing subscribers' tariffs are preserved.

## 4.3 Order Management (Order Service)

- FR-09: A customer can place a new-line order, a plan change, or an addon order.
- FR-10: Orders are processed with multi-service coordination via the saga pattern.
- FR-11: Order states: DRAFT, PENDING_PAYMENT, PAID, FULFILLED, CANCELLED.
- FR-12: Order cancellation triggers compensation events.

## 4.4 Subscription Management (Subscription Service)

- FR-13: When an order completes, the subscription is automatically activated.
- FR-14: Subscription suspension (on non-payment), reactivation, and termination are supported.
- FR-15: A customer may have multiple subscriptions.
- FR-16: Number portability (MNP) is managed by a separate state machine.

> **PLATFORM NOTE.** FR-16 (MNP) is **scope-out for the MVP**; it is scaffolded only. Its full design
> lives in [`TELCO-CRM-ADVANCED.md`](TELCO-CRM-ADVANCED.md).

## 4.5 Usage Tracking (Usage Service)

- FR-17: The CDR stream is consumed over Kafka and usage balances are updated.
- FR-18: Remaining quota (minutes, sms, mb) is visible in near real time.
- FR-19: A notification event is produced at the 80% and 100% usage thresholds.
- FR-20: Overage usage is aggregated for transfer to the billing service.

## 4.6 Billing (Billing Service)

- FR-21: A monthly bill-run job issues invoices for all postpaid subscribers.
- FR-22: Invoice lines: monthly fee, addon fees, overage, VAS fees, taxes.
- FR-23: The invoice is rendered as a PDF and sent to the Notification service.
- FR-24: When payment is received, an InvoicePaid event is produced.

## 4.7 Payment (Payment Service)

- FR-25: Payment by credit card, bank transfer, and wallet is supported.
- FR-26: Payment is idempotent; the same paymentRequestId is not processed twice.
- FR-27: Failed payments are retried at 24/72/168-hour intervals.

## 4.8 Notification (Notification Service)

- FR-28: Supports SMS, e-mail, and push notification channels.
- FR-29: Has templated notification management.
- FR-30: Respects the user's communication preferences (opt-in/opt-out).

## 4.9 Call-Center Ticket Management (Ticket Service)

- FR-31: Customers can open complaints, requests, and fault records.
- FR-32: Tickets are auto-assigned to the relevant team on an SLA basis.
- FR-33: When a ticket is opened, the customer is notified.

# 5. Non-Functional Requirements

| Category | Requirement | Target |
| --- | --- | --- |
| Performance | API response time (p95) | < 300 ms |
| Performance | Bill-run job duration | 100K subscribers < 30 min |
| Scalability | Horizontal scalability | Stateless services, auto-scale via K8s HPA |
| Availability | Service uptime | 99.5% (MVP) |
| Security | Auth | OAuth2 / JWT, validated at the gateway |
| Security | Data | PII fields stored encrypted (TCKN, card number) |
| Observability | Distributed tracing | OpenTelemetry + Tempo |
| Observability | Logging | Structured JSON logs, centralized (Loki) |
| Observability | Metrics | Prometheus + Grafana |
| Resilience | Circuit breaker | Resilience4j on all external calls |
| Data consistency | Consistency model | Eventual consistency (Outbox pattern) |
| Compliance | Regulation | KVKK / GDPR, audit log mandatory |

> **PLATFORM NOTE.** The brief named Zipkin/Jaeger and ELK for the last two observability rows; the
> table above reflects the delivered stack (OpenTelemetry + Tempo, Loki) per ADR-012. Targets are
> unchanged.

# 6. MVP Scope

## 6.1 Scope In (in the MVP)

- Individual customer registration and KYC
- Postpaid tariff ordering and activation
- Monthly billing (fixed fee + overage)
- Credit-card payment (mock PSP)
- SMS and e-mail notifications (mock channel)
- Quota viewing and threshold notifications
- Basic customer ticketing
- Product catalog CRUD for the admin panel

## 6.2 Scope Out (post-MVP)

- Prepaid top-up and real-time charging
- Number portability (MNP)
- Corporate customers and fleet management
- Campaign / promotion engine
- BTK regulatory reports
- Roaming usage tracking
- Mobile application (backend + Swagger UI only)

> **PLATFORM NOTE.** Every Scope-Out item is designed out at enterprise level in
> [`TELCO-CRM-ADVANCED.md`](TELCO-CRM-ADVANCED.md), alongside additional professional-grade concerns
> (multi-region scale, zero-trust security, data/intelligence platform).

# 7. Microservices Architecture

The table lists the MVP services, their bounded contexts, and core aggregates. Each service owns its
PostgreSQL schema - the database-per-service pattern is applied.

| Service | Port | Bounded Context | Core Aggregates | Architecture Mode (ADR-004) |
| --- | --- | --- | --- | --- |
| api-gateway | 8080 | Edge routing | - | Edge (N/A) |
| discovery-server | 8761 | Service registry | - | Registry (N/A) |
| config-server | 8888 | Centralized config | - | Config (N/A) |
| identity-service | 9001 | Identity & authz | User, Role, Permission | CQRS + Mediator |
| customer-service | 9002 | Customer management | Customer, Address, Document | CQRS + Mediator |
| product-catalog-service | 9003 | Product catalog | Tariff, Addon, ProductOffering | CQRS + Mediator |
| order-service | 9004 | Order orchestration | Order, OrderItem, SagaState | Domain Orchestration |
| subscription-service | 9005 | Subscription lifecycle | Subscription, MSISDN, SimCard | CQRS + Mediator |
| usage-service | 9006 | Usage & quota | UsageRecord, Quota, CdrEvent | CQRS + Mediator |
| billing-service | 9007 | Invoice generation | Invoice, InvoiceLine, BillCycle | Domain Orchestration |
| payment-service | 9008 | Payment | Payment, PaymentAttempt, Wallet | Domain Orchestration |
| notification-service | 9009 | Notification | Notification, Template, Channel | Simple Service Layer |
| ticket-service | 9010 | Customer requests | Ticket, Comment, SLA | CQRS + Mediator |

> **PLATFORM NOTE (architecture modes).** The Architecture Mode column is an addition not present in
> the original brief. ADR-004 mandates that every service declares exactly one of three modes; the
> assignments above are authoritative in [`docs/architecture/service-catalog.md`](../architecture/service-catalog.md).

## 7.1 Horizontal Components (Infrastructure)

- PostgreSQL (a separate schema or DB instance per service)
- Apache Kafka - domain event broker
- Redis - cache + rate limiting + idempotency keys
- Keycloak (optional, advanced) - OAuth2 / OIDC provider
- MinIO or local FS - invoice PDF / document storage
- Observability stack - OpenTelemetry + Tempo + Loki + Prometheus + Grafana

> **PLATFORM NOTE.** The brief listed "Zipkin + ELK"; the delivered observability stack is
> OpenTelemetry collector -> Tempo (traces), Loki (logs), Prometheus + Grafana (metrics), wired in
> `infra/docker/observability/` (ADR-012).

## 7.2 Logical Architecture Diagram (Text)

```text
[ Web/Mobile Client ]
        |
        v
+-----------------+
|   API Gateway   |  <-- JWT validation, rate limit, routing
+-----------------+
        | (REST)
        v
+---------------------------------------------------------+
|        Discovery Server     |     Config Server         |
+---------------------------------------------------------+
        |
   ------------------------------------------------------------
   |        |         |        |            |        |
   v        v         v        v            v        v
identity customer  catalog   order   subscription  usage
   |        |         |        |            |        |
   +--------+---------+--------+------------+--------+
                        |
                  [ Kafka Bus ]
                        |
   +--------+---------+------------+--------+
   |        |         |            |
   v        v         v            v
billing  payment  notification  ticket   analytics (future)
```

# 8. Service Details

## 8.1 Customer Service

**Responsibility**

Master record of the customer's identity and contact data.

**Key APIs**

- POST /api/v1/customers - new customer
- GET /api/v1/customers/{id}
- PUT /api/v1/customers/{id}
- POST /api/v1/customers/{id}/documents - upload KYC document
- POST /api/v1/customers/{id}/kyc/approve

**Events**

- Publish: CustomerRegistered, CustomerKYCApproved, CustomerUpdated

## 8.2 Product Catalog Service

**Responsibility**

Master catalog management of tariffs, addons, and VAS products. A read-heavy service - Redis cache
is used intensively.

**Key APIs**

- GET /api/v1/tariffs
- GET /api/v1/tariffs/{code}
- POST /api/v1/tariffs (admin)
- GET /api/v1/addons?tariffCode=...

**Events**

- Publish: TariffCreated, TariffPriceChanged

## 8.3 Order Service

**Responsibility**

Order capture and orchestration via the saga. Manages the Customer -> Catalog -> Subscription ->
Payment chain.

**Key APIs**

- POST /api/v1/orders
- GET /api/v1/orders/{id}
- POST /api/v1/orders/{id}/cancel

**Events**

- Publish: OrderCreated, OrderConfirmed, OrderCancelled
- Consume: PaymentCompleted, PaymentFailed, SubscriptionActivated

## 8.4 Subscription Service

**Responsibility**

Manages the subscription state machine. MSISDN allocation/release.

**Key APIs**

- POST /api/v1/subscriptions (internal - called by Order)
- GET /api/v1/subscriptions/{id}
- POST /api/v1/subscriptions/{id}/suspend
- POST /api/v1/subscriptions/{id}/reactivate
- POST /api/v1/subscriptions/{id}/terminate

**Events**

- Publish: SubscriptionActivated, SubscriptionSuspended, SubscriptionTerminated
- Consume: OrderConfirmed, PaymentFailed (after grace period)

## 8.5 Usage Service

**Responsibility**

Consumes CDR (Call Detail Record) events and updates usage counters. Write-heavy.

**Key APIs**

- GET /api/v1/usage/subscriptions/{id}/quota
- GET /api/v1/usage/subscriptions/{id}/history?from=...&to=...

**Events**

- Consume: CdrRecorded (from the CDR simulator)
- Publish: QuotaThresholdReached, QuotaExceeded

## 8.6 Billing Service

**Responsibility**

Monthly bill-run scheduler and invoice generation.

**Key APIs**

- GET /api/v1/invoices?customerId=...
- GET /api/v1/invoices/{id}
- GET /api/v1/invoices/{id}/pdf
- POST /api/v1/billing/runs (admin trigger)

**Events**

- Publish: InvoiceGenerated, InvoicePaid, InvoiceOverdue
- Consume: UsageAggregated, SubscriptionActivated, PaymentCompleted

## 8.7 Payment Service

**Responsibility**

Payment capture and PSP integration (mock).

**Key APIs**

- POST /api/v1/payments
- GET /api/v1/payments/{id}
- POST /api/v1/payments/{id}/refund

**Events**

- Publish: PaymentCompleted, PaymentFailed, PaymentRefunded
- Consume: InvoiceGenerated (for the auto-pay scenario)

## 8.8 Notification Service

**Responsibility**

Multi-channel notification dispatch.

**Key APIs**

- POST /api/v1/notifications (internal)
- GET /api/v1/notifications/users/{id}/history

**Events**

- Consume: ALMOST all domain events (template-based mapping)

## 8.9 Ticket Service

**Responsibility**

Customer request / complaint management, SLA.

**Key APIs**

- POST /api/v1/tickets
- GET /api/v1/tickets/{id}
- POST /api/v1/tickets/{id}/comments
- POST /api/v1/tickets/{id}/assign
- POST /api/v1/tickets/{id}/resolve

**Events**

- Publish: TicketOpened, TicketResolved, SlaBreached

> **PLATFORM NOTE (API contracts).** Section 8 is a summary. The authoritative, ADR-015-conformant
> per-service contracts (auth, idempotency, pagination, error shape, full event lists) live in
> [`docs/api-contracts/`](../api-contracts/).

# 9. Inter-Service Communication and Event Flow

## 9.1 Synchronous vs. Asynchronous Decision

| Scenario | Communication Type | Rationale |
| --- | --- | --- |
| Customer check during order creation | Synchronous (REST) | Immediate validation required |
| Catalog product price during order creation | Synchronous (REST + cache) | A snapshot must be taken |
| Order -> Subscription activation | Asynchronous (Kafka) | Reversible, eventual consistency |
| Subscription -> Billing subscription data | Asynchronous (Kafka) | Loose coupling |
| CDR stream -> Usage | Asynchronous (Kafka) | High volume, can be processed retroactively |
| Invoice -> Notification | Asynchronous (Kafka) | Even if notification fails, the invoice stays issued |
| Payment validation | Synchronous (PSP REST) | Immediate feedback to the customer required |

## 9.2 Saga Example: New-Line Order

1. Customer --POST /orders--> Order Service
2. Order Service: OrderCreated ===> Kafka
3. Payment Service consumes OrderCreated -> charge attempt -> PaymentCompleted ===> Kafka
4. Subscription Service consumes PaymentCompleted -> allocate MSISDN -> create Subscription -> SubscriptionActivated ===> Kafka
5. Order Service consumes SubscriptionActivated -> marks order FULFILLED
6. Notification Service consumes SubscriptionActivated -> sends welcome SMS

Compensation: if SubscriptionActivation fails -> Subscription Service emits SubscriptionActivationFailed
-> Payment Service triggers a refund -> Order Service moves the order to CANCELLED.

## 9.3 Outbox Pattern Requirement

A write to a service's DB plus a Kafka publish must be atomic. To guarantee this, each service keeps
an outbox table; a separate publisher delivers those rows to Kafka. The combination of transactional
outbox + idempotent consumer is mandatory in the MVP.

> **PLATFORM NOTE.** The delivered platform implements the publisher as **Debezium CDC** reading the
> outbox table into Kafka, with consumer idempotency enforced by the inbox (eventId), per ADR-019.
> The outbox/inbox machinery is provided by the platform starters (`starter-outbox`, `starter-inbox`).

# 10. Data Model - High-Level Entities

The core entities owned by each service are summarized below. Producing the detailed ER diagram was
the original "Week 1" assignment; the diagrams now live in [`docs/erd/`](../erd/).

## 10.1 Customer Service

- Customer(id, type[INDIVIDUAL|CORPORATE], firstName, lastName, identityNumber, dateOfBirth, status, createdAt)
- Address(id, customerId, line1, city, district, postalCode, isDefault)
- Document(id, customerId, type[ID_CARD|PASSPORT], fileRef, verifiedAt)

## 10.2 Product Catalog

- Tariff(id, code, name, type[POSTPAID|PREPAID], monthlyFee, minutesIncluded, smsIncluded, dataMbIncluded, status, effectiveFrom, effectiveTo)
- Addon(id, code, name, price, type[DATA|SMS|MINUTES|VAS], validityDays)
- TariffAddon(tariffId, addonId) - many-to-many

## 10.3 Order Service

- Order(id, customerId, status, totalAmount, currency, createdAt)
- OrderItem(id, orderId, productCode, productType, quantity, unitPrice)
- SagaState(id, orderId, currentStep, payload, lastUpdated)

## 10.4 Subscription Service

- Subscription(id, customerId, msisdn, tariffCode, status[ACTIVE|SUSPENDED|TERMINATED], activatedAt, terminatedAt)
- MsisdnPool(msisdn, status[FREE|RESERVED|ALLOCATED], reservedUntil)
- SimCard(iccid, imsi, msisdn, status)

## 10.5 Usage Service

- Quota(id, subscriptionId, periodStart, periodEnd, minutesRemaining, smsRemaining, mbRemaining)
- UsageRecord(id, subscriptionId, type[VOICE|SMS|DATA], quantity, recordedAt, cdrRef)

## 10.6 Billing Service

- Invoice(id, customerId, subscriptionId, periodStart, periodEnd, subTotal, tax, grandTotal, status, dueDate, issuedAt)
- InvoiceLine(id, invoiceId, description, quantity, unitPrice, lineTotal)
- BillCycle(id, customerId, dayOfMonth, nextRunDate)

## 10.7 Payment Service

- Payment(id, invoiceId, amount, method, status, externalRef, paidAt)
- PaymentAttempt(id, paymentId, attemptNo, response, attemptedAt)

## 10.8 Notification Service

- NotificationTemplate(id, code, channel, locale, subject, bodyTemplate)
- Notification(id, userId, templateCode, channel, payloadJson, status, sentAt)

## 10.9 Ticket Service

- Ticket(id, customerId, category, priority, status, slaDueAt, createdAt)
- TicketComment(id, ticketId, authorId, body, createdAt)

# 11. Technology Stack

| Layer | Technology | Version / Note |
| --- | --- | --- |
| Language | Java | 21 (LTS) |
| Framework | Spring Boot | 4.1.0 (brief said 3.3.x; ADR-003) |
| Spring Cloud | Gateway, Config, Eureka, OpenFeign | Aligned with Boot 4.x, pinned by `platform-bom` |
| Build | Maven | Maven multi-module (ADR-020) |
| DB | PostgreSQL | 16, a separate schema per service |
| Cache | Redis | 7 |
| Broker | Apache Kafka | 3.7+ (KRaft mode) |
| Schema | Avro + Schema Registry | Versioned `domain.event.v1` (ADR-019) |
| Migration | Flyway | In every service |
| ORM | Spring Data JPA + Hibernate | - |
| Mapping | MapStruct | - |
| Validation | Jakarta Bean Validation | - |
| Auth | Spring Security + JWT (jjwt 0.12.x) | Relayed at the gateway |
| Doc | Springdoc OpenAPI | Separate Swagger UI per service |
| Resilience | Resilience4j | Circuit breaker, retry, bulkhead |
| Observability | Micrometer + OpenTelemetry + Tempo + Loki + Prometheus | Brief said Zipkin/ELK; ADR-012 |
| Test | JUnit 5, Mockito, Testcontainers, RestAssured | - |
| Container | Docker, Docker Compose | Local development |
| Orchestration | Kubernetes | Local via Minikube / Kind |
| CI/CD | GitHub Actions | Build -> test -> docker push -> kubectl apply |

> **PLATFORM NOTE.** Versions in this table are illustrative; the **single source of truth for
> versions is `platform-bom`** (ADR-003, ADR-020). Service POMs must inherit from the BOM and never
> hardcode versions.

# 12. API Design Standards

- All REST APIs use the /api/v1 prefix. Versioning is URI-based.
- Resource names are plural (customers, orders, subscriptions).
- HTTP method semantics: GET (read), POST (create + command), PUT (full update), PATCH (partial), DELETE (soft delete).
- Error format follows the RFC 7807 Problem Details standard.
- Pagination: ?page=0&size=20&sort=createdAt,desc - Spring Data Pageable is used.
- The Idempotency-Key header is supported on POST operations (especially Payment, Order).
- The Correlation-Id header is injected by the gateway and written to logs in every service.
- All date fields are ISO-8601 UTC.
- Money fields use BigDecimal with a separate currency code (TRY).

> **PLATFORM NOTE.** All external responses are additionally wrapped in `ApiResult<T>` and errors in
> `ApiError` (ADR-015); see [`docs/api-contracts/README.md`](../api-contracts/README.md).

## 12.1 Example Error Response

```json
{
  "type": "https://telco.example/errors/customer-not-found",
  "title": "Customer not found",
  "status": 404,
  "detail": "Customer with id 1234 does not exist",
  "instance": "/api/v1/customers/1234",
  "correlationId": "9f3c1b..."
}
```

# 13. Security Architecture

- Auth: identity-service issues JWT (access + refresh) on login.
- The API Gateway validates the JWT on every request, extracts userId/role from the payload, and
  forwards them downstream as the X-User-Id and X-User-Roles headers.
- Services do not re-validate the JWT internally; gateway-behind-trust is used. (mTLS is recommended
  in production; out of scope for the MVP.)
- Refresh-token rotation: after each refresh the old token is added to a blacklist (Redis); if reuse
  is detected all active tokens are revoked.
- Authorization: role/permission-based via @PreAuthorize, particularly on admin endpoints.
- PII encryption: TCKN and card number are encrypted with AES-GCM; the key is read from Vault/K8s Secret.
- Audit log: in the identity, customer, payment, and subscription services, every change is written
  to an audit_log table.
- Rate limit: Redis-based at the gateway; 100 req/min per user by default.

> **PLATFORM NOTE (token issuer).** The brief says identity-service issues the JWT on login. The
> delivered platform follows **ADR-011: Keycloak is the identity provider and issues tokens**
> (`Client -> BFF/Gateway -> Keycloak -> JWT -> Gateway -> services`). identity-service does not mint
> JWTs; it manages users/roles/permissions via the Keycloak Admin API and owns app-specific
> authorization data. Refresh-token rotation and reuse detection are Keycloak realm features. See
> [`docs/architecture/keycloak-and-auth.md`](../architecture/keycloak-and-auth.md).
>
> **PLATFORM NOTE.** PII masking in logs/telemetry is mandated by ADR-021 and enforced by the platform
> starters. mTLS, tokenization/HSM, and fraud detection are designed at enterprise level in
> [`TELCO-CRM-ADVANCED.md`](TELCO-CRM-ADVANCED.md).

# 14. Acceptance Criteria

On MVP delivery, the following scenarios must work end to end:

## 14.1 Scenario: New Subscriber Onboarding

- The customer applies (POST /customers).
- A KYC document is uploaded and approved by an admin.
- The customer chooses a postpaid tariff and places an order.
- Payment succeeds via the mock PSP.
- The subscription is automatically activated and an MSISDN is assigned.
- A welcome SMS (mock log) is sent to the customer.

## 14.2 Scenario: Monthly Invoice

- The bill-run job is triggered manually.
- The last month's usage of active subscribers is aggregated.
- An invoice is created and a PDF rendered for each subscriber.
- The notification service sends an e-mail on the InvoiceGenerated event.
- When the customer pays the invoice, the InvoicePaid event is triggered.

## 14.3 Scenario: Quota Exhaustion

- The CDR simulator produces usage events.
- The usage service decrements quotas.
- A warning SMS is sent at 80%.
- An SMS suggesting an add-on package is sent at 100%.
- Post-exhaustion usage flows to billing as overage.

> **PLATFORM NOTE.** These map to AC-01, AC-02, and AC-03 in the delivered backlog: built in Sprints
> 09/11/10 respectively and validated in Sprint 14 (see [`docs/tasks/STATUS.md`](../tasks/STATUS.md)).

# 15. Glossary

| Term | Definition |
| --- | --- |
| Bounded Context | In DDD, a clearly defined boundary within which a model is valid. |
| Saga | A pattern that manages distributed transactions with compensation steps. |
| Outbox Pattern | A table-based solution that makes a DB transaction + message publish atomic. |
| Idempotency | The property that performing the same operation more than once does not change the result. |
| CQRS | Separation of Command (write) and Query (read) responsibilities. |
| Circuit Breaker | A pattern that automatically cuts off calls when an error-rate threshold is exceeded (Resilience4j). |
| CDR | Call Detail Record - telecom usage records. |
| MSISDN | The subscriber's phone number, unique system-wide. |
| MNP | Mobile Number Portability - number porting. |
| KYC | Know Your Customer - the identity-verification process. |
| VAS | Value Added Service - an extra service (cloud, music, etc.). |
| KVKK / GDPR | Regulations on the protection of personal data. |
| Service Mesh | The infrastructure layer that manages inter-service communication (Istio, etc.). |
| HPA | Horizontal Pod Autoscaler - K8s horizontal scaling. |

_- End of Document -_
