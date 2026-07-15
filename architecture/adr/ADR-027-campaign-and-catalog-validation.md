# ADR-027 Campaign and Catalog Validation

Status: Accepted
Date: 2026-07-11
Ratified: 2026-07-13 (tech-lead; see Section 4 amendment notes below)

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

- order-service calls `POST /internal/campaigns/validate` (tokenless, network-perimeter trust) with
  the resolved tariff code, customerId, and an optional campaign code, and receives an eligibility
  decision plus the computed discount. See the ratification addendum below (2026-07-13) for the
  path/auth-model correction and rationale.
- order-service snapshots the discounted price into the OrderItem, same as it already does for the
  undiscounted catalog price.
- The circuit breaker is **fail-open**: if campaign-service is unreachable, the order proceeds at the
  full undiscounted price rather than being blocked. A campaign outage must never block order
  creation.

Redemption is **not** counted at the synchronous validation call (an abandoned order must not
consume a customer's cap); the call is a pure read - it decides eligibility and a discount, and writes
nothing. A `CampaignRedemption` row's lifecycle (`RESERVED` -> `CONFIRMED` | `RELEASED`) is driven
entirely by consuming order-service's own real, already-produced events:

- **`RESERVED`**: created when campaign-service consumes `order.created.v1` (real, produced by
  order-service's `CreateOrderCommandHandler` at order placement, carrying `orderId`/`customerId` and,
  once wired, the `campaignId` the order item was priced against). This is what makes the
  `perCustomerRedemptionCap`/`totalRedemptionCap` counting rule (count `CONFIRMED` + `RESERVED` rows)
  actually race-safe: the slot is held from the moment the order exists, not only once payment clears.
- **`CONFIRMED`**: set when campaign-service consumes `payment.completed.v1` (real, produced by
  payment-service; `docs/architecture/event-catalog.md` line 47), looked up by `orderId`.
- **`RELEASED`**: set when campaign-service consumes `order.cancelled.v1` (real, produced by
  order-service; event-catalog.md line 46), looked up by `orderId`.
- campaign-service also consumes `tariff.created.v1` and `tariff.price-changed.v1` defensively, to
  detect/flag a campaign whose target tariff code was retired or repriced, rather than mirroring tariff
  data.

**Reservation-expiry reaper (required).** Not every `RESERVED` row resolves to `CONFIRMED` or
`RELEASED`: order-service has no order-abandonment timeout event today (an order left at
`PENDING_PAYMENT` with no follow-up `payment.completed.v1`/`order.cancelled.v1` simply never
transitions), so a `RESERVED` redemption can otherwise accumulate indefinitely and permanently occupy a
cap slot. `CampaignRedemption` therefore carries a `reservedUntil` timestamp (set at reservation time,
same shape as `subscription-service`'s `MsisdnPool.reservedUntil`), and campaign-service runs a
`@Scheduled` reaper that releases stale `RESERVED` rows back to available, coordinated across replicas
with `starter-lock`'s explicit-lease `DistributedLock` (ADR-024) - the same pattern
`subscription-service`'s MSISDN reservation-expiry reaper already ships (Sprint 17 Feature 17.3). This
is a new, required addition to this ADR's scope (not present in the original Proposed draft), added
during tech-lead ratification; it is required for the redemption-cap guarantee this ADR exists to
provide to actually hold under real-world order abandonment, not just under the happy path.

An event-driven eligibility *precomputation* model was considered and rejected for this step: the
eligibility decision must be resolved before order-service commits to a price; an async check would
force the saga to poll or block on a callback for no benefit at MVP campaign volumes.

**(Ratification note, 2026-07-13):** the Proposed draft of this section said campaign-service
"consumes the order-confirmation event from order-service's saga" without naming it, and separately
said a `RESERVED` redemption is "released" by `order.cancelled.v1` without ever specifying where a
`RESERVED` row is created - internally inconsistent (nothing would exist to release) and, on the
"order-confirmation event" question, unbuildable as literally read: `docs/architecture/event-catalog.md`
line 45 and `docs/api-contracts/order-service.md`'s own `ConfirmOrderCommandHandler` both confirm
`order.confirmed.v1` is deferred and not produced anywhere in the codebase (no `.avsc`, no publish call
site) - subscription/order confirmation in the MVP saga runs on `payment.completed.v1` instead
(event-catalog.md Section 3). Built as originally drafted, the redemption-commit consumer would have
subscribed to a topic that is never populated, silently defeating the cap enforcement that is this
service's entire reason for existing (Section 1). This was independently flagged as an unresolved open
item by the Sprint 21 design note (Section 7) and feature breakdown (21.2.2, 21.4.2/21.4.3) pending
tech-lead confirmation before implementation; the resolution above - `order.created.v1` for `RESERVED`,
`payment.completed.v1` for `CONFIRMED`, `order.cancelled.v1` for `RELEASED`, plus the reaper - is that
confirmation, matches those docs' own recommendation, and is now the binding decision. If
`order.confirmed.v1` is later promoted to a real, produced event, campaign-service should switch its
`CONFIRMED` trigger to it instead of `payment.completed.v1`; that is a follow-up event-contract change,
not required by this ADR.

**(Second ratification addendum, 2026-07-13, Section 4 - endpoint path and auth model):**
Sprint 21 Feature 21.3 flagged that this Decision literally named the validate endpoint's path as
`/api/v1/campaigns/validate` without settling its auth model, even though the call is
service-to-service only (order-service -> campaign-service), never gateway-routed or externally
called (21.1.3 deliberately registers no gateway route for campaign-service). Ruling: the path is
corrected to **`POST /internal/campaigns/validate`, tokenless, network-perimeter trust** - the same
pattern already established platform-wide (tech-lead ruling 2026-07-06,
`product-catalog-service`'s `CatalogSecurityConfig`/`TariffInternalController`, mirrored verbatim by
`customer-service`, `order-service`'s `OrderInternalController`, `subscription-service`) and codified
at the edge in `api-gateway`'s `GatewayRouteConfig`/`GatewaySecurityConfig`: **`/internal/**` is the
only path prefix the gateway excludes from public routing** (`internal-deny-route` -> 404); every
other prefix, including `/api/v1/**`, is proxied through and requires a JWT there
(`anyRequest().authenticated()`). A service's own security config marking an `/api/v1/**` route
`permitAll` does not change what the gateway does with that path - it only creates a route that is
unauthenticated wherever it is reached directly (bypassing the gateway), which is precisely the class
of bug the 2026-07-06 ruling closed for the tariff endpoints. `/api/v1` is reserved, by ADR-015
convention, for the externally-exposed, JWT-bearing contract surface; an endpoint that is never called
except service-to-service does not belong there regardless of its auth model, and keeping it there
while marking it tokenless would silently reopen the exact internet-reachability gap already fixed
once this sprint area touches shared infrastructure.

On the customerId-carries-PII objection: it does not warrant different treatment. The existing
`/internal/customers/{customerId}` endpoint (`CustomerInternalController`, called tokenless by
`order-service`'s own `CustomerServiceClient`) already returns full customer PII under this exact
network-perimeter-trust model; a request that merely carries a `customerId` as a correlation key (and
returns only a discount decision, not customer data) is strictly less sensitive than that established
precedent. Standard, always-on controls - structured-log PII masking (ADR-012/ADR-021) and the
network-perimeter boundary itself (ADR-011) - are the correct and sufficient controls here, exactly as
they already are for every other `/internal/**` endpoint. No JWT, no separate service-to-service token,
and no `/api/v1` placement are required. `docs/api-contracts/campaign-service.md` and the Sprint 21
task breakdown (21.3.1) are corrected to match; this addendum is binding.

**(Third ratification addendum, 2026-07-13, Section 4 - order-service schema addition for campaign
correlation):** Feature 21.3.3 flagged that wiring discounted pricing into
`CreateOrderCommandHandler` requires order-service to persist which campaign priced each `OrderItem`
(a nullable `campaign_id`/`campaign_code` column on `order_items`, a new Flyway migration, and a
nullable `campaignId` field on `OrderCreatedEvent.OrderItemPayload`), since `payment.completed.v1`
carries only `orderId`/`customerId` and campaign-service has no other way to correlate a later
redemption-confirmation back to a specific campaign. Ruling: **approved as scoped, no alternative
required.** This is not new scope invented by the implementing agent - the "once wired" language
already present in this Decision's `RESERVED`-creation bullet (`order.created.v1`, "carrying
`orderId`/`customerId` and, once wired, the `campaignId` the order item was priced against")
anticipated exactly this addition; this addendum simply confirms it explicitly rather than leaving it
implied. The alternative flagged for consideration - campaign-service re-deriving the applicable
campaign itself at redemption-confirmation time, without any order-service schema change - is
rejected: eligibility state (validity window, redemption caps, campaign status) can change between
order-creation time and `payment.completed.v1` consumption, so a later re-evaluation could disagree
with what was actually used to price the order, corrupting the exact redemption bookkeeping this ADR
exists to make correct (Section 1). order-service, as the system of record for what price it actually
charged, must be the source of truth for which campaign produced that price, recorded at the moment of
pricing. Field-level shape is also correctly item-scoped, not order-scoped: `CampaignRedemption`
(Section 5) is keyed one row per `(campaignId, customerId, orderId)`, and a single order can carry
multiple items priced against different campaigns, so `campaignId` belongs on
`OrderItem`/`OrderItemPayload`, not on `Order`/the event envelope. Persisting to the `order_items`
column (not only embedding the value in the outbox event payload in-memory) is required, matching the
symmetry already established by `tariff_id`/`tariff_code`/`tariff_version` on the same table (added in
`V5__add_tariff_snapshot_to_order_items.sql` for the identical reason: order-service must remain the
durable source of truth for what priced an order line, for its own future queries/support/audit needs,
not only for the outbox event's sake). The new column is nullable and the `OrderItemPayload` field
addition is additive/nullable, so this is Avro-backward-compatible per ADR-019 and requires the
standard `*EventSchemaCompatTest` update, not a schema-registry version bump beyond the ordinary
additive-field flow. This addendum is binding; `docs/api-contracts/order-service.md` is updated per
21.3.3's own deliverable list to note the optional campaign discount and correlation field.

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
- campaign-service now also owns a `@Scheduled` reservation-expiry reaper and a `starter-lock`
  dependency (added in ratification, Section 4), the same operational shape as
  `subscription-service`'s MSISDN reaper - one more moving part, justified by closing a real
  cap-leak/indefinite-hold gap rather than added speculatively.

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
* ADR-024 Distributed Lock Strategy (reservation-expiry reaper coordination, Section 4 amendment)
