# Requirements Specification

## Telco CRM Platform

| Field | Value |
| --- | --- |
| Document | Functional and Non-Functional Requirements |
| Version | 1.0 |
| Status | Draft for review |
| Parent | [BRD.md](./BRD.md) |
| Technical authority | ADR-001 through ADR-020 |
| Last updated | 2026-06-19 |

This document refines the [BRD](./BRD.md) into testable requirements. Each functional
requirement (FR) is traced to the owning service and the release phase from
[roadmap.md](./roadmap.md). Requirement keywords (MUST, SHOULD, MAY) follow RFC 2119.

---

## 1. Requirement Conventions

- FR-XX: Functional requirement.
- NFR-XX: Non-functional requirement.
- AC-XX: Acceptance criterion.
- Phase: Release phase from the product roadmap (P0 platform, P1..P5 features).
- Priority: MUST (MVP-blocking), SHOULD (MVP-desired), MAY (post-MVP candidate).

---

## 2. Functional Requirements

### 2.1 Identity and Access (identity-service, api-gateway)

| ID | Requirement | Service | Phase | Priority |
| --- | --- | --- | --- | --- |
| FR-IAM-01 | The system MUST authenticate users and issue JWT access and refresh tokens via OAuth2/OIDC (Keycloak-backed). | identity-service | P1 | MUST |
| FR-IAM-02 | The API Gateway MUST validate JWT on every request and reject invalid or expired tokens. | api-gateway | P1 | MUST |
| FR-IAM-03 | The gateway MUST extract userId and roles from the token and forward them downstream as `X-User-Id` and `X-User-Roles`. | api-gateway | P1 | MUST |
| FR-IAM-04 | The system MUST support role and permission based authorization, enforced on admin endpoints. | identity-service | P1 | MUST |
| FR-IAM-05 | Refresh-token rotation MUST blacklist the previous token in Redis; detected reuse MUST revoke all active tokens for the user. | identity-service | P1 | MUST |

### 2.2 Customer Management (customer-service)

| ID | Requirement | Service | Phase | Priority |
| --- | --- | --- | --- | --- |
| FR-01 | The system MUST support individual and corporate customer registration with TCKN/VKN validation. | customer-service | P1 | MUST (individual); SHOULD (corporate, post-MVP) |
| FR-02 | After the KYC process, customer status MUST transition PENDING -> ACTIVE / REJECTED. | customer-service | P1 | MUST |
| FR-03 | A customer MUST be able to manage address, contact information, and identity documents. | customer-service | P1 | MUST |
| FR-04 | Customer deletion MUST be performed via soft-delete (KVKK/GDPR). | customer-service | P1 | MUST |

### 2.3 Product and Tariff Catalog (product-catalog-service)

| ID | Requirement | Service | Phase | Priority |
| --- | --- | --- | --- | --- |
| FR-05 | The system MUST manage tariffs, packages, addons, and VAS products hierarchically. | product-catalog-service | P1 | MUST |
| FR-06 | Every product MUST have validity dates (effectiveFrom / effectiveTo) and a target segment. | product-catalog-service | P1 | MUST |
| FR-07 | Products MUST be classified as postpaid, prepaid, or hybrid. | product-catalog-service | P1 | MUST (postpaid); MAY (prepaid/hybrid) |
| FR-08 | Tariff changes MUST be versioned; existing subscribers' tariff MUST be preserved. | product-catalog-service | P1 | MUST |

### 2.4 Order Management (order-service)

| ID | Requirement | Service | Phase | Priority |
| --- | --- | --- | --- | --- |
| FR-09 | A customer MUST be able to place a new-line order, plan change, or addon order. | order-service | P2 | MUST (new-line); SHOULD (plan change, addon) |
| FR-10 | Orders MUST be processed via the saga pattern coordinating multiple services. | order-service | P2 | MUST |
| FR-11 | Order statuses MUST be: DRAFT, PENDING_PAYMENT, PAID, FULFILLED, CANCELLED. | order-service | P2 | MUST |
| FR-12 | On order cancellation, compensation events MUST be triggered. | order-service | P2 | MUST |

### 2.5 Subscription Management (subscription-service)

