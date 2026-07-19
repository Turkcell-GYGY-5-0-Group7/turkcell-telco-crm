# order-service - API Contract

| Field | Value |
| --- | --- |
| Port | 9004 |
| Mode | Domain Orchestration (saga) |
| Base path | `/api/v1` |
| Owning sprint | [Sprint 08](../tasks/sprint-08-order-and-payment/README.md) (built), [Sprint 09](../tasks/sprint-09-subscription-and-onboarding-saga/README.md) (saga) |
| Build status | TODO |
| Requirements | FR-09, FR-10, FR-11, FR-12 |

Bounded context: order orchestration. Coordinates customer -> catalog -> payment -> subscription
via the onboarding saga with compensation.

## Authentication and Authorization

All endpoints require a valid JWT.

## Endpoints

| Method | Path | Auth | Idempotency | Summary |
| --- | --- | --- | --- | --- |
| POST | `/api/v1/orders` | JWT | **mandatory** | Place an order (PENDING_PAYMENT); validates customer + price snapshot. |
| GET | `/api/v1/orders/{id}` | JWT | - | Fetch an order with saga/status. |
| GET | `/api/v1/orders` | JWT | - | List a customer's orders (paged). |
| DELETE | `/api/v1/orders/{id}` | JWT | - | Cancel an order (soft delete/status transition, not a physical delete); triggers compensation. |

## Events

| Direction | Event |
| --- | --- |
| Publish | `order.created.v1`, `order.confirmed.v1`, `order.cancelled.v1` |
| Consume | `payment.completed.v1`, `payment.failed.v1`, `subscription.activated.v1` |

## Notes

- `Idempotency-Key` is mandatory on order creation; replays return the original result.
- Order list pagination: `page` (default 0), `size` (default 20) and optional `sort=field,asc|desc`
  (direction optional, `desc` assumed; default `createdAt,desc`). Sortable fields: `createdAt`,
  `totalAmount`, `status`. Any other field or a malformed value returns the standard 400 validation
  error shape.
- Order capture validates the customer (ACTIVE/KYC) and snapshots catalog price synchronously.
- Saga state is persisted; activation failure compensates (refund + order CANCELLED).
- **Optional campaign discount at order capture (Sprint 21 Feature 21.3.3, ADR-027 Decision Section
  4):** after the existing tariff price-snapshot call, order-service asks campaign-service (tokenless,
  `POST /internal/campaigns/validate` on `CampaignServiceClient`, behind a **fail-open** Resilience4j
  circuit breaker) whether a campaign discount applies to each line item. Each item in
  `POST /api/v1/orders`'s request body may optionally carry a `campaignCode`; when omitted,
  campaign-service auto-resolves the best-matching ACTIVE campaign for the item's tariff. When
  eligible, the `OrderItem`'s `unitPrice` is discounted (`PERCENTAGE`:
  `monthlyFee * (1 - discountValue/100)`; `FIXED_AMOUNT`: `monthlyFee - discountValue`, both floored at
  zero) and the nullable `campaignId`/`campaignCode` snapshot columns on `order_items` are populated
  (`campaignId` always when a discount applied; `campaignCode` only when the caller explicitly
  requested that campaign - matching the `tariff_id`/`tariff_code`/`tariff_version` snapshot symmetry).
  A campaign-service outage, an OPEN circuit breaker, or a genuinely ineligible decision all leave the
  item priced at today's undiscounted `monthlyFee` - a campaign outage never blocks order creation.
  `OrderCreatedEvent.OrderItemPayload` carries a matching nullable `campaignId` field for Feature 21.4's
  redemption-confirmation flow.

## Order kinds (Sprint 24 Feature 24.2, design-note D1/D2)

`POST /api/v1/orders` accepts three order kinds through one generalized item model. Each request
item may carry, in addition to the pre-24.2 `tariffId`/`quantity`/`campaignCode`:

- `itemType` (optional, `TARIFF` | `ADDON`, defaults to `TARIFF` when omitted - every pre-24.2
  request body keeps working unchanged),
- `productCode` (catalog addon code; required on `ADDON` items),
- `targetSubscriptionId` (an existing subscription; see the matrix).

The order kind is **derived from the items** (never sent by the caller) and persisted plus exposed
as `orderType` on all order responses: every item `ADDON` -> `ADDON`; exactly one `TARIFF` item
carrying a `targetSubscriptionId` -> `PLAN_CHANGE`; anything else -> `NEW_LINE`.

Validation matrix (400 `VALIDATION_FAILED` for malformed shapes, 422 `BUSINESS_RULE_VIOLATION` for
domain-rule violations):

| Kind | Items | Rules |
| --- | --- | --- |
| `NEW_LINE` | exactly 1 `TARIFF` (+ 0..N bundled `ADDON`) | `tariffId` required on the tariff item; `productCode` required on addon items; `targetSubscriptionId` forbidden on every item. |
| `ADDON` | 1..N `ADDON` | all items carry the SAME non-null `targetSubscriptionId`; the target subscription must exist (404 otherwise), be ACTIVE and belong to the ordering customer (fail-closed hop to subscription-service `GET /internal/subscriptions/{id}`). |
| `PLAN_CHANGE` | exactly 1 `TARIFF` | `tariffId` AND `targetSubscriptionId` required; target subscription ACTIVE, owned, and its current tariff must differ from the requested one. The order charges the new tariff's monthly fee through the unchanged payment saga (design-note D2). |
| any | - | `campaignCode` on a non-`TARIFF` item is a validation error (campaign eligibility is tariff-scoped, ADR-027). |

`ADDON` items are priced fail-closed from product-catalog's
`GET /internal/addons/{code}/snapshot` (Feature 24.1): the addon's price becomes the item
`unitPrice` and its name/allowances are snapshotted onto the item
(`allowance_data_mb`/`allowance_minutes`/`allowance_sms` columns), so downstream consumers never
need a runtime catalog hop. Item responses expose `itemType`, `productCode` and
`targetSubscriptionId` alongside the existing tariff/campaign snapshot fields (the tariff snapshot
fields are `null` on `ADDON` items; `tariffName` doubles as the generic product-name snapshot).
`order.created.v1` carries matching additive item fields `itemType` (default `TARIFF`),
`productCode` and `targetSubscriptionId` (see event-catalog Section 5).

Reference: [service-catalog](../architecture/service-catalog.md), [event-catalog](../architecture/event-catalog.md), ADR-015, ADR-027.
