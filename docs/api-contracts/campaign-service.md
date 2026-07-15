# campaign-service - API Contract

| Field | Value |
| --- | --- |
| Port | 9011 |
| Mode | CQRS + Mediator |
| Base path | `/api/v1` |
| Owning sprint | [Sprint 21](../tasks/sprint-21-campaign-catalog-validation/README.md) |
| Build status | TODO |
| Requirements | Not yet assigned FR/AC IDs in `docs/product/requirements.md`; scope is defined by ADR-027 and `docs/tasks/sprint-21-campaign-catalog-validation/design-note.md`. |

Bounded context: campaign lifecycle and catalog-limits validation at order/catalog time (ADR-027).
Owns `Campaign` and `CampaignRedemption` in its own `campaign-db` (PostgreSQL, database-per-service,
ADR-006). Infrastructure profile is transactional and per-customer-consistent, **not** cache-aside -
contrast with product-catalog-service (`docs/architecture/service-catalog.md` Section 5).

As of Sprint 21 Feature 21.4 this service has admin CQRS wiring (`/api/v1/campaigns/**`,
JWT-required), domain eligibility/redemption-cap logic, the validation API (21.3), and full eventing
wiring (21.4): campaign lifecycle outbox events, order/payment/tariff inbox consumers, and a
`starter-lock`-coordinated reservation-expiry reaper.

## Authentication and Authorization

- `/api/v1/campaigns/**` (admin campaign lifecycle: create/activate/pause/cancel/get/list) requires a
  valid JWT with `ROLE_ADMIN` (`@PreAuthorize("hasRole('ADMIN')")` on every route). No gateway route is
  registered for this surface in the Sprint 21 MVP (an admin-facing campaign management UI is a
  separate, future task).
