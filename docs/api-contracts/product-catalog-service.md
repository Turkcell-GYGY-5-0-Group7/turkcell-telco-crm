# product-catalog-service - API Contract

| Field | Value |
| --- | --- |
| Port | 9003 |
| Mode | CQRS + Mediator |
| Primary store | PostgreSQL + Redis (cache-aside); MongoDB read-projection post-MVP |
| Base path | `/api/v1` |
| Owning sprint | [Sprint 07](../tasks/sprint-07-product-catalog-domain/README.md) |
| Build status | TODO |
| Requirements | FR-05, FR-06, FR-07, FR-08 |

Bounded context: tariffs, addons, VAS. Read-heavy with Redis cache-aside. Versioned tariff
changes preserve existing subscribers' tariff.

## Authentication and Authorization

Reads require a valid JWT. Authoring (create/update/version) requires an admin role.

## Endpoints

| Method | Path | Auth | Idempotency | Summary |
| --- | --- | --- | --- | --- |
| GET | `/api/v1/tariffs` | JWT | - | List tariffs (paged, cache-served). |
| GET | `/api/v1/tariffs/{code}` | JWT | - | Fetch a tariff by its code (current version). |
| GET | `/api/v1/tariffs/by-id/{id}` | JWT | - | Fetch a tariff by its UUID primary key. Used by cross-service callers (e.g. order-service) that hold the tariff's id rather than its code. |
| GET | `/api/v1/tariffs/{code}/price` | JWT | - | Price snapshot for order capture. |
| POST | `/api/v1/tariffs` | RBAC admin | optional | Create a tariff. |
| PUT | `/api/v1/tariffs/{code}` | RBAC admin | - | New version on price/attribute change. |
| GET | `/api/v1/addons?tariffCode=...` | JWT | - | List addons for a tariff. |
| POST | `/api/v1/addons` | RBAC admin | optional | Create an addon. |

## Events

| Direction | Event |
| --- | --- |
| Publish | `tariff.created.v1`, `tariff.price-changed.v1`, `tariff.changed.v1` |

## Notes

- A price change creates a new version; prior versions are preserved for existing subscribers.
- Reads are cache-aside (Redis); writes invalidate the affected cache keys.

Reference: [service-catalog](../architecture/service-catalog.md), ADR-015.
