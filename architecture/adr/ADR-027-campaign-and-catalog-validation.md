# ADR-027 Campaign and Catalog Validation

Status: Proposed
Date: 2026-07-11

---

## Context

TELCO-CRM-ADVANCED.md Section 2.4 proposes a full campaign/promotion engine: a rule engine
(eligibility, targeting segments, validity windows), a discount/benefit model applied at rating time,
segments computed from a data platform that does not exist yet (ADVANCED.md Section 5, phase P10),
and A/B/holdout groups. That design is not buildable today. This ADR scopes a narrower, buildable
capability - dynamic pricing and campaign-limit validation at order/catalog time - on top of the
existing platform-core, order-service saga (ADR-004 Domain Orchestration), and product-catalog-service
(ADR-004 CQRS + Mediator), per the "evolve, do not rewrite" guiding principle.

order-service already snapshots catalog price synchronously at order-creation
(`docs/api-contracts/order-service.md`). There is currently no concept of a promotional discount or
campaign-limit validation anywhere in the platform.

## Decision

### 1. Service boundary

A new **campaign-service** (proposed port 9011) is created; the campaign capability is **not** added
to product-catalog-service.

Rationale (ADR-006 database-per-service + bounded-context cohesion): product-catalog-service owns
stable, broadcast reference data (Tariff/Addon/ProductOffering), read-heavy with a Redis cache-aside
profile (`docs/architecture/service-catalog.md` Section 5). Campaign/CampaignRedemption is a
time-boxed, per-customer-stateful aggregate family requiring strong per-customer consistency on
redemption counts - a fundamentally different write/read profile. Co-locating them in one
database-per-service boundary would mix a cache-heavy, read-mostly aggregate family with a
write-heavy, per-customer-transactional one, degrading the simplicity of product-catalog-service's
cache-aside model and giving a campaign bug blast radius over tariff-price reads. A new service keeps
each bounded context's ADR-006 database boundary clean. This also matches the eventual target service
already named in TELCO-CRM-ADVANCED.md Section 6 (`campaign-service`), so this ADR builds the same
service narrower now rather than starting inside product-catalog-service and extracting it later.

### 2. Architecture mode (ADR-004)

**CQRS + Mediator.** Business rules exist (eligibility rules, redemption caps, validity windows),
domain logic is non-trivial, events are emitted (campaign lifecycle, redemption), and read/write
separation helps (fast validation reads on the order hot path vs. comparatively rare admin writes).
Domain Orchestration is not warranted: campaign-service does not own a multi-service saga with
compensation logic - order-service remains the sole saga owner. campaign-service's own operations
(create/activate a campaign, record a redemption) are single-aggregate, single-transaction, matching
the same criteria product-catalog-service and subscription-service already satisfy under CQRS +
Mediator.

### 3. Data ownership (ADR-006)

campaign-service owns a new `campaign-db` (PostgreSQL 17, database-per-service). Aggregates: Campaign
(including embedded campaign-limits: `totalRedemptionCap`, `perCustomerRedemptionCap`, validity
window) and CampaignRedemption. campaign-service stores only admin-curated tariff/offering *codes* it
targets - never a copy of tariff pricing data - avoiding a stale-cache/dual-source-of-truth problem.
No service accesses `campaign-db` directly except campaign-service (ADR-006 cross-service data rule).

### 4. Catalog validation model

Synchronous, at order-creation time, via REST/OpenFeign with a Resilience4j circuit breaker (ADR-005
internal synchronous communication model), mirroring order-service's existing catalog-price-snapshot
call:

- order-service calls `POST /api/v1/campaigns/validate` (internal) with the resolved tariff code,
  customerId, and an optional campaign code, and receives an eligibility decision plus the computed
  discount.
- order-service snapshots the discounted price into the OrderItem, same as it already does for the
  undiscounted catalog price.
- The circuit breaker is **fail-open**: if campaign-service is unreachable, the order proceeds at the
  full undiscounted price rather than being blocked. A campaign outage must never block order
  creation.

Redemption is **not** counted at validation time (an abandoned order must not consume a customer's
cap). Instead, campaign-service consumes the order-confirmation event from order-service's saga to
durably record a `CONFIRMED` redemption, and consumes `order.cancelled.v1` to release a `RESERVED`
one. campaign-service also consumes `tariff.created.v1` and `tariff.price-changed.v1` defensively, to
detect/flag a campaign whose target tariff code was retired or repriced, rather than mirroring tariff
data.

An event-driven eligibility *precomputation* model was considered and rejected for this step: the
eligibility decision must be resolved before order-service commits to a price; an async check would
force the saga to poll or block on a callback for no benefit at MVP campaign volumes.

### 5. Explicitly deferred

Segment-based targeting (`segment.membership.v1`), A/B and holdout groups, and rating-time discount
application (ADVANCED.md Section 2.4) are deferred - no data platform or real-time rating engine
exists yet (ADVANCED.md Section 5 is phase P10; billing remains monthly batch, Section 2.1). A later
ADR is required before any of these graduate into delivery.

## Consequences

### Positive

- Clean bounded-context and database separation from product-catalog-service (ADR-006).
- Reuses order-service's existing sync-call and circuit-breaker pattern - no new communication style
  introduced.
- Independently scalable/deployable from the read-heavy catalog service.

### Negative

- One more service to operate.
- order-service gains a second internal synchronous dependency (catalog + campaign) on its
  order-creation hot path; mitigated by a fail-open circuit breaker.

## Alternatives Considered

### Extend product-catalog-service

Rejected - mixes a cache-heavy, read-mostly aggregate family with a write-heavy, per-customer-stateful
one in the same database-per-service boundary; blast-radius and cache-model concerns (see Decision
Section 1).

### Fully event-driven eligibility precomputation

Rejected for the MVP validation step - would add callback/polling complexity to the order-creation
hot path with no benefit at current campaign volumes. Revisit once a segment platform and higher
campaign volume exist.

### Rating-time discount application (per ADVANCED.md)

Rejected for MVP scope - no real-time rating engine exists; billing is monthly batch. Campaign
discounts apply at order/price time instead.

## Related ADRs

* ADR-004 Architecture Style (mode selection: CQRS + Mediator)
* ADR-005 Service Communication Strategy (internal sync REST/OpenFeign + circuit breaker)
* ADR-006 Database Strategy (database-per-service, data ownership)
* ADR-009 Event Driven Architecture / ADR-019 Event Contract and Schema Governance (redemption commit
  events)
* ADR-017 Service Template Standard
