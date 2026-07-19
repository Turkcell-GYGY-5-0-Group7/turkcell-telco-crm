# Event Catalog

## Telco CRM Platform

| Field | Value |
| --- | --- |
| Document | Domain Event Catalog |
| Version | 1.0 |
| Parent | [../product/BRD.md](../product/BRD.md) |
| Technical authority | ADR-009 (event-driven architecture), ADR-019 (event contract and schema governance) |
| Last updated | 2026-07-07 |

All events are immutable, versioned (`domain.event.v1`), Avro-encoded, and registered in the
Schema Registry. Events are published through the transactional outbox and consumed
idempotently via the inbox pattern (ADR-005). Topic names follow `domain.event` with the
version carried by the schema. Debezium routes on the outbox `aggregate_type` column via
`${routedByValue}.events`; therefore `aggregate_type` MUST be the lowercase domain (e.g.
`subscription`), producing the `subscription.events` topic consumers subscribe to. A PascalCase
`aggregate_type` routes to the wrong topic and is silently never delivered.

---

## 1. Event Naming and Versioning Rules

- Format: `domain.event.v1` (for example `customer.registered.v1`).
- Events MUST be immutable and backward compatible (ADR-019).
- A breaking change requires a new version (`.v2`); old versions remain until consumers migrate.
- Producers MUST publish via outbox; consumers MUST deduplicate via inbox.

---

## 2. Event Registry

| Event | Producer | Consumers | Purpose |
| --- | --- | --- | --- |
| `customer.registered.v1` | customer-service | notification | New customer created. |
| `user.created.v1` | identity-service | - | New identity user provisioned (Keycloak-backed). No current consumer; registered for future audit/notification integration. |
| `user.deleted.v1` | identity-service | - | Identity user deleted/deactivated. No current consumer; registered for future audit/notification integration. |
| `customer.kyc-approved.v1` | customer-service | notification, order | KYC approved; customer becomes ACTIVE. |
| `customer.kyc-rejected.v1` | customer-service | notification | KYC rejected. |
| `customer.updated.v1` | customer-service | notification | Customer profile changed. |
| `tariff.created.v1` | product-catalog-service | notification, campaign | New tariff published. campaign-service consumes defensively (Feature 21.4.3, ADR-027 Section 4) to log when a tariff code referenced by an ACTIVE campaign is (re)created. |
| `tariff.price-changed.v1` | product-catalog-service | billing, notification, campaign | Tariff price updated (versioned). campaign-service consumes defensively (Feature 21.4.3, ADR-027 Section 4) to flag an ACTIVE campaign whose `applicable_tariff_codes` references the repriced tariff - never to mirror pricing data. |
| `order.created.v1` | order-service | payment, notification, campaign | Order placed; starts the saga. campaign-service consumes it (Feature 21.4.3) to create a `RESERVED` `CampaignRedemption` row per campaign-priced order item, keyed by `(campaignId, customerId, orderId)`. |
| `order.confirmed.v1` | order-service | subscription, notification | Order confirmed for fulfillment. (deferred; not produced in the MVP - subscription activates on `payment.completed.v1`) |
| `order.cancelled.v1` | order-service | payment, subscription, notification, campaign | Order cancelled; triggers compensation. campaign-service consumes it (Feature 21.4.2) to release a `RESERVED` `CampaignRedemption` back to available. |
| `payment.completed.v1` | payment-service | order, subscription, billing, notification, campaign | Payment succeeded. campaign-service consumes it (Feature 21.4.2, ADR-027 Section 4 ratification) as the "order is real" trigger, transitioning the matching `CampaignRedemption` RESERVED -> CONFIRMED. |
| `payment.failed.v1` | payment-service | order, subscription, notification | Payment failed; may trigger retry. |
| `payment.refunded.v1` | payment-service | order, notification | Refund issued (compensation). |
| `msisdn.allocated.v1` | subscription-service | notification | MSISDN allocated to a subscription. |
| `msisdn.released.v1` | subscription-service | - | MSISDN returned to the pool. |
| `subscription.activated.v1` | subscription-service | order, billing, notification | Subscription activated. |
| `subscription.suspended.v1` | subscription-service | billing, notification | Subscription suspended (non-payment). |
| `subscription.terminated.v1` | subscription-service | billing, notification | Subscription terminated. |
| `subscription.activation-failed.v1` | subscription-service | payment, order, notification | Subscription activation failed; triggers saga compensation. |
| `usage.recorded.v1` | usage-service | - | Usage applied to quota. |
| `quota.threshold-reached.v1` | usage-service | notification | 80% usage threshold reached. |
| `quota.exceeded.v1` | usage-service | billing, notification | 100% usage reached; overage begins. |
| `usage.aggregated.v1` | usage-service | billing | Period usage aggregated for billing. |
| `invoice.generated.v1` | billing-service | notification | Invoice created and PDF rendered. Not consumed by payment-service in the MVP - paying an invoice is a customer/admin-initiated `POST /api/v1/payments` call (Section 14.2), not an auto-pay reaction to this event. |
| `invoice.paid.v1` | billing-service | notification | Invoice settled. |
| `invoice.overdue.v1` | billing-service | notification, ticket | Invoice overdue. |
| `ticket.opened.v1` | ticket-service | notification | Ticket created. |
| `ticket.assigned.v1` | ticket-service | notification | Ticket assigned to a team/agent. |
| `ticket.resolved.v1` | ticket-service | notification | Ticket resolved. |
| `ticket.sla-breached.v1` | ticket-service | notification | SLA breached for a ticket. |
| `notification.dispatched.v1` | notification-service | - | Notification sent on a channel. |
| `campaign.created.v1` | campaign-service | - | Campaign created in DRAFT status (Feature 21.4.1). No current consumer; registered for future notification/reporting integration. |
| `campaign.activated.v1` | campaign-service | - | Campaign transitioned DRAFT/PAUSED -> ACTIVE (Feature 21.4.1). No current consumer; registered for future notification/reporting integration. |
| `campaign.paused.v1` | campaign-service | - | Campaign transitioned ACTIVE -> PAUSED (Feature 21.4.1). No current consumer; registered for future notification/reporting integration. |
| `campaign.expired.v1` | campaign-service | - | Campaign transitioned ACTIVE/PAUSED -> EXPIRED, explicitly or defensively when `validTo` has passed (Feature 21.4.1). No current consumer; registered for future notification/reporting integration. |
| `campaign.cancelled.v1` | campaign-service | - | Campaign transitioned any non-terminal status -> CANCELLED (Feature 21.4.1). No current consumer; registered for future notification/reporting integration. |

