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

Defined in `realm/telco-realm.json`. Local development only.

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

Edit `realm/telco-realm.json` and recreate the container, or import a fresh export. Because import
skips realms that already exist, run `make destroy` (drops the DB volume) or delete the `telco-crm`
realm in the console before re-importing changes.
