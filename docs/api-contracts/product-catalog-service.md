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
| GET | `/api/v1/addons?tariffCode=...` | JWT | - | List addons (paged); optional filter to addons linked to a tariff. |
| POST | `/api/v1/addons` | RBAC admin | optional | Create an addon, optionally linked to tariffs by code. |
| GET | `/internal/addons/{code}/snapshot` | internal (no JWT) | - | Addon snapshot for order-service pricing. Network-perimeter trust like the tariff internal routes: the gateway blocks `/internal/**` (internal-deny-route), reachable in-network only. |

## Addons (Sprint 24, feature 24.1)

Addons carry nullable, type-dependent allowance fields: `dataMb` (DATA), `voiceMinutes`
(MINUTES), `smsCount` (SMS); VAS addons carry none. All addon responses include these fields.

`POST /api/v1/addons` request body:

```json
{
  "code": "DATA_5GB",
  "name": "Data 5 GB",
  "price": 49.90,
  "currency": "TRY",
  "type": "DATA",
  "validityDays": 30,
  "dataMb": 5120,
  "voiceMinutes": null,
  "smsCount": null,
  "applicableTariffCodes": ["POSTPAID-BASIC"]
}
```

Constraints: `code` <= 50 chars, `name` <= 200, `price` >= 0.00, `currency` 3-letter uppercase,
`type` in DATA | SMS | MINUTES | VAS, `validityDays` > 0, allowances >= 0 when present.
`applicableTariffCodes` is optional; each code must reference an existing tariff (unknown code
-> 422), and a duplicate addon `code` -> 422. Returns `201` with the created addon wrapped in
`ApiResult`:

```json
{
  "success": true,
  "data": {
    "id": "…",
    "code": "DATA_5GB",
    "name": "Data 5 GB",
    "price": 49.90,
    "currency": "TRY",
    "type": "DATA",
    "validityDays": 30,
    "dataMb": 5120,
    "voiceMinutes": null,
    "smsCount": null,
    "status": "ACTIVE",
    "createdAt": "…"
  }
}
```

`GET /internal/addons/{code}/snapshot` (internal) returns `ApiResult` of
`{id, code, name, type, price, currency, validityDays, dataMb, voiceMinutes, smsCount}`;
404 when the addon is absent or not ACTIVE.

A standard addon catalog (DATA_5GB, DATA_10GB, SMS_500, VOICE_300, VAS_CALLERTUNE) is seeded by
migration `V2__addon_management.sql`. Tariff links are runtime-managed (no tariffs are seeded),
so seeded addons appear under `?tariffCode=` filters only after being linked via
`POST /api/v1/addons` or future admin tooling.

## Events

| Direction | Event |
| --- | --- |
| Publish | `tariff.created.v1`, `tariff.price-changed.v1`, `tariff.changed.v1` |

## Notes

- A price change creates a new version; prior versions are preserved for existing subscribers.
- Reads are cache-aside (Redis); writes invalidate the affected cache keys.
- Tariff list pagination: `page` (default 0), `size` (default 20) and optional
  `sort=field,asc|desc` (direction optional, `desc` assumed; default `createdAt,desc`). Sortable
  fields: `createdAt`, `name`, `monthlyFee`, `effectiveFrom`. Any other field or a malformed value
  returns the standard 400 validation error shape.

Reference: [service-catalog](../architecture/service-catalog.md), ADR-015.
