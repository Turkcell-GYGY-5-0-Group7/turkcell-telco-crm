# Data Model (ERDs)

Each service owns its schema; there is no shared database
([ADR-006](../adr/ADR-006-database-strategy.md)). Per-service entity-relationship diagrams are
kept as PDFs in `docs/erd/` (viewable in any browser via the links below) rather than as
Markdown, since they are generated diagrams, not prose.

| Diagram | Scope |
| --- | --- |
| [01 - Monolith (reference)](../erd/01_TelcoCRM_Monolith.pdf) | The legacy monolithic schema this platform replaces incrementally - useful context for *why* the domain looks the way it does |
| [02 - Microservices (overview)](../erd/02_TelcoCRM_Microservices.pdf) | Cross-service overview: every service's schema on one map, with the (non-existent) foreign keys between services shown only conceptually |
| [03 - Customer Service](../erd/03_TelcoCRM_CustomerSvc.pdf) | Customer, Address, Document |
| [04 - Product Catalog Service](../erd/04_TelcoCRM_ProductCatalogSvc.pdf) | Tariff, Addon, ProductOffering, TariffAddon |
| [05 - Order Service](../erd/05_TelcoCRM_OrderSvc.pdf) | Order, OrderItem, SagaState |
| [06 - Subscription Service](../erd/06_TelcoCRM_SubscriptionSvc.pdf) | Subscription, MsisdnPool, SimCard |
| [07 - Usage Service](../erd/07_TelcoCRM_UsageSvc.pdf) | Quota, UsageRecord, CdrEvent |
| [08 - Billing Service](../erd/08_TelcoCRM_BillingSvc.pdf) | Invoice, InvoiceLine, BillCycle |
| [09 - Payment Service](../erd/09_TelcoCRM_PaymentSvc.pdf) | Payment, PaymentAttempt, Wallet |
| [10 - Notification Service](../erd/10_TelcoCRM_NotificationSvc.pdf) | NotificationTemplate, Notification, Channel (MongoDB) |
| [11 - Ticket Service](../erd/11_TelcoCRM_TicketSvc.pdf) | Ticket, TicketComment, SLA |

There is no published ERD yet for identity-service, campaign-service, dispute-service, or
fraud-service (all added after the original ERD set) - their aggregates are documented in the
[Service Catalog](../architecture/service-catalog.md#4-data-ownership-summary) instead.

## Cross-service consistency

Since there is no shared database and no distributed transaction across services, consistency
between two services' data is always eventual, driven by the same outbox/inbox event flow
described in [Events & Messaging](events-and-messaging.md). When you need to reason about "does
service B's view of service A's data ever go stale, and for how long," start from the event
catalog's producer/consumer table for the specific event involved, not from the schema alone.
