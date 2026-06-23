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
| POST | `/api/v1/orders/{id}/cancel` | JWT | - | Cancel an order; triggers compensation. |

## Events

| Direction | Event |
| --- | --- |
| Publish | `order.created.v1`, `order.confirmed.v1`, `order.cancelled.v1` |
| Consume | `payment.completed.v1`, `payment.failed.v1`, `subscription.activated.v1` |

## Notes

- `Idempotency-Key` is mandatory on order creation; replays return the original result.
- Order capture validates the customer (ACTIVE/KYC) and snapshots catalog price synchronously.
- Saga state is persisted; activation failure compensates (refund + order CANCELLED).

Reference: [service-catalog](../architecture/service-catalog.md), [event-catalog](../architecture/event-catalog.md), ADR-015.
