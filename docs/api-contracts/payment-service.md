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

- Idempotency on charge (PDF Section 12): the `Idempotency-Key` HTTP header is the standard
  mechanism. When the header is present and non-blank it WINS as the `paymentRequestId`; the body
  `paymentRequestId` field stays supported for back-compat and is used only when the header is
  absent/blank. At least one source is required, otherwise 400 `VALIDATION_FAILED`. Duplicate keys
  return the original outcome (single payment).
- Failed payments retry on a 24/72/168h schedule before final failure.
- Audit rows written for every charge/refund state change.
- `POST /api/v1/payments` accepts an optional `invoiceId` (UUID) alongside the mandatory `orderId`.
  When supplied (customer-initiated "pay this invoice" call, Section 14.2), it is persisted on the
  `Payment` and carried through to `payment.completed.v1`/`payment.failed.v1` so billing-service's
  `PaymentCompletedBillingConsumer` can mark the invoice paid and emit `InvoicePaid`. There is no
  automatic auto-pay consumer of `invoice.generated.v1` in the MVP: Section 14.2 only requires
  "when the customer pays the invoice", which is this customer/admin-initiated charge, not a
  background auto-charge on invoice creation.

### Request headers (`POST /api/v1/payments`)

| Header | Required | Notes |
| --- | --- | --- |
| `Idempotency-Key` | no* | Standard idempotency key (PDF Section 12). Wins over the body `paymentRequestId` when present. *Required unless the body supplies `paymentRequestId`. |

### Request body (`POST /api/v1/payments`)

| Field | Type | Required | Notes |
| --- | --- | --- | --- |
| `orderId` | UUID | yes | Order this charge covers. |
| `customerId` | UUID | yes | Paying customer. |
| `amount` | decimal | yes | Must be > 0. |
| `paymentRequestId` | string | no* | Legacy idempotency key (back-compat). Ignored when the `Idempotency-Key` header is present. *Required when the header is absent. |
| `invoiceId` | UUID | no | Invoice this charge settles; omit for a plain order charge. |
| `method` | enum | no | `CREDIT_CARD` (default) \| `BANK_TRANSFER` \| `WALLET` (FR-25). Label only: the mock PSP ignores the method; wallet balance modeling is out of scope (Sprint 24 design-note D6). Echoed as `method` on the payment response. |

Reference: [service-catalog](../architecture/service-catalog.md), [event-catalog](../architecture/event-catalog.md), ADR-011, ADR-015.
