# payment-service - API Contract

| Field | Value |
| --- | --- |
| Port | 9008 |
| Mode | Domain Orchestration |
| Base path | `/api/v1` |
| Owning sprint | [Sprint 08](../tasks/sprint-08-order-and-payment/README.md) |
| Build status | TODO |
| Requirements | FR-25, FR-26, FR-27 |

Bounded context: payment with PSP integration (mock in MVP). Audit and idempotency mandatory.

## Authentication and Authorization

All endpoints require a valid JWT. Refund requires an operations role.

## Endpoints

| Method | Path | Auth | Idempotency | Summary |
| --- | --- | --- | --- | --- |
| POST | `/api/v1/payments` | JWT | **mandatory** | Charge via mock PSP; idempotent by key. |
| GET | `/api/v1/payments/{id}` | JWT | - | Fetch a payment and its attempts. |
| POST | `/api/v1/payments/{id}/refund` | RBAC ops | **mandatory** | Idempotent refund (compensation). |

## Events

| Direction | Event |
| --- | --- |
| Publish | `payment.completed.v1`, `payment.failed.v1`, `payment.refunded.v1` |
| Consume | `order.created.v1` |

## Notes

- `Idempotency-Key` mandatory on charge and refund; duplicate keys return the original outcome.
- Failed payments retry on a 24/72/168h schedule before final failure.
- Audit rows written for every charge/refund state change.
- `POST /api/v1/payments` accepts an optional `invoiceId` (UUID) alongside the mandatory `orderId`.
  When supplied (customer-initiated "pay this invoice" call, Section 14.2), it is persisted on the
  `Payment` and carried through to `payment.completed.v1`/`payment.failed.v1` so billing-service's
  `PaymentCompletedBillingConsumer` can mark the invoice paid and emit `InvoicePaid`. There is no
  automatic auto-pay consumer of `invoice.generated.v1` in the MVP: Section 14.2 only requires
  "when the customer pays the invoice", which is this customer/admin-initiated charge, not a
  background auto-charge on invoice creation.

### Request body (`POST /api/v1/payments`)

| Field | Type | Required | Notes |
| --- | --- | --- | --- |
| `orderId` | UUID | yes | Order this charge covers. |
| `customerId` | UUID | yes | Paying customer. |
| `amount` | decimal | yes | Must be > 0. |
| `paymentRequestId` | string | yes | Idempotency key (`Idempotency-Key`). |
| `invoiceId` | UUID | no | Invoice this charge settles; omit for a plain order charge. |

Reference: [service-catalog](../architecture/service-catalog.md), [event-catalog](../architecture/event-catalog.md), ADR-011, ADR-015.
