# web-bff - API Contract

| Field | Value |
| --- | --- |
| Role | Backend-for-Frontend for the web channel (ADR-011 Section 5, ADR-022) |
| Base path | `/bff/v1` |
| Owning sprint | [Sprint 16](../tasks/sprint-16-web-frontend/README.md) |
| Build status | TODO (post-MVP) |

The web-bff composes domain APIs into UI-shaped responses for the SvelteKit web app. It owns no data
and is not a domain service; it relays the user's Keycloak token to the gateway and enforces the
channel security boundary (the browser never calls a domain service directly).

## Authentication and Authorization

- The browser obtains a token from Keycloak via Authorization Code + PKCE (`telco-web` client).
- The BFF forwards the bearer token to the gateway, which validates it (JWKS) and propagates identity
  headers. The BFF performs no token issuance.

## Endpoints (composition examples)

| Method | Path | Auth | Summary |
| --- | --- | --- | --- |
| GET | `/bff/v1/home` | JWT | Dashboard: profile + active subscriptions + latest invoice, composed. |
| GET | `/bff/v1/onboarding/catalog` | JWT | Tariffs + addons shaped for the onboarding wizard. |
| POST | `/bff/v1/onboarding/order` | JWT | Orchestrates customer check + order placement for the wizard. |
| GET | `/bff/v1/account` | JWT | Profile, subscriptions, usage summary in one response. |
| GET | `/bff/v1/invoices` | JWT | Invoice list + pre-signed PDF links, composed for the UI. |

## Notes

- The BFF is read-optimized composition; all writes ultimately go to the owning domain service via the
  gateway (e.g. order creation carries the `Idempotency-Key`).
- No domain logic or persistence in the BFF (ADR-022); it is a thin aggregation/transformation layer.
- Responses are UI-shaped; they need not use `ApiResult<T>` internally, but downstream domain calls do.
- `POST /bff/v1/onboarding/order` reuse path: when the request carries a `customerId` the BFF reuses that
  customer (skipping register/KYC) rather than deriving identity from the token, so ownership MUST be
  re-checked by the owning domain service (order-service) against the relayed identity. Unlike the
  `/home`, `/account`, and `/invoices` reads - which are strictly self-scoped from the gateway-forwarded
  identity and bind no client id - this one write accepts a client-supplied id by design for the wizard.

Reference: [api-gateway](api-gateway.md), [keycloak-and-auth.md](../architecture/keycloak-and-auth.md),
ADR-011, ADR-022.
