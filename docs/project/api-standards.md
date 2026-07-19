# API Standards

Authority: [ADR-015: API Design Standards](../adr/ADR-015-api-design-standards.md). Per-service
request/response shapes live under [API Contracts](../api-contracts/README.md).

## Versioning

Every external REST API is mounted under `/api/v1`. `/internal/**` endpoints (synchronous,
service-to-service, tokenless-at-the-network-perimeter calls such as campaign-service's
eligibility check) are explicitly **not** versioned this way and are never gateway-routed.

## The response envelope

Every external response is wrapped in `ApiResult<T>` (`com.telco.platform.common.api.ApiResult`,
from `starter-api` - never hand-rolled per service):

```json
{
  "success": true,
  "data": { "...": "..." },
  "error": null,
  "meta": {
    "traceId": "...",
    "correlationId": "...",
    "timestamp": "...",
    "service": "customer-service",
    "path": "/api/v1/customers/123"
  }
}
```

On failure, `data` is `null` and `error` carries a stable `code`, a human-readable `message`, the
same `traceId`, and a server-side `logId` that correlates to the full detail in the logs - never
a stack trace or raw exception message. Domain models are never returned directly; every response
is a DTO.

## Pagination

Two supported models, chosen per endpoint based on the data shape:

| Model | Type | Use for |
| --- | --- | --- |
| Offset-based | `PageResult<T>` | Default; small to medium, typically admin-facing datasets |
| Cursor-based | `CursorPage<T>` | Large or streaming reads where offset pagination would degrade (e.g. usage history) |

Cursors are opaque, stable, and backed by deterministic ordering - never a raw offset or a
client-visible database identifier.

## Errors

Every thrown exception is one of the platform's typed `PlatformException` subtypes
(`ResourceNotFoundException`, `ValidationException`, `ConflictException`, `BusinessRuleException`,
`AccessDeniedException`, `UnauthenticatedException`, `DependencyFailureException`), each mapping
to a fixed HTTP status via the shared `GlobalExceptionHandler`. Services extend the platform
`ErrorCode` enum with their own domain-specific codes rather than inventing a new mechanism.

## Idempotency

Endpoints that must not double-execute on retry (payments, in particular) require an
`Idempotency-Key` header. This is currently implemented per service; a shared
`starter-api`/`starter-inbox` mechanism is a tracked platform gap - see
[Platform & Reuse-Before-Build](platform-and-reuse.md#planned-not-yet-available).

## Where the real contracts live

This page is the shape of the contract, not the content. For what each service actually exposes -
every route, request/response body, and status code - see [API Contracts](../api-contracts/README.md)
and the per-service pages under it.
