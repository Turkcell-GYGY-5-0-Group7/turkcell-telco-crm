# Event Catalog

## Telco CRM Platform

| Field | Value |
| --- | --- |
| Document | Domain Event Catalog |
| Version | 1.0 |
| Parent | [../product/BRD.md](../product/BRD.md) |
| Technical authority | ADR-009 (event-driven architecture), ADR-019 (event contract and schema governance) |
| Last updated | 2026-07-04 |

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
| `customer.kyc-approved.v1` | customer-service | notification, order | KYC approved; customer becomes ACTIVE. |
| `customer.kyc-rejected.v1` | customer-service | notification | KYC rejected. |
| `customer.updated.v1` | customer-service | notification | Customer profile changed. |
| `tariff.created.v1` | product-catalog-service | notification | New tariff published. |
| `tariff.price-changed.v1` | product-catalog-service | billing, notification | Tariff price updated (versioned). |
| `order.created.v1` | order-service | payment, notification | Order placed; starts the saga. |
| `order.confirmed.v1` | order-service | subscription, notification | Order confirmed for fulfillment. (deferred; not produced in the MVP - subscription activates on `payment.completed.v1`) |
| `order.cancelled.v1` | order-service | payment, subscription, notification | Order cancelled; triggers compensation. |
| `payment.completed.v1` | payment-service | order, subscription, billing, notification | Payment succeeded. |
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

---

Document end.