| ID | Requirement | Service | Phase | Priority |
| --- | --- | --- | --- | --- |
| FR-13 | When an order completes, a subscription MUST be activated automatically. | subscription-service | P2 | MUST |
| FR-14 | Suspension (on non-payment), reactivation, and termination MUST be supported. | subscription-service | P2 | MUST |
| FR-15 | A customer MAY have multiple subscriptions. | subscription-service | P2 | MUST |
| FR-16 | Number portability (MNP) MUST be managed via a separate state machine. | subscription-service | Post-MVP | MAY |

### 2.6 Usage Tracking (usage-service)

| ID | Requirement | Service | Phase | Priority |
| --- | --- | --- | --- | --- |
| FR-17 | CDR stream MUST be consumed via Kafka and usage balances updated. | usage-service | P3 | MUST |
| FR-18 | Remaining quota (minutes, sms, mb) MUST be viewable in near real time. | usage-service | P3 | MUST |
| FR-19 | At 80% and 100% usage thresholds, a notification event MUST be produced. | usage-service | P3 | MUST |
| FR-20 | Overage usage MUST be aggregated for transfer to the billing service. | usage-service | P3 | MUST |

### 2.7 Billing (billing-service)

| ID | Requirement | Service | Phase | Priority |
| --- | --- | --- | --- | --- |
| FR-21 | The monthly bill-run job MUST generate invoices for all postpaid subscribers. | billing-service | P3 | MUST |
| FR-22 | Invoice lines MUST include monthly fee, addon fees, overage, VAS fees, and taxes. | billing-service | P3 | MUST |
| FR-23 | Invoices MUST be produced as PDF and sent to the notification service. | billing-service | P3 | MUST |
| FR-24 | When payment is received, an InvoicePaid event MUST be produced. | billing-service | P3 | MUST |

### 2.8 Payment (payment-service)

| ID | Requirement | Service | Phase | Priority |
| --- | --- | --- | --- | --- |
| FR-25 | Payment MUST be supported via credit card, bank transfer, and wallet. | payment-service | P2 | MUST (card via mock PSP); MAY (transfer, wallet) |
| FR-26 | Payment MUST be idempotent; the same paymentRequestId MUST NOT be processed twice. | payment-service | P2 | MUST |
| FR-27 | Failed payments MUST be retried at 24/72/168 hour intervals. | payment-service | P2 | MUST |

### 2.9 Notification (notification-service)

| ID | Requirement | Service | Phase | Priority |
| --- | --- | --- | --- | --- |
| FR-28 | SMS, email, and push notification channels MUST be supported. | notification-service | P4 | MUST (SMS, email via mock); MAY (push) |
| FR-29 | Templated notification management MUST be provided. | notification-service | P4 | MUST |
| FR-30 | The system MUST respect user communication preferences (opt-in/opt-out). | notification-service | P4 | MUST |

### 2.10 Ticketing (ticket-service)

| ID | Requirement | Service | Phase | Priority |
| --- | --- | --- | --- | --- |
| FR-31 | Customers MUST be able to open complaints, requests, and fault tickets. | ticket-service | P4 | MUST |
| FR-32 | Tickets MUST be auto-assigned to the relevant team based on SLA. | ticket-service | P4 | MUST |
| FR-33 | When a ticket is opened, a notification MUST be sent to the customer. | ticket-service | P4 | MUST |

---

## 3. Non-Functional Requirements

| ID | Category | Requirement | Target | ADR |
| --- | --- | --- | --- | --- |
| NFR-01 | Performance | API response time (p95) | < 300 ms | ADR-012 |
| NFR-02 | Performance | Bill-run job duration | 100K subscribers < 30 min | ADR-014 |
| NFR-03 | Scalability | Horizontal scalability of stateless services | Kubernetes HPA auto-scale | ADR-003 |
| NFR-04 | Availability | Service uptime (MVP) | 99.5% | - |
| NFR-05 | Security | Authentication and authorization | OAuth2/JWT validated at gateway | ADR-011 |
| NFR-06 | Security | PII at rest | AES-GCM encryption (TCKN, card number) | ADR-011 |
| NFR-07 | Observability | Distributed tracing | OpenTelemetry + Tempo | ADR-012 |
| NFR-08 | Observability | Logging | Structured JSON, centralized (Loki) | ADR-012 |
| NFR-09 | Observability | Metrics | Prometheus + Grafana | ADR-012 |
| NFR-10 | Resilience | Circuit breaker on all outbound calls | Resilience4j | ADR-005 |
| NFR-11 | Consistency | Consistency model | Eventual consistency via outbox | ADR-005, ADR-009 |
| NFR-12 | Compliance | Regulation | KVKK/GDPR, mandatory audit log | ADR-011 |
| NFR-13 | Traceability | Every request carries traceId and correlationId | 100% of requests | ADR-015 |
| NFR-14 | API contract | All external responses wrapped in `ApiResult<T>` | Enforced by global handler | ADR-015 |
| NFR-15 | Data isolation | Database-per-service | No cross-service DB access | ADR-006 |
| NFR-16 | Event contract | Events versioned and Avro-schema-driven, backward compatible | Schema Registry compatibility checks | ADR-019 |
| NFR-17 | Testability | Unit + integration tests mandatory for merge | CI gate | ADR-013, ADR-014 |
| NFR-18 | Rate limiting | Per-user request throttling at gateway | 100 req/min default (Redis-backed) | ADR-011 |

