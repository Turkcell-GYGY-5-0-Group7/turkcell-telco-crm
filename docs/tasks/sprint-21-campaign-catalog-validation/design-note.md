# Design Note: Campaign / Catalog Validation

| Field | Value |
| --- | --- |
| Track | Sprint 21 |
| Status | Draft (input to ADR-027, which is now Accepted/ratified 2026-07-13 - see Section 4 amendment note in the ADR for the Section 7 item 1 resolution) |
| Author | architecture agent |
| Last updated | 2026-07-11 |
| Related | [ADR-027](../../../architecture/adr/ADR-027-campaign-and-catalog-validation.md), [service-catalog.md](../../architecture/service-catalog.md), [event-catalog.md](../../architecture/event-catalog.md) |

## 1. Problem

TELCO-CRM-ADVANCED.md Section 2.4 proposes a full campaign/promotion engine: a rule engine with
eligibility, targeting segments, validity windows, a discount/benefit model applied *at rating time*,
segments computed from a data platform that does not exist yet (Section 5, phase P10), and A/B/holdout
groups. None of that is buildable today. This note scopes a buildable slice: dynamic pricing and
campaign-limit validation at **order/catalog time**, reusing the existing order-service saga and the
existing catalog price-snapshot pattern.

## 2. Service boundary decision

### Option A - extend product-catalog-service

product-catalog-service (`docs/architecture/service-catalog.md`) is documented as read-heavy,
Redis-cache-aside, owning Tariff/Addon/ProductOffering - stable, broadcast reference data. Its
infrastructure profile is explicitly cache-intensive.

Campaigns are a different shape of data:

- Time-boxed lifecycle (DRAFT -> ACTIVE -> PAUSED -> EXPIRED -> CANCELLED), not a stable catalog entry.
- Per-customer redemption state that must be strongly consistent (a customer must not redeem past
  their cap even under concurrent order attempts) - a write-heavy, per-customer-transactional
  concern, unlike tariff reads which are broadcast and cacheable.
- An independent admin/lifecycle workflow (marketing creates/activates/pauses campaigns) that has
  nothing to do with tariff/addon pricing administration.

Putting Campaign/CampaignRedemption tables inside `product-catalog-db` would violate the cohesion
that makes product-catalog-service's Redis cache-aside model simple (cache invalidation reasoning
gets harder when the same schema holds both cache-friendly and write-heavy-per-customer data), and it
would give a campaign-redemption bug a blast radius that includes tariff-price reads on the checkout
hot path.

### Option B - new campaign-service (chosen)

A new service owns `campaign-db` (PostgreSQL, database-per-service, ADR-006). It stores only
admin-curated tariff/offering *codes* it targets (opaque strings), never a copy of tariff pricing -
so there is no risk of a stale price being read from two places. This also matches the target
end-state already named in TELCO-CRM-ADVANCED.md Section 6 (`campaign-service` listed as a "New"
service), so this is the same service being built narrower now rather than started inside
product-catalog-service and extracted later.

**Decision: new `campaign-service`, proposed port 9011.**

## 3. Architecture mode (ADR-004)

**CQRS + Mediator.** Business rules exist (eligibility, redemption caps, validity windows), domain
logic is non-trivial, events are emitted (campaign lifecycle, redemption), and read/write separation
matters because campaign *validation* reads must be fast on the order hot path while campaign
*administration* writes are comparatively rare and low-volume. Domain Orchestration is not warranted:
campaign-service does not itself coordinate a multi-service saga with compensation - order-service
remains the sole saga owner (unchanged), and campaign-service's own operations (create campaign,
record a redemption) are single-aggregate, single-transaction operations, structurally identical to
product-catalog-service and subscription-service, both already CQRS + Mediator.

## 4. Catalog validation model

**Synchronous, at order-creation time**, using the same internal-communication pattern order-service
already uses for catalog price snapshotting (`docs/api-contracts/order-service.md`: "Order capture
validates the customer (ACTIVE/KYC) and snapshots catalog price synchronously"; ADR-005: internal
sync calls are REST/OpenFeign + Resilience4j circuit breaker, Kafka async remains the primary
inter-service channel).

Flow:

1. order-service resolves the tariff/offering via its existing sync call to product-catalog-service.
2. order-service calls `POST /internal/campaigns/validate` (tokenless, network-perimeter trust -
   see ADR-027 Section 4, 2026-07-13 second ratification addendum) on campaign-service with
   `{ customerId, tariffCode, campaignCode? }`.
3. campaign-service evaluates eligibility (tariff code in `applicableTariffCodes`, validity window,
   `perCustomerRedemptionCap` not yet exhausted, `totalRedemptionCap` not yet exhausted) and returns
   `{ eligible, discountType, discountValue, campaignId }` or `{ eligible: false, reason }`.
4. order-service snapshots the *discounted* price into the OrderItem, exactly as it already snapshots
   the undiscounted catalog price.
