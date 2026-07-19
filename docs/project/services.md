# Services

This page is a curated, current-state view of every service in `microservices/`. The
[Service Catalog](../architecture/service-catalog.md) is the authoritative source (ports, bounded
contexts, aggregates, key APIs, events) - consult it before building against a service.

## Infrastructure services

| Service | Port | Responsibility |
| --- | --- | --- |
| api-gateway | 8080 | Edge routing, JWT validation, rate limiting, correlationId injection |
| discovery-server | 8761 | Service registry (Eureka in dev; Kubernetes-native DNS in prod) |
| config-server | 8888 | Centralized configuration (Spring Cloud Config in dev; ConfigMaps/Secrets in prod) |

## Domain services

| Service | Port | Architecture mode | Status |
| --- | --- | --- | --- |
| identity-service | 9001 | CQRS + Mediator | MVP complete |
| customer-service | 9002 | CQRS + Mediator | MVP complete |
| product-catalog-service | 9003 | CQRS + Mediator | MVP complete |
| order-service | 9004 | Domain Orchestration | MVP complete |
| subscription-service | 9005 | CQRS + Mediator | MVP complete |
| usage-service | 9006 | CQRS + Mediator | MVP complete |
| billing-service | 9007 | Domain Orchestration | MVP complete |
| payment-service | 9008 | Domain Orchestration | MVP complete |
| notification-service | 9009 | Simple Service Layer | MVP complete |
| ticket-service | 9010 | CQRS + Mediator | MVP complete |
| campaign-service | 9011 | CQRS + Mediator | Post-MVP, Sprint 21 - done |
| dispute-service | 9012 | Domain Orchestration | Post-MVP, Sprint 22 - code-complete |
| fraud-service | 9013 | CQRS + Mediator | Post-MVP, Sprint 23 - done |

## Channel service

| Service | Port | Purpose |
| --- | --- | --- |
| web-bff | 9020 | Backend-for-frontend composing domain APIs for the SvelteKit web app ([ADR-022](../adr/ADR-022-frontend-and-bff-strategy.md)); its only egress is `api-gateway` |

## Non-shipping template modules

`microservices/service-template` and `microservices/reference-service` are not deployed - they
are the canonical starting points for a new service ([ADR-017](../adr/ADR-017-service-template-standard.md)).
`service-template` is minimal (platform starters only, a ping/echo round trip);
`reference-service` adds JPA, Flyway, and outbox wiring as the fuller worked example. Copy one of
these, never a live domain service, when scaffolding something new.

## Data ownership

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
| dispute-service | Dispute, DisputeEvidence, DisputeStateHistory |
| fraud-service | MsisdnLifecycleSignal, FraudRule, FraudSignal, FraudCase |

## Infrastructure profile (per service)

Default primary store is PostgreSQL 17; anything else is a Tech-Lead-approved exception
([ADR-006](../adr/ADR-006-database-strategy.md)).

| Service | Primary store | Cache | Object storage |
| --- | --- | --- | --- |
| customer-service | PostgreSQL | - | MinIO (KYC documents) |
| product-catalog-service | PostgreSQL | Redis (cache-aside) | - |
| usage-service | PostgreSQL | Redis (near-real-time quota) | - |
| billing-service | PostgreSQL | - | MinIO (invoice PDFs) |
| payment-service | PostgreSQL | Redis (idempotency keys) | - |
| notification-service | **MongoDB** (approved exception) + PostgreSQL outbox | - | - |
| campaign-service | PostgreSQL (transactional, per-customer-consistent) | - | - |
| dispute-service | PostgreSQL | - | MinIO (evidence objects) |
| fraud-service | PostgreSQL | Redis (optional velocity counters, not source of truth) | - |
| all others (identity, order, subscription, ticket, api-gateway rate limiting) | PostgreSQL / Redis where noted | - | - |

## Service template pattern

Every domain service (created from `service-template`/`reference-service`, per ADR-017) shares
the same shape: a two-stage Alpine Dockerfile (Maven build stage, then a `jre-alpine` runtime
stage with a non-root user and an `/actuator/health` `HEALTHCHECK`), a `README.md` declaring its
architecture mode, mandatory starters (`starter-api`, `starter-security`, `starter-observability`)
plus whichever of `starter-mediator` / `starter-outbox` / `starter-inbox` the domain needs, and
its own Flyway migration set under `classpath:db/migration` plus the shared platform tables under
`classpath:db/migration/platform` (outbox/inbox).

For the full per-service key API and event list, see the
[Service Catalog](../architecture/service-catalog.md). For the frontend, see
[Getting Started](getting-started.md#running-the-web-frontend).
