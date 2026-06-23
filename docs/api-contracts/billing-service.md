# billing-service - API Contract

| Field | Value |
| --- | --- |
| Port | 9007 |
| Mode | Domain Orchestration |
| Primary store | PostgreSQL |
| Object storage | MinIO (invoice PDFs) |
| Base path | `/api/v1` |
| Owning sprint | [Sprint 11](../tasks/sprint-11-billing/README.md) |
| Build status | TODO |
| Requirements | FR-21, FR-22, FR-23, FR-24 |

Bounded context: invoice generation and the monthly bill-run. Composes invoice lines, renders PDFs,
reconciles payment.

## Authentication and Authorization

Invoice reads require a valid JWT. Triggering a bill-run requires an admin role.

## Endpoints

| Method | Path | Auth | Idempotency | Summary |
| --- | --- | --- | --- | --- |
| GET | `/api/v1/invoices?customerId=...` | JWT | - | List invoices for a customer (paged). |
| GET | `/api/v1/invoices/{id}` | JWT | - | Fetch an invoice with lines. |
| GET | `/api/v1/invoices/{id}/pdf` | JWT | - | Download the rendered PDF. |
| POST | `/api/v1/billing/runs` | RBAC admin | optional | Trigger a bill-run for a period. |

## Events

| Direction | Event |
| --- | --- |
| Publish | `invoice.generated.v1`, `invoice.paid.v1`, `invoice.overdue.v1` |
| Consume | `usage.aggregated.v1`, `subscription.activated.v1`, `payment.completed.v1` |

## Notes

- The bill-run is idempotent per (subscriber, period) and meets the NFR-02 throughput target.
- Invoice lines compose monthly fee, addons, overage, VAS, and taxes.
- Rendered invoice PDFs are stored in MinIO; `GET /invoices/{id}/pdf` returns a time-limited
  pre-signed URL (or streams via the service). The invoice row holds only the object reference
  (ADR-006); PDFs are never stored in the database.

Reference: [service-catalog](../architecture/service-catalog.md), [event-catalog](../architecture/event-catalog.md), ADR-015.
