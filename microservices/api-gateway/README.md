# api-gateway

| Field | Value |
| --- | --- |
| Port | 8080 |
| Architecture Mode (ADR-004) | N/A (edge) |
| Infrastructure Profile (ADR-006) | Redis (rate limiting) |
| Status | Skeleton (no business code) |

Spring Cloud Gateway - the single secured entry point (ADR-011, ADR-015). Validates the
Keycloak-issued JWT (JWKS), propagates `X-User-Id`/`X-User-Roles`, injects `X-Correlation-Id`, and
applies Redis-backed rate limiting. See [api-gateway contract](../../docs/api-contracts/api-gateway.md)
and [keycloak-and-auth](../../docs/architecture/keycloak-and-auth.md).

Routes, filters, and rate-limit config are served centrally from `api-gateway.yml`; this module keeps
only the minimal `spring.config.import` bootstrap. Routing/validation logic is added during Sprint 04.

## Internal surface is never routed (ADR-011)

`/internal/**` is a **service-to-service-only** surface. Example: order-service exposes
`GET /internal/orders/{orderId}` for the onboarding saga (Sprint 09 Feature 9.4) with **no JWT and no
ownership guard** - a trusted system read whose entire security rests on the network boundary
(gateway-behind-trust, ADR-011).

The gateway therefore **MUST NEVER route `/internal/**` to any downstream service.** This is enforced
two ways:

1. Every domain route is narrowly scoped to `/api/v1/**` (plus `/api-docs/**`, `/realms/**`). There is
   no catch-all or host-based route, so `/internal/**` is already unrouted and 404s by default.
2. Defense in depth: the highest-priority `internal-deny-route` in `api-gateway.yml` matches
   `/internal/**` and forwards to a local 404 sink (`forward:/__gateway_blocked`, served by
   `GatewayRouteConfig#internalDenyRouterFunction`) - never to a `lb://` downstream. Even a future
   broad or mistaken route change cannot accidentally expose `/internal/**` through the gateway.

Net effect: `/internal/**` cannot be reached through the gateway from outside the cluster. Services
call each other's `/internal/**` endpoints directly via service discovery, inside the trust boundary.

## Run (dev)

```bash
SPRING_PROFILES_ACTIVE=dev mvn -pl api-gateway spring-boot:run
```
