# Keycloak (auth profile)

Keycloak provides OAuth2 / OIDC for the platform (ADR-011). It uses **PostgreSQL** as its data
store (database `keycloak`, created by `postgres/initdb`), so realm and user data survive restarts.
The `telco-crm` realm is **imported automatically** on startup via `start-dev --import-realm`, so
no manual panel setup is needed.

## Start

```bash
cd infra
make auth        # starts core + keycloak
```

Admin console: http://localhost:8085  (bootstrap admin from `.env`: `admin` / `admin`).

## Imported realm: `telco-crm`

Defined in `realm/realm-export.json` (roles, client scopes, clients, and the local demo users), all
imported on startup. The companion `keycloak-config` container only relaxes the `master` realm
`sslRequired` for local plain-HTTP admin access; it does not seed this realm. Local development only.
Full auth integration guide: [`docs/architecture/keycloak-and-auth.md`](../../../docs/architecture/keycloak-and-auth.md).

Realm roles: `SUBSCRIBER`, `CALL_CENTER_AGENT`, `DEALER`, `MARKETING_MANAGER`,
`BILLING_OPERATOR`, `ADMIN`, `SERVICE`. A protocol mapper exposes realm roles as a flat `roles`
claim in the access token, matching the platform `JwtService` (starter-security).

Clients:

| Client | Type | Notes |
| --- | --- | --- |
| `telco-web` | public | Authorization Code + PKCE; direct access grants enabled for local testing |
| `telco-gateway` | confidential | secret `local-dev-secret`; service accounts + direct access grants |

Demo users (password = the obvious value, local only):

| Username | Password | Role |
| --- | --- | --- |
| `admin@telco.local` | `admin` | ADMIN |
| `agent@telco.local` | `agent` | CALL_CENTER_AGENT |
| `subscriber@telco.local` | `subscriber` | SUBSCRIBER |

In addition, `loadtest-user-01@telco.local` through `loadtest-user-30@telco.local` (password
`loadtest`, role SUBSCRIBER) are seeded for k6/perf load-test runs only (task 14.3.1). The gateway's
per-JWT-subject rate limiter is 100 req/min; a single seeded identity cannot sustain 20-50 VU load
test concurrency, so the load-test suite round-robins its virtual users across this pool of
identities instead. These are test-infrastructure identities, not application users - do not use
them in acceptance/functional test scenarios that assert on a specific persona's data.

## Get a token (password grant, for local testing)

```bash
curl -s http://localhost:8085/realms/telco-crm/protocol/openid-connect/token \
  -d grant_type=password \
  -d client_id=telco-gateway \
  -d client_secret=local-dev-secret \
  -d username=admin@telco.local \
  -d password=admin | jq -r .access_token
```

The realm signing public key (for services validating Keycloak tokens via
`telco.platform.security.jwt.public-key`) is at:
`http://localhost:8085/realms/telco-crm` (see `jwks_uri` in the OIDC discovery document
`/.well-known/openid-configuration`).

## Changing the realm

Edit `realm/realm-export.json` and recreate the container, or import a fresh export. Because import
skips realms that already exist, run `make destroy` (drops the DB volume) or delete the `telco-crm`
realm in the console before re-importing changes.
