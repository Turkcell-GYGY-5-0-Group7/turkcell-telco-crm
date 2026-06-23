# usage-service - API Contract

| Field | Value |
| --- | --- |
| Port | 9006 |
| Mode | CQRS + Mediator |
| Base path | `/api/v1` |
| Owning sprint | [Sprint 10](../tasks/sprint-10-usage-metering/README.md) |
| Build status | TODO |
| Requirements | FR-17, FR-18, FR-19, FR-20 |

Bounded context: usage and quota. Consumes CDR events, decrements quota, emits threshold/overage
events. Write-heavy; reads are near-real-time.

## Authentication and Authorization

All read endpoints require a valid JWT. Ingestion is event-driven (no public write API).

## Endpoints

| Method | Path | Auth | Idempotency | Summary |
| --- | --- | --- | --- | --- |
| GET | `/api/v1/usage/subscriptions/{id}/quota` | JWT | - | Remaining quota (near-real-time). |
| GET | `/api/v1/usage/subscriptions/{id}/history?from=&to=` | JWT | - | Usage history for a window (paged). |

## Events

| Direction | Event |
| --- | --- |
| Consume | `cdr.recorded.v1` (CDR simulator), `subscription.activated.v1` (quota provisioning) |
| Publish | `usage.recorded.v1`, `quota.threshold-reached.v1`, `quota.exceeded.v1`, `usage.aggregated.v1` |

## Notes

- Metering is idempotent by `cdrRef` and concurrency-safe.
- The 80% threshold and 100% exceeded events each fire exactly once per period.
- Post-exhaustion usage is aggregated and forwarded to billing as overage.

Reference: [service-catalog](../architecture/service-catalog.md), [event-catalog](../architecture/event-catalog.md), ADR-015.
