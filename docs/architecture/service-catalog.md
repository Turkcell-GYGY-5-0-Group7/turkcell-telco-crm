# Service Catalog

## Telco CRM Platform

| Field | Value |
| --- | --- |
| Document | Microservice Catalog |
| Version | 1.0 |
| Parent | [../product/BRD.md](../product/BRD.md) |
| Technical authority | ADR-004 (architecture modes), ADR-006 (database), ADR-005 (communication) |
| Last updated | 2026-06-19 |

This catalog is the authoritative list of MVP services: ports, bounded contexts, aggregates,
architecture mode (ADR-004), and key APIs/events. Each service owns its PostgreSQL schema
(database-per-service, ADR-006). Architecture mode assignments are recommendations subject to
final Tech Lead Agent approval.

---

## 1. Infrastructure Services

| Service | Port | Responsibility |
| --- | --- | --- |
| api-gateway | 8080 | Edge routing, JWT validation, rate limiting, correlationId injection. |
| discovery-server | 8761 | Service registry (Eureka in dev; Kubernetes-native in prod per ADR-010). |
| config-server | 8888 | Centralized configuration (Spring Cloud Config in dev; ConfigMaps/Secrets in prod). |

---

## 2. Domain Services

### identity-service (port 9001)

- Bounded context: Identity and authorization (user/role/permission management).
- Architecture mode: CQRS + Mediator.
- Aggregates: User, Role, Permission.
- Responsibility: User/role/permission management and a domain projection of identity. **Token
  issuance and the login/refresh flow belong to Keycloak (ADR-011)** - identity-service does NOT mint
  JWTs. It administers users/roles via the Keycloak Admin API and owns app-specific authorization data
  and audit. See [keycloak-and-auth.md](keycloak-and-auth.md).
- Key APIs: `GET /api/v1/users/{id}`, `GET /api/v1/users`, `POST /api/v1/users`,
  `PUT /api/v1/users/{id}/roles`. (Authentication endpoints are served by Keycloak's realm token
  endpoint, not here.)
- Events: publish `user.created.v1`.
- Audit logging: mandatory.

### customer-service (port 9002)

- Bounded context: Customer management (master record of identity and contact data).
- Architecture mode: CQRS + Mediator.
- Aggregates: Customer, Address, Document.
- Key APIs: `POST /api/v1/customers`, `GET /api/v1/customers/{id}`, `PUT /api/v1/customers/{id}`,
  `POST /api/v1/customers/{id}/documents`, `POST /api/v1/customers/{id}/kyc/approve`.
- Events: publish `CustomerRegistered`, `CustomerKYCApproved`, `CustomerKYCRejected`, `CustomerUpdated`.
- Audit logging: mandatory.

### product-catalog-service (port 9003)

- Bounded context: Product catalog (tariffs, addons, VAS). Read-heavy; Redis cache-intensive.
- Architecture mode: CQRS + Mediator.
- Aggregates: Tariff, Addon, ProductOffering.
- Key APIs: `GET /api/v1/tariffs`, `GET /api/v1/tariffs/{code}`, `POST /api/v1/tariffs` (admin),
  `GET /api/v1/addons?tariffCode=...`.
- Events: publish `TariffCreated`, `TariffPriceChanged`, `TariffChanged`.

### order-service (port 9004)

- Bounded context: Order orchestration. Coordinates Customer -> Catalog -> Subscription -> Payment.
- Architecture mode: Domain Orchestration (saga).
- Aggregates: Order, OrderItem, SagaState.
- Key APIs: `POST /api/v1/orders`, `GET /api/v1/orders/{id}`, `POST /api/v1/orders/{id}/cancel`.
- Events: publish `OrderCreated`, `OrderConfirmed`, `OrderCancelled`; consume `PaymentCompleted`,
  `PaymentFailed`, `SubscriptionActivated`.

### subscription-service (port 9005)

