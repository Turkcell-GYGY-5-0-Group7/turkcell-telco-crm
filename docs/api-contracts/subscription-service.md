# subscription-service - API Contract

| Field | Value |
| --- | --- |
| Port | 9005 |
| Mode | CQRS + Mediator |
| Base path | `/api/v1` |
| Owning sprint | [Sprint 09](../tasks/sprint-09-subscription-and-onboarding-saga/README.md) |
| Build status | TODO |
| Requirements | FR-13, FR-14, FR-15 (FR-16 MNP post-MVP) |

Bounded context: subscription lifecycle state machine and atomic MSISDN allocation/release.
Audit mandatory.

## Authentication and Authorization

Read and lifecycle endpoints require a valid JWT. Activation is internal (saga-driven).

## Endpoints

| Method | Path | Auth | Idempotency | Summary |
| --- | --- | --- | --- | --- |
| POST | `/api/v1/subscriptions` | Internal (saga) | mandatory | Activate a subscription; allocate MSISDN. |
| GET | `/api/v1/subscriptions/{id}` | JWT | - | Fetch a subscription. |
| GET | `/api/v1/subscriptions` | JWT | - | List a customer's subscriptions (paged). |
| POST | `/api/v1/subscriptions/{id}/suspend` | JWT | - | Suspend (e.g. non-payment). |
| POST | `/api/v1/subscriptions/{id}/reactivate` | JWT | - | Reactivate a suspended subscription. |
| POST | `/api/v1/subscriptions/{id}/terminate` | JWT | - | Terminate; release MSISDN. |

## Events

| Direction | Event |
| --- | --- |
| Publish | `subscription.activated.v1`, `subscription.suspended.v1`, `subscription.terminated.v1`, `msisdn.allocated.v1`, `msisdn.released.v1` |
| Consume | `order.confirmed.v1`, `payment.completed.v1`, `payment.failed.v1` (after grace period) |

## Notes

- MSISDN allocation is atomic and concurrency-safe; no MSISDN is double-allocated.
- Lifecycle transitions are enforced as domain invariants; illegal transitions are rejected.

Reference: [service-catalog](../architecture/service-catalog.md), [event-catalog](../architecture/event-catalog.md), ADR-015.