---

## 3. Saga: New-Line Order (event sequence)

```text
order.created.v1
  -> payment.completed.v1
       -> subscription.activated.v1
            -> order (FULFILLED), notification (welcome SMS)

Compensation on activation failure:
  subscription.activation-failed.v1
  -> payment.refunded.v1
  -> order.cancelled.v1
```

---

## 4. Schema Governance

- Each event has an Avro schema registered before first publish (ADR-019).
- Compatibility mode: backward (consumers can read older producer schemas).
- Schemas are versioned alongside the producing service.
- Consumers MUST tolerate unknown optional fields.

---

## 5. Schema Evolution Log

Additive, backward-compatible field changes (ADR-019). Each entry is a new nullable/optional
field only; no field was renamed, removed, or retyped.

| Date | Event | Field added | Reason |
| --- | --- | --- | --- |
| 2026-07-04 | `payment.completed.v1`, `payment.failed.v1` | `invoiceId` (nullable string) | Carries the invoice being settled so billing-service's `PaymentCompletedBillingConsumer` can mark the invoice paid when a customer pays via `POST /api/v1/payments` with an `invoiceId` (Section 14.2). Null for order-only charges. |
| 2026-07-04 | `quota.threshold-reached.v1`, `quota.exceeded.v1` | `customerId` (nullable string) | Lets notification-service route the 80%/100% quota SMS to the real customer instead of falling back to the literal `unknown`. Resolved by usage-service from the `Quota` aggregate's locally stored `customer_id` (set at provisioning time from `subscription.activated.v1`). Null only for events emitted before this field existed (rolling-upgrade compatibility). |
| 2026-07-07 | `customer.registered.v1` | `registeredByUserId` (nullable string) | Carries the Keycloak subject of the caller for genuine self-service registration, so identity-service's new inbox consumer can upsert `users.customer_id` and close the identity-to-customer linkage gap (Section 14.1.1 ruling). Null for agent/dealer-assisted registration - those customers stay unlinked until a future "claim my account" flow. |
| 2026-07-19 | `customer.registered.v1`, `customer.updated.v1` | `email`, `phone` (nullable strings) | Optional contact info added to the customer master record (FR-03, Sprint 24 feature 24.5). Populated from the aggregate at publish time; null when the customer has none. PII: consumers MUST mask both in logs/telemetry (ADR-021); stored plain at rest per Sprint 24 design-note D5 (encryption is mandated only for TCKN and card data). |
| 2026-07-19 | `order.created.v1` | item `itemType` (string, default `"TARIFF"`), item `productCode`, item `targetSubscriptionId` (nullable strings) | Order-model generalization (FR-09/FR-22, Sprint 24 feature 24.2, design-note D1/D2): items are now TARIFF or ADDON lines and orders carry a derived kind (NEW_LINE, ADDON, PLAN_CHANGE). `productCode` is the catalog addon code (null on TARIFF items); `targetSubscriptionId` is set on standalone ADDON items and on a PLAN_CHANGE order's tariff item. For ADDON items the pre-existing non-null `tariffId`/`tariffName` item fields generalize to the catalog product snapshot (addon id/name). Existing consumers unaffected: payment-service charges `totalAmount`, campaign-service reads per-item `campaignId` only, and both deserialize with unknown-field tolerance. |