- Bounded context: Subscription lifecycle state machine; MSISDN allocation/release.
- Architecture mode: CQRS + Mediator.
- Aggregates: Subscription, MsisdnPool, SimCard.
- Key APIs: `POST /api/v1/subscriptions` (internal), `GET /api/v1/subscriptions/{id}`,
  `POST /api/v1/subscriptions/{id}/suspend`, `.../reactivate`, `.../terminate`.
- Events: publish `SubscriptionActivated`, `SubscriptionSuspended`, `SubscriptionTerminated`,
  `MSISDNAllocated`, `MSISDNReleased`; consume `OrderConfirmed`, `PaymentCompleted`,
  `PaymentFailed` (after grace period).
- Audit logging: mandatory.

### usage-service (port 9006)

- Bounded context: Usage and quota. Consumes CDR events; updates usage counters. Write-heavy.
- Architecture mode: CQRS + Mediator.
- Aggregates: Quota, UsageRecord, CdrEvent.
- Key APIs: `GET /api/v1/usage/subscriptions/{id}/quota`,
  `GET /api/v1/usage/subscriptions/{id}/history?from=...&to=...`.
- Events: consume `CdrRecorded` (from CDR simulator); publish `UsageRecorded`,
  `QuotaThresholdReached`, `QuotaExceeded`.

### billing-service (port 9007)

- Bounded context: Invoice generation; monthly bill-run scheduler.
- Architecture mode: Domain Orchestration.
- Aggregates: Invoice, InvoiceLine, BillCycle.
- Key APIs: `GET /api/v1/invoices?customerId=...`, `GET /api/v1/invoices/{id}`,
  `GET /api/v1/invoices/{id}/pdf`, `POST /api/v1/billing/runs` (admin trigger).
- Events: publish `InvoiceGenerated`, `InvoicePaid`, `InvoiceOverdue`; consume `UsageAggregated`,
  `SubscriptionActivated`, `PaymentCompleted`.

### payment-service (port 9008)

- Bounded context: Payment; PSP integration (mock in MVP).
- Architecture mode: Domain Orchestration.
- Aggregates: Payment, PaymentAttempt, Wallet.
- Key APIs: `POST /api/v1/payments`, `GET /api/v1/payments/{id}`, `POST /api/v1/payments/{id}/refund`.
- Events: publish `PaymentCompleted`, `PaymentFailed`, `PaymentRefunded`; consume `OrderCreated`,
  `InvoiceGenerated` (auto-pay scenario).
- Audit logging: mandatory. Idempotency: mandatory (Idempotency-Key on POST).

### notification-service (port 9009)

- Bounded context: Multi-channel notification dispatch.
- Architecture mode: Simple Service Layer (template CRUD plus channel adapters; the single
  emitted event uses the outbox).
- Aggregates: NotificationTemplate, Notification, Channel.
- Key APIs: `POST /api/v1/notifications` (internal), `GET /api/v1/notifications/users/{id}/history`.
- Events: consume most domain events (template-based mapping); publish `NotificationDispatched`.

### ticket-service (port 9010)

- Bounded context: Customer requests/complaints; SLA management.
- Architecture mode: CQRS + Mediator.
- Aggregates: Ticket, TicketComment, SLA.
- Key APIs: `POST /api/v1/tickets`, `GET /api/v1/tickets/{id}`, `POST /api/v1/tickets/{id}/comments`,
  `.../assign`, `.../resolve`.
- Events: publish `TicketOpened`, `TicketAssigned`, `TicketResolved`, `SlaBreached`.

### campaign-service (port 9011)

- Bounded context: Campaign and catalog-limits validation at order/catalog time (ADR-027; Sprint 21,
  narrower slice of the full promotion-engine vision in TELCO-CRM-ADVANCED.md Section 2.4).
