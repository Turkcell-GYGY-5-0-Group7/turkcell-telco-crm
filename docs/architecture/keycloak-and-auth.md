# Keycloak and Authentication

| Field | Value |
| --- | --- |
| Authority | ADR-011 (security foundation) |
| Status | Realm provisioned in `infra/`; service-side integration TODO (Sprint 04-05) |
| Last updated | 2026-06-23 |

This is the authoritative integration guide for authentication. Per **ADR-011, Keycloak is the
identity provider and the token issuer**. No service mints or refreshes JWTs; identity-service manages
users/roles via Keycloak's Admin API. The local realm lives in
[`infra/docker/keycloak/`](../../infra/docker/keycloak/).

## 1. Token authority (the rule)

```text
Client (web) --Authorization Code + PKCE--> Keycloak --JWT--> API Gateway --validate (JWKS)--> services
```

- **Keycloak** owns login, JWT (access + refresh) issuance, refresh-token rotation, and reuse
  detection - all as realm features, not custom code.
- **API Gateway** validates every incoming JWT against the realm JWKS and forwards `X-User-Id` /
  `X-User-Roles` downstream (gateway-behind-trust).
- **Services** trust the gateway internally; `starter-security` can also validate a JWT directly via
  the realm public key for local/dev or defense-in-depth.
- **identity-service** is NOT a token issuer. It manages users/roles/permissions through the Keycloak
  Admin API and keeps a domain projection plus app-specific authorization data and audit.

## 2. Realm: `telco-crm`

Defined in [`infra/docker/keycloak/realm/realm-export.json`](../../infra/docker/keycloak/realm/realm-export.json),
imported on startup via `start-dev --import-realm`. Local development only.

| Setting | Value |
| --- | --- |
| Access token lifespan | 3600s |
| SSO session idle / max | 1800s / 36000s |
| Registration | disabled (users provisioned by admin / identity-service) |
| Reset password | enabled |

### Realm roles

`SUBSCRIBER`, `CALL_CENTER_AGENT`, `DEALER`, `MARKETING_MANAGER`, `BILLING_OPERATOR`, `ADMIN`,
`SERVICE`. These map to the personas in [`../product/personas.md`](../product/personas.md).

### Role -> claim mapping

The `telco-roles` client scope carries a protocol mapper (`oidc-usermodel-realm-role-mapper`) that
exposes realm roles as a **flat `roles` claim** in the access token. The platform `JwtService`
(`starter-security`) and the gateway read this `roles` claim for RBAC. Do not change the claim name
without updating `starter-security`.

## 3. Clients

| Client | Type | Flow | Notes |
| --- | --- | --- | --- |
| `telco-web` | public | Authorization Code + PKCE (standard flow); direct access grants for local testing | Redirect `http://localhost:3000/*` (the SvelteKit web app). |
| `telco-gateway` | confidential | standard flow + service accounts | Secret `local-dev-secret` (local only); redirect `http://localhost:8080/*`. |

Production clients use real secrets from Vault/K8s Secret and HTTPS redirect URIs.

## 4. Token flows

### Web app (production-shaped)

SvelteKit web client uses **Authorization Code + PKCE** against `telco-web`; tokens are validated by
the gateway. This is the only browser flow used in production.

### Local testing (password grant)

```bash
curl -s http://localhost:8085/realms/telco-crm/protocol/openid-connect/token \
  -d grant_type=password \
  -d client_id=telco-gateway \
  -d client_secret=local-dev-secret \
  -d username=admin@telco.local \
  -d password=admin | jq -r .access_token
```

### Service-to-service (dev)

`telco-gateway` has service accounts enabled; the `SERVICE` role represents internal callers. In
production, service-to-service trust is **mTLS** (ADR-011 Section 3), not bearer tokens.

## 5. Validation

- **Gateway**: validates the JWT signature and claims against the realm JWKS, discoverable at
  `http://localhost:8085/realms/telco-crm/.well-known/openid-configuration` (`jwks_uri`).
- **Services (`starter-security`)**: validate via the realm signing public key, configured as
  `telco.platform.security.jwt.public-key`. Used for direct/local validation; in the normal path the
  gateway has already validated and services trust the forwarded identity headers.

## 6. identity-service relationship

| Concern | Owner |
| --- | --- |
| Credentials, login, token issuance, refresh, reuse detection | Keycloak (realm) |
| User provisioning, role assignment | identity-service via Keycloak Admin API |
| App-specific permissions, domain projection of users, audit | identity-service (PostgreSQL) |

identity-service publishes `user.created.v1` after provisioning. See its contract:
[`../api-contracts/identity-service.md`](../api-contracts/identity-service.md).

## 7. Local setup

```bash
cd infra
make auth        # starts core services + keycloak (compose profile: auth)
```

- Admin console: `http://localhost:8085` (bootstrap admin from `.env`: `admin` / `admin`).
- Keycloak stores realm/user data in the `keycloak` PostgreSQL database (survives restarts).
- The realm (roles, scopes, clients, and the local demo users) is imported from the realm file on
  startup. The companion `keycloak-config` container only relaxes the `master` realm `sslRequired`
  for plain-HTTP local admin access; it does not seed the `telco-crm` realm.
- The realm signing key / OIDC discovery: `http://localhost:8085/realms/telco-crm`.

### Changing the realm

Edit `realm/realm-export.json` and recreate the container, or adjust the `keycloak-config` script.
Because import skips realms that already exist, run `make destroy` (drops the DB volume) or delete the
`telco-crm` realm in the console before re-importing.

## 8. Production notes (ADR-011)

- Real client secrets from Vault/K8s Secret; HTTPS everywhere; `sslRequired` enforced.
- Signing-key rotation; short access-token lifespans; refresh rotation enabled.
- Service-to-service is mTLS (SPIFFE/PKI), not bearer tokens (advanced design in
  [`../product/TELCO-CRM-ADVANCED.md`](../product/TELCO-CRM-ADVANCED.md)).
- One realm per environment (dev/test/prod); no shared secrets across environments.

## 9. Setup and integration checklist (Sprint 04-05)

- [x] Realm imported and reachable; OIDC discovery returns a `jwks_uri` (infra; done).
- [x] Gateway configured to validate Keycloak JWT via JWKS and propagate identity headers (Sprint 04).
- [ ] `starter-security` `telco.platform.security.jwt.public-key` wired to the realm key (Sprint 04-05).
- [ ] identity-service integrated with the Keycloak Admin API for user/role provisioning (Sprint 05).
- [x] Role -> `roles` claim verified end to end (Sprint 05.3); RBAC enforced on admin endpoints (Sprint 05.5).
- [ ] Web client (`telco-web`) Authorization Code + PKCE login working against the gateway (frontend sprint).
- [ ] Production hardening: real secrets, HTTPS, key rotation, per-env realms (Sprint 14-15).

Reference: [infra/docker/keycloak/README.md](../../infra/docker/keycloak/README.md), ADR-011, ADR-003.