5. campaign-service does **not** count the redemption yet - only a confirmed order should consume a
   customer's redemption slot (an abandoned order/cart must not burn the cap).

**Why not event-driven for the validation step itself:** the eligibility decision must be resolved
before order-service commits to a price. An async check would force the saga to poll or wait on a
callback, adding latency/complexity to an already-working sync price-snapshot pattern, for no benefit
at MVP campaign volumes.

**Where event-driven IS used:**

- campaign-service consumes `order.confirmed.v1` (or, per the event-catalog's documented MVP note
  that `order.confirmed.v1` is deferred and subscription currently activates on `payment.completed.v1`
  - campaign-service should consume whichever event order-service's saga treats as "order is real" at
  build time; this is called out as an open item for the domain-engineer implementing 21.4) to
  durably write a `CampaignRedemption` row (status CONFIRMED).
- campaign-service consumes `order.cancelled.v1` to release a `RESERVED` redemption back to available.
- campaign-service consumes `tariff.created.v1` and `tariff.price-changed.v1` **defensively** - not to
  mirror tariff data, but to detect a campaign whose target tariff code was retired/changed and flag
  or auto-expire it, so a dangling reference is caught by campaign-service rather than surfacing only
  as a runtime 404 at order time.

**Resilience:** the order-service -> campaign-service call is wrapped in a Resilience4j circuit
breaker with a **fail-open** default - if campaign-service is unreachable, the order proceeds at the
full undiscounted catalog price rather than blocking order creation. A campaign outage must never
block the ability to place an order.

## 5. Campaign-limits aggregate shape

```text
Campaign
  id, code (unique), name, description
  discountType        : PERCENTAGE | FIXED_AMOUNT
  discountValue        : numeric
  applicableTariffCodes: string[]           (admin-curated, opaque references to product-catalog codes)
  validFrom, validTo    : timestamp          (validity window)
  status                : DRAFT | ACTIVE | PAUSED | EXPIRED | CANCELLED
  totalRedemptionCap    : integer, nullable  (null = unlimited; usage cap across all customers)
  perCustomerRedemptionCap : integer, default 1 (per-customer redemption limit)
  createdAt, updatedAt, version

CampaignRedemption
  id, campaignId, customerId, orderId
  status    : RESERVED | CONFIRMED | RELEASED
  redeemedAt, confirmedAt (nullable)
```

`totalRedemptionCap` and `perCustomerRedemptionCap` together are the "campaign-limits" concept: a
global usage cap and a per-customer cap, both enforced by counting `CONFIRMED` (+ still-live
`RESERVED`, to prevent a race between two concurrent orders) rows in `CampaignRedemption`.

A total-discount-budget cap (spend-based, not count-based) is a plausible future addition but is left
out of the MVP shape to keep the aggregate small; flagged here for a later iteration.

## 6. Explicitly deferred (per TELCO-CRM-ADVANCED.md scope-down)

- Segment-based targeting (`segment.membership.v1`) - no data platform exists (ADVANCED.md Section 5
  is phase P10). MVP eligibility is limited to attributes resolvable synchronously at order time.
- A/B and holdout groups, campaign performance analytics feedback loop - depends on the same
  non-existent data platform.
- Rating-time discount application - the MVP has no real-time rating engine (billing is a monthly
  batch, ADVANCED.md Section 2.1); campaign discounts apply at order/price time, not rating time.

## 7. Open questions for the sprint 21 feature authoring pass

- ~~Which order-service event campaign-service should treat as "order is real" for redemption
  commit~~ - **RESOLVED** by tech-lead ADR-027 ratification (2026-07-13, Section 4 amendment):
  `RESERVED` on `order.created.v1`, `CONFIRMED` on `payment.completed.v1` (not `order.confirmed.v1`,
  confirmed deferred/unproduced), `RELEASED` on `order.cancelled.v1`, plus a required
  `reservedUntil` reservation-expiry reaper. See ADR-027 Section 4.
- ~~Endpoint path/auth model for the synchronous validate call~~ - **RESOLVED** by tech-lead
  ADR-027 ratification addendum (2026-07-13): `POST /internal/campaigns/validate`, tokenless,
  network-perimeter trust, matching the platform-wide `/internal/**` pattern - not
  `/api/v1/campaigns/validate`. See ADR-027 Section 4.
- ~~order-service schema addition to correlate an order/OrderItem back to the campaign that priced
  it~~ - **RESOLVED** by tech-lead ADR-027 ratification addendum (2026-07-13): approved as scoped -
  nullable `campaign_id`/`campaign_code` on `order_items` plus `OrderCreatedEvent.OrderItemPayload`.
  See ADR-027 Section 4.
- Whether campaign eligibility needs a `channel` attribute (web/app/dealer/call-center) at MVP or can
  wait - ADVANCED.md Section 2.7 (BFF/channels) is itself a separate deferred item. Still open.