- `/internal/campaigns/validate` (below) is called synchronously and directly by order-service
  (mirroring `ProductCatalogServiceClient`'s direct-to-service call pattern), **not** routed through
  api-gateway - tokenless, network-perimeter trust (ADR-005, ADR-011, tech-lead ruling 2026-07-13,
  ADR-027 Decision Section 4 second ratification addendum). `api-gateway`'s `GatewayRouteConfig`
  registers no route at all for campaign-service, and independently, `/internal/**` is the only path
  prefix the gateway excludes from public routing even if a route existed
  (`internal-deny-route` -> 404) - the same model already established for
  `product-catalog-service`'s `/internal/tariffs/**` (tech-lead ruling 2026-07-06).

## Endpoints

| Method | Path | Auth | Summary |
| --- | --- | --- | --- |
| POST | `/api/v1/campaigns` | JWT (ADMIN) | Create a campaign (DRAFT). |
| POST | `/api/v1/campaigns/{id}/activate` | JWT (ADMIN) | DRAFT/PAUSED -> ACTIVE. |
| POST | `/api/v1/campaigns/{id}/pause` | JWT (ADMIN) | ACTIVE -> PAUSED. |
| DELETE | `/api/v1/campaigns/{id}` | JWT (ADMIN) | Cancel (any non-terminal status -> CANCELLED; no hard delete). |
| GET | `/api/v1/campaigns/{id}` | JWT (ADMIN) | Fetch a campaign. |
| GET | `/api/v1/campaigns` | JWT (ADMIN) | List campaigns (paged). |
| POST | `/internal/campaigns/validate` | none (tokenless, network-perimeter trust) | Eligibility + discount decision for order-creation pricing. |

All responses wrapped in `ApiResult<T>` (ADR-015).

### `POST /internal/campaigns/validate` (Feature 21.3.1)

Tokenless, internal service-to-service only - called by order-service's `CampaignServiceClient`
(`microservices/order-service/.../infrastructure/client/CampaignServiceClient.java`) behind a
fail-open Resilience4j circuit breaker (ADR-027 Decision Section 4). Read-only: never creates or
mutates a `CampaignRedemption` row - reservation happens only via Feature 21.4's `order.created.v1`
consumption.

Request body:

```json
{ "customerId": "<uuid>", "tariffCode": "POSTPAID-001", "campaignCode": "SUMMER25" }
```

`campaignCode` is optional. If omitted, the endpoint auto-resolves the best-matching ACTIVE campaign
whose `applicableTariffCodes` includes `tariffCode`. **Tie-break rule** when more than one ACTIVE
campaign matches the tariff: the candidate with the highest raw `discountValue` wins (a simple,
deterministic rule - not a currency/percentage-normalized comparison; `CampaignRepository
.findByStatusAndApplicableTariffCode` orders candidates `discountValue DESC` and the handler takes the
first). The chosen candidate's validity window and redemption caps are then evaluated exactly as if
its code had been supplied explicitly - ACTIVE + tariff-applicable alone does not guarantee eligibility.

Response (eligible):

```json
{ "eligible": true, "campaignId": "<uuid>", "discountType": "PERCENTAGE", "discountValue": 25.00 }
```

Response (ineligible - always HTTP 200 for a well-formed request, never 4xx/5xx):

```json
{ "eligible": false, "reason": "EXPIRED" }
```

`reason` is one of `CampaignRepository`'s companion `EligibilityReason` enum values:
`CAMPAIGN_NOT_FOUND`, `EXPIRED`, `NOT_YET_ACTIVE`, `NOT_ACTIVE_STATUS`, `TARIFF_NOT_APPLICABLE`,
`PER_CUSTOMER_CAP_EXCEEDED`, `TOTAL_CAP_EXCEEDED`, or `NO_MATCHING_CAMPAIGN` (only returned on the
auto-resolve path, when no ACTIVE campaign matches the given tariff at all).

## Events (Feature 21.4, ADR-009, ADR-019, ADR-027 Decision Section 4)

### Published (campaign lifecycle outbox, Feature 21.4.1)

All published via `OutboxService.publish(...)` atomically with the state transition (never a direct
Kafka producer). Topic: `campaign.events` (Debezium routes on the outbox `aggregate_type` column,
`campaign`). Avro schemas registered under `platform/platform-event-contracts/src/main/avro/`.

| Event | Aggregate | Trigger |
| --- | --- | --- |
| `campaign.created.v1` | `campaign` | `CreateCampaignCommandHandler` (DRAFT created). |
| `campaign.activated.v1` | `campaign` | `ActivateCampaignCommandHandler` (DRAFT/PAUSED -> ACTIVE). |
| `campaign.paused.v1` | `campaign` | `PauseCampaignCommandHandler` (ACTIVE -> PAUSED). |
| `campaign.expired.v1` | `campaign` | `CampaignEligibilityService.evaluate(...)`'s defensive auto-expire branch (ACTIVE -> EXPIRED when `validTo` has passed) - the only real call site of `Campaign.expire()` today; there is no dedicated admin "expire" command. |
| `campaign.cancelled.v1` | `campaign` | `CancelCampaignCommandHandler` (any non-terminal status -> CANCELLED). |

No current consumer for any of the five (registered for future notification/reporting integration,
`docs/architecture/event-catalog.md`).

### Consumed (redemption lifecycle + tariff-defensive, Features 21.4.2/21.4.3)

All consumed via `@KafkaListener`, type-filtered on the `eventType` Kafka header (never on payload
shape alone), and dispatched as a mediator `Command` implementing `IdempotentRequest` so the platform
`InboxBehavior` dedups redelivery atomically inside the handler transaction. Each listener has its own
dedicated consumer group (`campaign-service-<purpose>`) - never shared with another campaign-service
listener on the same topic.

| Event | Topic | Consumer | Effect |
| --- | --- | --- | --- |
| `order.created.v1` | `order.events` | `OrderCreatedRedemptionReservationConsumer` (group `campaign-service-redemption-reservation`) | For each item with a non-null `campaignId`, creates exactly one `RESERVED` `CampaignRedemption` row keyed by `(campaignId, customerId, orderId)`. Delegates to `CampaignEligibilityService.reserve(...)`, whose `PESSIMISTIC_WRITE` lock on `Campaign` makes this race-safe across concurrent `order.created.v1` events for the same campaign (Feature 21.2.2's cap-safety guarantee, now real end to end through the event path). A cap-exceeded/campaign-missing outcome at this stage is logged as a WARN and swallowed, not rethrown (a known, accepted race between the fail-open synchronous validate read and this write). |
| `payment.completed.v1` | `payment.events` | `RedemptionCommitEventConsumer` (group `campaign-service-redemption-commit`) | Looks up `CampaignRedemption` by `orderId`, transitions RESERVED -> CONFIRMED. This is the "order is real" trigger per ADR-027 Section 4's ratification (NOT `order.confirmed.v1`, which is deferred/never produced). An `orderId` with no matching redemption row is a silent no-op. |
| `order.cancelled.v1` | `order.events` | `OrderCancelledEventConsumer` (group `campaign-service-order-cancelled`) | Looks up `CampaignRedemption` by `orderId`, transitions RESERVED -> RELEASED. An `orderId` with no matching redemption row is a silent no-op. |
| `tariff.created.v1` | `tariff.events` | `TariffCreatedEventConsumer` (group `campaign-service-tariff-created`) | **Read-only diagnostic.** If the (re)created tariff code is referenced by an ACTIVE campaign, logs a WARN ("verify this is not an unintended tariff-code reuse"). Never sets the stale-tariff flag (see "Tariff-defensive behavior" below). |
| `tariff.price-changed.v1` | `tariff.events` | `TariffPriceChangedEventConsumer` (group `campaign-service-tariff-price-changed`) | If the repriced tariff code is referenced by an ACTIVE campaign, sets `Campaign.flagStaleTariffReference(reason)` (persisted: `stale_tariff_flag`/`stale_tariff_reason`/`stale_tariff_flagged_at` columns, `V3__campaign_stale_tariff_flag.sql`) and logs a WARN. See "Tariff-defensive behavior" below. |

campaign-service never mirrors tariff pricing data from `tariff.created.v1`/`tariff.price-changed.v1`
(ADR-027 Decision Section 3/4) - it stores only the admin-curated tariff *codes* it already owns
(`applicable_tariff_codes`) and reacts defensively when those codes' referents change upstream.

### Tariff-defensive behavior (chosen implementation, Feature 21.4.3)

ADR-027 leaves the choice between "flag" and "auto-expire" to the implementer. **Chosen: flag, never
auto-expire.** Rationale:

- A tariff price change does not necessarily invalidate a campaign's discount logic (the discount is
  typically a percentage or a fixed absolute reduction, defined independently of the underlying price)
  - auto-expiring a live, revenue-generating campaign on every incidental price change would be an
    overreaction with real business impact, reversible only by a human re-activating it.
- The flag (`stale_tariff_flag`/`stale_tariff_reason`/`stale_tariff_flagged_at`, all present in
  `CampaignResponse`) is admin-visible via `GET /api/v1/campaigns/{id}` / `GET /api/v1/campaigns`
  without requiring any new endpoint, and is idempotent to redeliver (re-flagging just refreshes the
  reason/timestamp) - a human reviews and decides whether to pause/cancel the campaign, keeping the
  actual lifecycle transition an explicit admin action (`PauseCampaignCommandHandler`/
  `CancelCampaignCommandHandler`), not an automated side effect of an unrelated service's event.
- `tariff.created.v1` is treated even more conservatively (log-only, no persisted flag): a brand-new
  tariff being created is normal catalog churn, not itself evidence of a problem, unless its code
  collides with one an ACTIVE campaign already references - in which case a WARN log is a sufficient,
  low-risk early-warning signal without asserting a fact (network of stale reference) the event alone
  cannot fully confirm.

### Reservation-expiry reaper (mandatory, ADR-024, ADR-027 Section 4 ratification)

`CampaignRedemptionReservationExpiryReaper` (`infrastructure/scheduler`), mirroring
subscription-service's `MsisdnReservationExpiryReaper` (Sprint 17 Feature 17.3) exactly: a
`@Scheduled` sweep (default every 60s, `telco.campaign.redemption-reaper.interval-ms`) releases every
`RESERVED` `CampaignRedemption` whose `reserved_until` has elapsed, guarded by an explicit-lease
`DistributedLock` (`starter-lock`, key `campaign-service:redemption-reaper`) so exactly one replica
performs a given tick's sweep once campaign-service scales out. Closes the gap where an abandoned
order (no follow-up `payment.completed.v1`/`order.cancelled.v1`) would otherwise hold a cap slot
indefinitely.

## Notes

- The synchronous validation call is a pure read - it decides eligibility and a discount and writes
  nothing; a redemption is not counted until `order.created.v1` reserves it and `payment.completed.v1`
  confirms it (Feature 21.4).

Reference: [service-catalog](../architecture/service-catalog.md), [event-catalog](../architecture/event-catalog.md), ADR-015, ADR-024, ADR-027.
