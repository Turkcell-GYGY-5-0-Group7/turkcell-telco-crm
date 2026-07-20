# Security & Identity

Full detail lives in [Security Posture](../architecture/security-posture.md) and
[Keycloak & Auth](../architecture/keycloak-and-auth.md) - this page is the map.

## Trust boundary

```text
Browser / client
    | HTTPS + Authorization Code + PKCE
    v
Keycloak (issuer) --JWT (access + refresh)--> client
    |
    v
API Gateway --validate JWT via realm JWKS--> strip client identity headers,
                                              inject X-User-Id / X-User-Roles from the verified token
    | HTTP inside the cluster (gateway-behind-trust)
    v
Downstream services --trust the forwarded identity headers--> @PreAuthorize / mediator AuthorizationRule
```

**Keycloak is the identity provider and the only JWT issuer.** No service mints or refreshes
tokens. identity-service manages users/roles/permissions through the Keycloak Admin API and owns
app-specific authorization data plus its own audit trail - it is explicitly not a token issuer
([ADR-011](../adr/ADR-011-security-foundation.md)).

## Realm and roles

Realm `telco-crm`, defined in `infra/docker/keycloak/realm/realm-export.json`, imported on
container startup for local development.

| Realm role | Maps to persona |
| --- | --- |
| `SUBSCRIBER` | End-user subscriber |
| `CALL_CENTER_AGENT` | Support agent |
| `DEALER` | Field / retail dealer |
| `MARKETING_MANAGER` | Marketing manager |
| `BILLING_OPERATOR` | Billing / finance operator |
| `ADMIN` | Platform administrator |
| `SERVICE` | Internal service-to-service caller (dev only) |

Roles are exposed as a flat `roles` claim (the `telco-roles` client scope) that both the gateway
and `starter-security` read for RBAC. Two clients: `telco-web` (public, Authorization Code + PKCE,
the browser flow) and `telco-gateway` (confidential, service accounts for dev-only
service-to-service testing).

## Authorization

Role/permission-based via `@PreAuthorize` and the mediator's `AuthorizationRule`, applied to
admin and privileged endpoints. Both Spring Security's and the platform's own
`AccessDeniedException` map to HTTP 403 through the shared `GlobalExceptionHandler`.

## PII protection

- **At rest:** Customer TCKN/VKN is AES-256-GCM encrypted (random 12-byte IV per record) via a
  JPA attribute converter; the database column stores ciphertext only. Payment card data is never
  stored at all - payment-service delegates to a PSP.
- **In telemetry** ([ADR-021](../adr/ADR-021-pii-and-data-masking-strategy.md)): a `@Sensitive`
  annotation drives a masking `ObjectMapper` for structured JSON logs (the primary control), with
  a Logback pattern-converter as a free-text backstop covering email, IBAN, PAN, MSISDN, and TCKN.
  Traces and metrics never carry PII in span attributes or metric labels. Masking applies only to
  logs and persisted request/exception logs - it never touches outbox/Kafka payloads, API
  responses, or a service's own database rows, which is the whole point: wire data stays correct,
  only observability surfaces are masked.

## Rate limiting

Redis-backed fixed-window limiter at the gateway: 100 requests/minute, keyed by JWT subject (or
client IP for unauthenticated allowlisted routes), via an atomic `INCR`+`EXPIRE` Lua script.
Exceeding the limit returns HTTP 429 in the standard `ApiResult` error envelope. It **fails open**
on a Redis outage - a deliberate availability-over-strictness tradeoff, the mirror image of
`starter-lock`'s fail-closed behavior.

## Service-to-service trust (mTLS)

The MVP ran internal traffic over plain HTTP inside the cluster under the gateway-behind-trust
model, with the residual risk (an in-cluster attacker forging identity headers to bypass the
gateway) explicitly accepted and compensated by NetworkPolicies and namespace isolation. Sprint 19
closes this gap: **Linkerd** (edge channel, chosen over Istio and bare SPIFFE/SPIRE) provides
automatic sidecar mTLS on all in-cluster traffic plus default-deny NetworkPolicies, as a second,
independent trust layer alongside the existing JWT/gateway model - neither layer replaces the
other. See [ADR-026](../adr/ADR-026-service-mesh-and-mtls.md).

## Secrets

Local development uses committed-default Kubernetes Secrets. Sprint 18 introduces **HashiCorp
Vault** (in-cluster, standalone Raft storage) delivered via the Secrets Store CSI Driver syncing
into native Kubernetes Secrets - chosen specifically so no application or Dockerfile code has to
change, since services keep consuming secrets via `envFrom.secretRef` exactly as before. See
[ADR-025](../adr/ADR-025-secrets-and-key-management.md).

## Error handling

The platform's `GlobalExceptionHandler` maps every exception to a status and an `ApiResult` error
envelope carrying a stable `code`, `traceId`, and a server-side `logId` - never a stack trace or
raw exception message. Full detail is logged server-side and referenced by `logId` only.

For the complete picture (key rotation, audit logging scope, production hardening checklist),
read [Security Posture](../architecture/security-posture.md) in full.
