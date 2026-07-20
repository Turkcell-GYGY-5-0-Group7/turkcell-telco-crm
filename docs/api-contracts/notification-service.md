# notification-service - API Contract

| Field | Value |
| --- | --- |
| Port | 9009 |
| Mode | Simple Service Layer |
| Primary store | MongoDB (approved exception, ADR-006) + PostgreSQL outbox |
| Base path | `/api/v1` |
| Owning sprint | [Sprint 12](../tasks/sprint-12-notifications-and-ticketing/README.md) |
| Build status | TODO |
| Requirements | FR-28, FR-29, FR-30 |

Bounded context: multi-channel notification dispatch (mock SMS/email/push). Consumes most domain
events and renders templates while respecting user preferences.

## Authentication and Authorization

History reads require a valid JWT. Direct send is internal (event-driven in normal operation).

## Endpoints

| Method | Path | Auth | Idempotency | Summary |
| --- | --- | --- | --- | --- |
| POST | `/api/v1/notifications` | Internal | optional | Send a templated notification (internal callers). |
| GET | `/api/v1/notifications/users/{id}/history` | JWT | - | Notification history for a user (paged). |
| GET | `/api/v1/notifications/templates` | RBAC admin | - | List templates. |
| PUT | `/api/v1/notifications/users/{id}/preferences` | JWT | - | Update channel preferences. |

## Events

| Direction | Event |
| --- | --- |
| Consume | most domain events (template-mapped): `customer.*`, `payment.*`, `subscription.*`, `invoice.*`, `quota.*`, `ticket.*`, `fraud.case-opened.v1` (internal ops/security alert, Sprint 23 Feature 23.4.3) |
| Publish | `notification.dispatched.v1` |

## Notes

- Dispatch respects per-user channel preferences; suppressed channels are not sent.
- History pagination: `page` (default 0), `size` (default 20) and optional `sort=field,asc|desc`
  (direction optional, `desc` assumed; default `createdAt,desc`). Sortable fields: `createdAt`,
  `sentAt`, `status`, `channel`. Any other field or a malformed value returns the standard 400
  validation error shape.
- `fraud.case-opened.v1` (fraud-service, ADR-029 Section 5) raises one internal ops/security alert on
  the `OPS_ALERT` channel (distinct from customer-facing SMS/email/push, keyed to the `security-ops`
  responder queue) via the `FRAUD_CASE_OPENED` template. Informational only: it triggers no
  subscription-service call and no automated suspension.
- Closes the messaging loops for AC-01 (welcome SMS), AC-02 (invoice email), AC-03 (quota SMS).
- Persistence: notification documents and history live in MongoDB; the lone event
  `notification.dispatched.v1` is written via a co-located PostgreSQL outbox (non-atomic across the
  two stores, acceptable for an idempotent non-financial event). See ADR-006 and the
  [service-catalog Infrastructure Profile](../architecture/service-catalog.md).

Reference: [service-catalog](../architecture/service-catalog.md), [event-catalog](../architecture/event-catalog.md), ADR-015.
