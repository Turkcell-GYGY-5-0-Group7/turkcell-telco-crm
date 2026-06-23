# identity-service - API Contract

| Field | Value |
| --- | --- |
| Port | 9001 |
| Mode | CQRS + Mediator |
| Base path | `/api/v1` |
| Owning sprint | [Sprint 05](../tasks/sprint-05-security-and-identity/README.md) |
| Build status | TODO |
| Requirements | FR-IAM-01, FR-IAM-04, FR-IAM-05 |

Bounded context: identity and authorization. Manages users, roles, and permissions and owns
app-specific authorization data and audit. **Token issuance is Keycloak's job (ADR-011), not this
service's** - identity-service does not mint or refresh JWTs.

## Authentication and Authorization

- **Login, token, and refresh are served by Keycloak's realm endpoint**, not by identity-service:
  `POST {keycloak}/realms/telco-crm/protocol/openid-connect/token` (Authorization Code + PKCE for the
  web client; password grant for local testing). See [keycloak-and-auth.md](../architecture/keycloak-and-auth.md).
- The gateway validates the Keycloak-issued JWT (JWKS) and forwards `X-User-Id` / `X-User-Roles`.
- All identity-service endpoints require a valid JWT; user administration requires an admin role (RBAC).

## Endpoints

| Method | Path | Auth | Idempotency | Summary |
| --- | --- | --- | --- | --- |
| GET | `/api/v1/users/{id}` | JWT | - | Fetch a user (domain projection). |
| GET | `/api/v1/users/me` | JWT | - | Current user's profile and roles. |
| GET | `/api/v1/users` | RBAC admin | - | List users (paged). |
| POST | `/api/v1/users` | RBAC admin | optional | Provision a user (via Keycloak Admin API) + local record. |
| PUT | `/api/v1/users/{id}/roles` | RBAC admin | - | Assign realm roles (via Keycloak Admin API). |
| GET | `/api/v1/roles` | RBAC admin | - | List roles/permissions. |

## Events

| Direction | Event |
| --- | --- |
| Publish | `user.created.v1` |

## Notes

- Authentication, refresh-token rotation, and reuse detection are **Keycloak realm features**
  (configured in the realm), not custom code in identity-service.
- identity-service administers users/roles through the Keycloak Admin API and keeps a local domain
  projection (for joins, app permissions, and audit). It is the system of record for app-specific
  authorization data, not for credentials.
- All responses wrapped in `ApiResult<T>`; errors as `ApiError` with `traceId`.

Reference: [service-catalog](../architecture/service-catalog.md),
[keycloak-and-auth.md](../architecture/keycloak-and-auth.md), ADR-011, ADR-015.
