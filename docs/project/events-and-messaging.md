# Events & Messaging

The full, authoritative registry is the [Event Catalog](../architecture/event-catalog.md). This
page summarizes the rules and the two documented saga sequences.

## Naming and versioning

- Format: `domain.event.v1` (for example `customer.registered.v1`).
- Events are immutable and must stay backward compatible
  ([ADR-019](../adr/ADR-019-event-contract-and-schema-governance.md)); a breaking change requires
  a new version (`.v2`), and the old version keeps running until every consumer has migrated.
- Producers publish only through the transactional outbox; consumers deduplicate only through the
  inbox. Nothing calls the Kafka client API directly.
- Consumers must tolerate unknown optional fields - schema evolution is additive-only in
  practice (see the Schema Evolution Log in the full event catalog for real examples).

## Producing domain services (by event family)

| Domain | Representative events |
| --- | --- |
| identity | `user.created.v1`, `user.deleted.v1` |
| customer | `customer.registered.v1`, `customer.kyc-approved.v1`, `customer.kyc-rejected.v1`, `customer.updated.v1` |
| product-catalog | `tariff.created.v1`, `tariff.price-changed.v1` |
| order | `order.created.v1`, `order.confirmed.v1`, `order.cancelled.v1` |
| payment | `payment.completed.v1`, `payment.failed.v1`, `payment.refunded.v1` |
| subscription | `msisdn.allocated.v1`, `msisdn.released.v1`, `subscription.activated.v1`, `subscription.suspended.v1`, `subscription.terminated.v1`, `subscription.activation-failed.v1` |
| usage | `usage.recorded.v1`, `quota.threshold-reached.v1`, `quota.exceeded.v1`, `usage.aggregated.v1` |
| billing | `invoice.generated.v1`, `invoice.paid.v1`, `invoice.overdue.v1` |
| ticket | `ticket.opened.v1`, `ticket.assigned.v1`, `ticket.resolved.v1`, `ticket.sla-breached.v1` |
| notification | `notification.dispatched.v1` |
| campaign | `campaign.created.v1`, `campaign.activated.v1`, `campaign.paused.v1`, `campaign.expired.v1`, `campaign.cancelled.v1` |
| dispute | `dispute.opened.v1`, `dispute.evidence-submitted.v1`, `dispute.resolved-customer.v1`, `dispute.resolved-merchant.v1`, `dispute.withdrawn.v1`, `dispute.closed.v1` |
| fraud | `fraud.signal-raised.v1`, `fraud.case-opened.v1`, `fraud.case-resolved.v1` |

## Saga: new-line order (event sequence)

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

This is the platform's primary acceptance scenario (AC-01, New Subscriber Onboarding) and the one
most worth understanding end to end before touching order-service, payment-service, or
subscription-service.

## Saga: dispute resolution (event sequence)

```text
dispute.opened.v1
  -> billing (Invoice.disputeStatus = ON_HOLD, excluded from dunning)
  -> payment (Payment.disputed = true, excluded from retry/expiry)
  -> ticket (auto-opens a DISPUTE-category ticket)

Resolution - upheld:
  dispute.resolved-customer.v1
    -> billing (unpaid invoice: real ADJUSTMENT line), OR
    -> payment (already-paid: real refund)
       [exactly one of the two acts, never both]
  -> dispute.closed.v1

Resolution - rejected:
  dispute.resolved-merchant.v1
    -> billing (hold cleared, no financial change)
    -> payment (disputed flag cleared, no financial change)
  -> dispute.closed.v1
```

`dispute.opened.v1` is the only signal a provisional hold ever travels on - it is never itself a
financial instruction. Only `dispute.resolved-customer.v1` authorizes a real credit or refund.
Every `dispute.*.v1` event uses `aggregate_id = disputeId` so Kafka partition ordering guarantees
these arrive in the order the state machine expects.

## Schema governance

Every event has an Avro schema registered before its first publish
([ADR-019](../adr/ADR-019-event-contract-and-schema-governance.md)), in backward-compatible mode.
The canonical schemas live in `platform/platform-event-contracts/src/main/avro/`, and every
producing service has a `*EventSchemaCompatTest` / `*EventContractTest` that fails the build the
moment a payload class drifts from its registered schema - this is a standing CI gate, not a
one-time check.

Debezium's outbox connectors route on the outbox table's `aggregate_type` column, converting the
value to lowercase to form the topic name (`<aggregate_type>.events`) - the actual Kafka Connect
payload on the wire is plain JSON (`schemas.enable=false`), with Avro/Schema Registry governing
the *shape* contract that CI enforces, not the literal on-topic bytes.

For the complete, currently-registered event list with every producer/consumer pair, and the
schema evolution history, see the full [Event Catalog](../architecture/event-catalog.md).
