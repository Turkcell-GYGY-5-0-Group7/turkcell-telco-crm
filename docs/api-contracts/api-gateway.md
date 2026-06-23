# API Gateway - Global Contract

| Field | Value |
| --- | --- |
| Port | 8080 |
| Role | Single secured entry point (ADR-011, ADR-015) |
| Mode | Edge (config-only, no domain logic) |
| Build status | TODO (Sprint 04) |

The gateway is the only externally exposed service. All client traffic enters here; everything
behind it is private and trusts the gateway.

## Responsibilities

- Route `/api/v1/**` to the owning service via discovery (ADR-010).
- Validate the **Keycloak-issued** JWT on every request via the realm JWKS (OAuth2/OIDC, NFR-05,
  ADR-011); reject invalid/expired tokens with 401. The gateway does not issue tokens.
- Propagate identity downstream as `X-User-Id` and `X-User-Roles` (gateway-behind-trust).
- Inject `X-Correlation-Id` if absent and propagate it to every downstream call (NFR-13).
- Enforce Redis-backed rate limiting: 100 req/min per user by default (NFR-18); return 429 on breach.
- Aggregate per-service OpenAPI for a unified Swagger view.

## Routing Table

| Path prefix | Target service | Auth |
| --- | --- | --- |
| `/realms/telco-crm/protocol/openid-connect/**` | Keycloak (token, JWKS, userinfo) | Public (OIDC) |
| `/api/v1/users/**` | identity-service (9001) | JWT + RBAC |
| `/api/v1/customers/**` | customer-service (9002) | JWT |
| `/api/v1/tariffs/**`, `/api/v1/addons/**` | product-catalog-service (9003) | JWT (read), RBAC (write) |
| `/api/v1/orders/**` | order-service (9004) | JWT |
| `/api/v1/subscriptions/**` | subscription-service (9005) | JWT |
| `/api/v1/usage/**` | usage-service (9006) | JWT |
| `/api/v1/invoices/**`, `/api/v1/billing/**` | billing-service (9007) | JWT (read), RBAC (runs) |
| `/api/v1/payments/**` | payment-service (9008) | JWT |
| `/api/v1/notifications/**` | notification-service (9009) | JWT |
| `/api/v1/tickets/**` | ticket-service (9010) | JWT |

## Standard Headers

| Header | Direction | Notes |
| --- | --- | --- |
| `Authorization: Bearer <JWT>` | client -> gateway | Required except on public auth routes. |
| `X-User-Id`, `X-User-Roles` | gateway -> service | Injected by the gateway; clients cannot set them. |
| `X-Correlation-Id` | both | Injected if absent; echoed back; logged everywhere. |
| `Idempotency-Key` | client -> service | Passed through; honored by payment and order POSTs. |

## Error Semantics

All errors are returned as `ApiError` (RFC 7807-aligned) with `traceId`. Gateway-level codes:
401 (invalid/missing token), 403 (insufficient role), 404 (no route), 429 (rate limit), 503
(downstream unavailable / circuit open).

## Infrastructure Services (not externally routed)

| Service | Port | Notes |
| --- | --- | --- |
| discovery-server | 8761 | Eureka in dev; Kubernetes-native in prod (ADR-010). |
| config-server | 8888 | Spring Cloud Config in dev; ConfigMaps/Secrets in prod. |

Token issuance and the login/refresh flow are Keycloak's (ADR-011); see
[keycloak-and-auth.md](../architecture/keycloak-and-auth.md). The gateway only validates and relays.

Reference: [service-catalog](../architecture/service-catalog.md),
[keycloak-and-auth.md](../architecture/keycloak-and-auth.md), ADR-010, ADR-011, ADR-015.
