# API Contracts

Per-service external API contracts for the Telco CRM Platform. These are design contracts derived
from `docs/architecture/service-catalog.md`, `docs/architecture/event-catalog.md`, and
`docs/product/requirements.md`, governed by **ADR-015 (API design standards)**. They describe the
intended public surface; implementation status tracks `docs/tasks/STATUS.md`.

## Index

| Contract | Port | Mode (ADR-004) | Build status |
| --- | --- | --- | --- |
| [api-gateway](api-gateway.md) (global edge + routing) | 8080 | Edge | TODO (Sprint 04) |
| [identity-service](identity-service.md) | 9001 | CQRS + Mediator | TODO (Sprint 05) |
| [customer-service](customer-service.md) | 9002 | CQRS + Mediator | TODO (Sprint 06) |
| [product-catalog-service](product-catalog-service.md) | 9003 | CQRS + Mediator | TODO (Sprint 07) |
| [order-service](order-service.md) | 9004 | Domain Orchestration | TODO (Sprint 08) |
| [payment-service](payment-service.md) | 9008 | Domain Orchestration | TODO (Sprint 08) |
| [subscription-service](subscription-service.md) | 9005 | CQRS + Mediator | TODO (Sprint 09) |
| [usage-service](usage-service.md) | 9006 | CQRS + Mediator | TODO (Sprint 10) |
| [billing-service](billing-service.md) | 9007 | Domain Orchestration | TODO (Sprint 11) |
| [notification-service](notification-service.md) | 9009 | Simple Service Layer | TODO (Sprint 12) |
| [ticket-service](ticket-service.md) | 9010 | CQRS + Mediator | TODO (Sprint 12) |
| [web-bff](web-bff.md) (web channel composition) | - | BFF (post-MVP) | TODO (Sprint 16) |
| [campaign-service](campaign-service.md) | 9011 | CQRS + Mediator | TODO (Sprint 21) |

Per-service infrastructure (primary store, cache, search, object storage) is declared in the
[service-catalog Infrastructure Profile](../architecture/service-catalog.md) (ADR-006). Notable
non-defaults: notification-service uses MongoDB; customer-service and billing-service use MinIO for
binary artifacts.

## Conventions (ADR-015 - apply to every contract)

- External REST under `/api/v1`; plural resource names (`customers`, `orders`).
- All responses wrapped in `ApiResult<T>`; errors use `ApiError` (RFC 7807-aligned: code, message,
  details, traceId).
- Pagination: offset (`?page=0&size=20&sort=createdAt,desc`) returns `PageResult<T>`; cursor
  (`CursorPage<T>`) for high-volume reads.
- `Idempotency-Key` header on POST commands (mandatory for payment-service and order-service).
- `X-Correlation-Id` injected by the gateway and logged by every service.
- Auth: clients send `Authorization: Bearer <JWT>` to the gateway. The gateway validates the token
  and forwards `X-User-Id` / `X-User-Roles` downstream; services trust the gateway.
- Dates ISO-8601 UTC. Money as `BigDecimal` with a separate currency code (TRY).
- Each service also exposes its own Springdoc OpenAPI/Swagger UI at `/swagger-ui` (ARC-08); these
  files are the human-readable summary, the OpenAPI doc is the generated detail.

## Contract template

Each service file documents: identity (port, mode, base path, owning sprint), authentication and
authorization, endpoints (method, path, auth, idempotency, summary), events produced and consumed,
and error/idempotency notes. Keep contracts in sync with the service catalog and event catalog.