---

## 6. Schema Governance Reconciliation Log (Feature 14.5)

Tracking reference: `docs/tasks/sprint-14-testing-and-hardening/14.5-avro-schema-governance-ruling.md`
(tech-lead ruling, ADR-019 Amendment (2026-07-07)). Unlike Section 5 above (additive field changes to
already-registered schemas), this log records the one-time reconciliation of the canonical schema
directory (`platform/platform-event-contracts/src/main/avro/`) against the real, currently-shipping
event payloads, closing a gap where the `.avsc` files had never been cross-checked against the code
that actually publishes each event.

| Date | Change type | Detail |
| --- | --- | --- |
| 2026-07-07 | Reconciled (type/shape fix, no behavior change) | 7 pre-existing canonical schemas corrected to match the real, already-shipping payload: `order-created.avsc` (added `items` array of the nested `OrderItemPayload` record, added `idempotencyKey`, removed `currency`, renamed/retyped `createdAt`(long) to `occurredAt`(string)); `payment-completed.avsc` (added `customerId`, removed `currency`, renamed/retyped `completedAt`(long) to `occurredAt`(string)); `cdr-recorded.avsc` (retyped `occurredAt` from `long`/timestamp-millis to `string`); `usage-aggregated.avsc` (retyped `periodStart`, `periodEnd`, `aggregatedAt` from `long` to `string`); `usage-recorded.avsc` (retyped `recordedAt` from `long` to `string`); `quota-exceeded.avsc` (retyped `exceededAt` from `long` to `string`); `quota-threshold-reached.avsc` (retyped `reachedAt` from `long` to `string`). The nested `OrderItemPayload` type required for `order-created.avsc`'s `items` field is defined inline within `order-created.avsc` itself (not an independent Schema Registry subject - see tracking doc's tech-lead ruling on the registry-parsing conflict). |
| 2026-07-07 | Added (new canonical schema, closing a previously-unregistered gap) | 14 event types that were already being published in production code but had no canonical `.avsc` file at all: `order.cancelled.v1`, `payment.failed.v1`, `payment.refunded.v1`, `tariff.created.v1`, `tariff.price-changed.v1`, `ticket.opened.v1`, `ticket.assigned.v1`, `ticket.resolved.v1`, `ticket.sla-breached.v1`, `invoice.paid.v1`, `invoice.overdue.v1`, `notification.dispatched.v1`, `user.created.v1`, `user.deleted.v1`. All 14 are now registered in the Schema Registry under the standard backward-compatibility mode. See Section 2 above for the two identity-service rows this addition required in the event registry table. |
| 2026-07-07 | Renamed (naming-convention fix, no shape change) | `EventEnvelope.avsc` -> `event-envelope.avsc`, closing a kebab-case-filename convention violation (ADR-019 Amendment, point A5). The Avro record's own `name` field is unchanged (`EventEnvelope`, PascalCase, drives Java class generation) - only the filename changed, along with the corresponding `avro-maven-plugin` property in `platform/platform-event-contracts/pom.xml`. |
| 2026-07-07 | Tooling (process change, going forward) | Added `AvroContractAssertions`, a shared, type-and-nullability-aware compatibility checker (`platform/platform-event-contracts/src/test/java/com/telco/platform/events/testsupport/AvroContractAssertions.java`, shipped as this module's test-jar). Every one of the 32 canonical schemas (18 reconciled/unchanged + 14 newly added above) now has a per-service `*EventSchemaCompatTest`/`*EventContractTest` that loads the schema directly from the canonical `platform-event-contracts` module (not a hand-maintained local copy) and asserts field name, type, and nullability against the real Java payload class or captured runtime payload. Going forward, any producing service whose payload class drifts from its canonical schema (field removed/renamed/retyped, or a nullability mismatch) fails that service's build - this is now a required, standing quality gate for every service that publishes a domain event, not a one-time manual reconciliation. |

---

Document end.
