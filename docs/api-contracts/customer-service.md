# customer-service - API Contract

| Field | Value |
| --- | --- |
| Port | 9002 |
| Mode | CQRS + Mediator |
| Primary store | PostgreSQL |
| Object storage | MinIO (KYC documents) |
| Base path | `/api/v1` |
| Owning sprint | [Sprint 06](../tasks/sprint-06-customer-domain/README.md) |
| Build status | DONE |
| Requirements | FR-01, FR-02, FR-03, FR-04 |

Bounded context: customer master record. Registration with TCKN/VKN validation, KYC workflow,
address and document management, soft-delete (KVKK/GDPR), PII encryption at rest. Audit mandatory.

## Authentication and Authorization

All endpoints require a valid JWT. KYC approval/rejection requires an operations role.

## Endpoints

| Method | Path | Auth | Idempotency | Summary |
| --- | --- | --- | --- | --- |
| POST | `/api/v1/customers` | JWT | optional | Register a customer (PENDING); validates TCKN (INDIVIDUAL) or VKN (CORPORATE) by `type`. |
| GET | `/api/v1/customers/{id}` | JWT | - | Fetch a customer (PII masked). |
| GET | `/api/v1/customers` | JWT | - | List/search customers (paged). |
| PUT | `/api/v1/customers/{id}` | JWT | - | Update profile/contact data. |
| DELETE | `/api/v1/customers/{id}` | JWT | - | Soft-delete (sets `deleted_at`). |
| POST | `/api/v1/customers/{id}/documents` | JWT | - | Upload a KYC document. |
| POST | `/api/v1/customers/{id}/kyc/approve` | RBAC ops | - | Approve KYC (-> ACTIVE). |
| POST | `/api/v1/customers/{id}/kyc/reject` | RBAC ops | - | Reject KYC (-> REJECTED). |

## Request/Response Fields (Sprint 24 feature 24.5)

Register (`POST /api/v1/customers`) request:

| Field | Type | Required | Validation |
| --- | --- | --- | --- |
| `type` | enum | yes | `INDIVIDUAL` or `CORPORATE`. |
| `firstName` | string | yes | not blank. |
| `lastName` | string | yes | not blank. |
| `identityNumber` | string | yes | class-level `@ValidIdentityForType`: TCKN checksum for INDIVIDUAL, VKN checksum for CORPORATE; violation reported on `identityNumber`. Masked in logs. |
| `dateOfBirth` | date | no | must be in the past. |
| `email` | string | no | RFC email, max 255. Masked in logs (`@Sensitive`). |
| `phone` | string | no | `^\+?[0-9]{7,15}$`, max 32. Masked in logs (`@Sensitive`). |

Update (`PUT /api/v1/customers/{id}`) request: `firstName`, `lastName`, `dateOfBirth`, `email`,
`phone` with the same rules (identity number is immutable). The update is a full profile
replacement: omitting `email`/`phone` clears them.

`CustomerResponse`: `id`, `type`, `firstName`, `lastName`, `identityNumberMasked` (only masked form
ever returned), `dateOfBirth`, `email`, `phone`, `status`, `createdAt`. Email/phone are returned
plainly (stored plain per design-note D5; log masking only).

## Events

| Direction | Event |
| --- | --- |
| Publish | `customer.registered.v1`, `customer.kyc-approved.v1`, `customer.kyc-rejected.v1`, `customer.updated.v1` |

## Notes

- Identity number (TCKN/VKN) encrypted with AES-GCM at rest; returned only masked; never logged.
- Contact info (`email`/`phone`, FR-03) is stored plain (no encryption mandate, design-note D5) but
  masked in logs/telemetry via the platform `@Sensitive` annotation (ADR-021).
- KYC state machine PENDING -> ACTIVE / REJECTED; illegal transitions rejected with a business error.
- Soft-deleted customers are excluded from default reads but the row persists.
- Customer list pagination: `page` (default 0), `size` (default 20) and optional
  `sort=field,asc|desc` (direction optional, `desc` assumed; default `createdAt,desc`). Sortable
  fields: `createdAt`, `firstName`, `lastName`, `status`, `type`. Any other field or a malformed
  value returns the standard 400 validation error shape.
- KYC document binaries are stored in MinIO; the `Document` row holds only the object reference
  (bucket, key, content-type, checksum), and downloads are served via time-limited pre-signed URLs
  (ADR-006). The raw bytes are never stored in the database.

Reference: [service-catalog](../architecture/service-catalog.md), ADR-011, ADR-021, ADR-015.