- Architecture mode: CQRS + Mediator.
- Infrastructure profile: transactional, per-customer-consistent - **not** cache-aside (contrast with
  product-catalog-service's Redis cache-aside profile, Section 5).
- Aggregates: Campaign, CampaignRedemption.
- Key APIs: `POST /api/v1/campaigns/validate` (internal, called synchronously by order-service at
  order-creation time; no gateway route - see `docs/api-contracts/campaign-service.md`).
- Events: consume `order.created.v1` (reserve), `payment.completed.v1` (confirm),
  `order.cancelled.v1` (release), `tariff.created.v1`/`tariff.price-changed.v1` (defensive staleness
  detection); publish campaign lifecycle/redemption events (not yet wired - Feature 21.4).
- Status: skeleton and schema only as of Sprint 21 Feature 21.1 - no domain behavior, API, or
  eventing wiring yet (21.2-21.4).

---

## 3. Architecture Mode Summary

| Mode (ADR-004) | Services |
| --- | --- |
| Simple Service Layer | notification-service |
| CQRS + Mediator | identity, customer, product-catalog, subscription, usage, ticket, campaign |
| Domain Orchestration | order, billing, payment |
| N/A (infrastructure) | api-gateway, discovery-server, config-server |

Each service MUST declare its mode in its own `README.md` (ADR-004).

---

## 4. Data Ownership Summary

| Service | Aggregates |
| --- | --- |
| identity-service | User, Role, Permission |
| customer-service | Customer, Address, Document |
| product-catalog-service | Tariff, Addon, ProductOffering, TariffAddon |
| order-service | Order, OrderItem, SagaState |
| subscription-service | Subscription, MsisdnPool, SimCard |
| usage-service | Quota, UsageRecord, CdrEvent |
| billing-service | Invoice, InvoiceLine, BillCycle |
| payment-service | Payment, PaymentAttempt, Wallet |
| notification-service | NotificationTemplate, Notification, Channel |
| ticket-service | Ticket, TicketComment, SLA |
| campaign-service | Campaign, CampaignRedemption |

Detailed entity-relationship diagrams: [`docs/erd/`](../erd/).

---

## 5. Infrastructure Profile

Per-service infrastructure declaration (ADR-006). Default primary store is PostgreSQL 17; a
non-default primary store is a Tech-Lead-approved exception. Binary artifacts go to MinIO, never a
database. Cache/search are added only where justified.

| Service | Primary store | Cache | Search | Object storage |
| --- | --- | --- | --- | --- |
| api-gateway | none (stateless) | Redis (rate limit) | - | - |
| discovery-server | none | - | - | - |
| config-server | none (config backend) | - | - | - |
| identity-service | PostgreSQL | - | - | - |
| customer-service | PostgreSQL | - | - | MinIO (KYC documents) |
| product-catalog-service | PostgreSQL | Redis (cache-aside) | - | - |
| order-service | PostgreSQL | - | - | - |
| subscription-service | PostgreSQL | - | - | - |
| usage-service | PostgreSQL | Redis (near-real-time quota) | - | - |
| billing-service | PostgreSQL | - | - | MinIO (invoice PDFs) |
| payment-service | PostgreSQL | Redis (idempotency keys) | - | - |
| notification-service | **MongoDB** (approved exception) + PostgreSQL outbox | - | - | - |
| ticket-service | PostgreSQL | - | - | - |
| campaign-service | PostgreSQL | - (transactional, per-customer-consistent - ADR-027) | - | - |

Notes:

- **notification-service** is the approved first MongoDB pilot (ADR-006): document/history data in
  MongoDB; its single event `notification.dispatched.v1` is emitted via a co-located PostgreSQL
  outbox (non-atomic across stores, acceptable for an idempotent non-financial event).
- **product-catalog-service** is the designated *second* polyglot pilot (post-MVP) as a MongoDB
  read-side projection fed by `tariff.created.v1` / `tariff.price-changed.v1`; PostgreSQL stays the
  write model.
- **customer-service** and **billing-service** use MinIO for binary artifacts; rows store only object
  references, accessed via pre-signed URLs.
- Financial/transactional services (order, billing, payment, subscription, identity, customer) remain
  PostgreSQL-only for their system of record.

---

Document end.