---

## 4. Cross-Cutting Architectural Requirements

These are mandated by `CLAUDE.md` and the ADRs and apply to every service.

| ID | Requirement | Source |
| --- | --- | --- |
| ARC-01 | Each service MUST declare exactly one architecture mode (Simple / CQRS+Mediator / Domain Orchestration). | ADR-004 |
| ARC-02 | Controllers MUST NOT contain business logic; domain logic MUST be framework-independent. | ADR-004, ADR-008 |
| ARC-03 | All domain operations in CQRS/Orchestration services MUST go through the Mediator. | ADR-008 |
| ARC-04 | Services MAY depend only on platform starters, never on platform-core directly. | ADR-018 |
| ARC-05 | DB write plus event publish MUST be atomic via the transactional outbox; consumers MUST be idempotent (inbox). | ADR-005, ADR-009 |
| ARC-06 | Internal service-to-service calls SHOULD use gRPC; external clients use REST via the gateway. | ADR-005 |
| ARC-07 | Each service MUST use Flyway for schema migrations. | ADR-016 |
| ARC-08 | Each service MUST expose its own OpenAPI/Swagger UI. | ADR-015 |
| ARC-09 | No emojis in code, comments, commits, or documentation. | CLAUDE.md |

---

## 5. Acceptance Criteria

### AC-01: New Subscriber Onboarding

```text
Given an unregistered individual customer
When they register (POST /api/v1/customers) and upload a KYC document
And an admin approves the KYC
And the customer places a postpaid tariff order
And the mock PSP payment succeeds
Then a subscription is activated automatically
And an MSISDN is allocated to the subscription
And a welcome SMS is dispatched (mock channel log)
And the order status becomes FULFILLED
```

Traces: FR-01, FR-02, FR-05, FR-09, FR-10, FR-13, FR-25, FR-26, FR-28.

### AC-02: Monthly Billing

```text
Given active postpaid subscribers with recorded usage
When the bill-run job is triggered (POST /api/v1/billing/runs)
Then last-period usage is aggregated per subscriber
And an invoice is generated and rendered to PDF for each subscriber
And an InvoiceGenerated event causes notification-service to email the invoice
And when the customer pays, an InvoicePaid event is emitted
```

Traces: FR-20, FR-21, FR-22, FR-23, FR-24, FR-28, FR-29.

### AC-03: Quota Exhaustion

```text
Given an active subscription with a quota
When the CDR simulator produces usage events consumed by usage-service
Then quota balances are decremented
And at 80% usage a warning SMS is dispatched
And at 100% usage an addon-recommendation SMS is dispatched
And post-exhaustion usage is forwarded to billing as overage
```

Traces: FR-17, FR-18, FR-19, FR-20, FR-28.

---

## 6. Traceability Matrix (capability to requirements)

| Business capability (BRD Section 5) | Functional requirements |
| --- | --- |
| Identity and access | FR-IAM-01..05 |
| Customer management | FR-01..04 |
| Product catalog | FR-05..08 |
| Ordering | FR-09..12 |
| Subscription lifecycle | FR-13..16 |
| Usage and quota | FR-17..20 |
| Billing | FR-21..24 |
| Payment | FR-25..27 |
| Notification | FR-28..30 |
| Ticketing | FR-31..33 |

---

Document end.
