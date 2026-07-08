# Security Posture and mTLS Decision

| Field | Value |
| --- | --- |
| Authority | ADR-011 (security foundation), NFR-05, NFR-06, NFR-12, NFR-18, ADR-021 |
| Status | MVP delivered (Sprints 04-13); reviewed in Sprint 14 (Feature 14.2); re-signed-off Phase 1, 2026-07-06 |
| Last updated | 2026-07-06 |
| Companion | `docs/architecture/keycloak-and-auth.md` (auth integration), `14.2-security-audit-report.md` (findings) |

This document records the platform's security posture and the explicit, reviewed decision to defer
mutual TLS (mTLS) for internal service-to-service traffic in the MVP, with the production
recommendation. It is the authoritative posture reference; detailed auth integration lives in
`keycloak-and-auth.md`.

## 1. Trust boundaries

```text
Browser / client
    | HTTPS (TLS) + Authorization Code + PKCE
    v
Keycloak (issuer)  --JWT (access + refresh)-->  client
    |
    v
API Gateway  --validate JWT via realm JWKS-->  strip client identity headers,
                                               inject X-User-Id / X-User-Roles from verified token
    | HTTP inside the cluster (gateway-behind-trust)
    v
Downstream services  --trust the forwarded identity headers-->  @PreAuthorize / mediator AuthorizationRule
```

- Edge (untrusted -> trusted): the API Gateway. It is the only component that validates the JWT and the
  only source of the identity headers.
- Internal (trusted): downstream services trust `X-User-Id` / `X-User-Roles` because the gateway
  strips any client-supplied copies and re-derives them from the verified token
  (`api-gateway/.../filter/JwtClaimsFilter.java`). Services may additionally validate the JWT directly
  via `starter-security` for local/dev or defense-in-depth.
- `/internal/**` endpoints are denied at the edge (routed to a local 404 sink) and are reachable only
  in-cluster (`GatewaySecurityConfig`).

## 2. Authentication and token handling (ADR-011)

- Keycloak is the identity provider and token issuer. No service mints or refreshes JWTs.
- Refresh-token rotation and reuse detection are realm features (`revokeRefreshToken: true`,
  `refreshTokenMaxReuse: 0`); realm event logging retains auth events for reuse-detection audit.
- identity-service manages users/roles/permissions via the Keycloak Admin API and owns app-specific
  authorization data plus its own audit trail; it is not a token issuer.
- Note on the brief: MVP brief Section 13 states identity-service issues the JWT; the delivered
  platform follows ADR-011 (Keycloak issuer). ADR-011 wins and the brief carries a reconciling
  platform note.

## 3. Authorization

- Role/permission-based via `@PreAuthorize` and the mediator `AuthorizationRule`, applied to admin and
  privileged endpoints. Realm roles are exposed as a flat `roles` claim (`telco-roles` client scope)
  and read by both the gateway and `starter-security`.
- Spring Security's `AccessDeniedException` and the platform `AccessDeniedException` both map to 403 via
  the platform `GlobalExceptionHandler`.

## 4. PII protection

### 4.1 At rest (NFR-06)
- Customer TCKN/VKN: AES-256-GCM, random 12-byte IV per record, 128-bit auth tag, Base64 stored in
  `identity_number_enc` (customer-service `IdentityNumberCryptoConverter`). Column stores ciphertext
  only; proven by an automated native-query DB-inspection test.
- Payment card data: not stored. payment-service delegates to a PSP and persists no PAN/CVV/expiry -
  the correct PCI-DSS posture (nothing to encrypt).
- email / username (identity-service): not in the NFR-06 encryption mandate (TCKN + card only); email
  is masked in telemetry via `@Sensitive`.

### 4.2 In telemetry (ADR-021)
- Layer A (structural): `@Sensitive` + Jackson masking module masks annotated fields in structured
  JSON logs.
- Layer B (free-text backstop): Logback `%mask` / `%maskEx` converters mask rendered messages and
  stack traces. Pattern set covers email, IBAN, PAN/card, MSISDN, and TCKN.
- Traces/metrics: no PII is ever placed into span attributes or metric labels (verified by source
  scan); this is the primary control for Tempo/Prometheus.
- Known limitation: the JSON appender shipped to Loki uses `LogstashEncoder`, which does not execute
  Logback pattern words, so Layer B does not run on the JSON body. Structured JSON masking relies on
  Layer A; keep a review rule that log statements never interpolate raw PII into the message body.

## 5. Key management and rotation (NFR-06)

- The AES key is a Base64 32-byte (AES-256) value supplied via configuration
  (`customer.crypto.aes-key`). In staging/production it is injected from a Kubernetes Secret / Vault
  with no default (`CUSTOMER_AES_KEY`); only the local base profile carries a dev default.
- Current model: a single active key encrypts and decrypts (MVP scope).
- Rotation procedure (single active key, MVP):
  1. Generate a new 32-byte key; store it in the Secret/Vault under a new version.
  2. Re-encrypt existing rows: read via the old key, write via the new key (offline batch or a
     maintenance job), within a maintenance window.
  3. Swap `CUSTOMER_AES_KEY` to the new value and roll the pods.
- Production recommendation (zero-downtime rotation): introduce a key-id column and an envelope /
  multi-key provider so the active key can rotate while old ciphertext remains decryptable by key id
  (dual-read, single-write). Extract the AES-GCM converter and key provider into a platform
  `starter-crypto` (already flagged in `platform-capabilities.md`) so all services share one rotation
  path. Back the DEK with a KMS/HSM CMK for envelope encryption.

## 6. Rate limiting (NFR-18)

- Redis-backed fixed-window limiter at the gateway: 100 req/min, keyed by JWT subject (client IP for
  unauthenticated allowlisted routes), atomic INCR+EXPIRE Lua script; exceed returns 429 in the
  standard error envelope.
- Fails OPEN on a Redis outage (a Redis failure must not take down the edge) - a deliberate
  availability-over-strictness tradeoff. Production recommendation: alert on the fail-open path and
  consider a conservative local fallback limit if Redis is unavailable for an extended period.

## 7. Error handling (no leakage)

- The platform `GlobalExceptionHandler` maps each exception to a status and returns an `ApiResult`
  error envelope carrying `code`, `traceId`, and a server-side `logId`. The catch-all returns a generic
  `INTERNAL_ERROR` message and never the exception message or stack trace; full detail is logged
  server-side and referenced by `logId`. Gateway 401/403 return fixed JSON envelopes. No stack trace is
  ever exposed to clients.

## 8. mTLS decision (NFR-05, ADR-011)

### Decision: deferred for the MVP
Internal service-to-service traffic runs over plain HTTP inside the cluster under the
gateway-behind-trust model; mTLS is NOT enabled in the MVP. This is explicit in the MVP brief Section
13: "Services do not re-validate the JWT internally; gateway-behind-trust is used. (mTLS is recommended
in production; out of scope for the MVP.)"

### Rationale
- The gateway is the single validated trust boundary; it strips and re-injects identity headers, so
  spoofing from outside is prevented.
- `/internal/**` is not reachable from the edge.
- For the MVP the cluster network is the trust perimeter; adding mTLS (cert issuance, rotation, SPIFFE
  identities, sidecars/mesh) is significant operational surface that does not change the external threat
  model for a single-cluster MVP.

### Residual risk accepted for the MVP
- An attacker with in-cluster network position could call a downstream service directly with forged
  `X-User-Id` / `X-User-Roles` headers, bypassing the gateway. This depends on the cluster network
  being compromised (NetworkPolicies and namespace isolation are the compensating controls) and is
  accepted for the MVP.

### Production recommendation
- Enforce mTLS for all internal service-to-service traffic (service mesh such as Istio/Linkerd, or
  SPIFFE/SPIRE-issued workload certificates), so identity is cryptographically bound per workload and
  downstream services can reject any caller that is not the gateway.
- Combine with Kubernetes NetworkPolicies (default-deny, explicit allow) and, for defense-in-depth,
  have downstream services validate the JWT directly via `starter-security` in addition to trusting the
  gateway.
- Service-to-service auth in production is mTLS (SPIFFE/PKI), not bearer tokens (ADR-011 Section 3;
  enterprise design in `docs/product/TELCO-CRM-ADVANCED.md`).

## 9. Audit logging (NFR-12)

- Mandatory in identity, customer, payment, and subscription. Each row carries actor, action, entity,
  entityId, correlationId, details (no PII), and timestamp, written inside the mediator transaction.
- Current status (Sprint 14 Phase 1 re-sign-off, 2026-07-06): all four mandated services are complete.
  identity and subscription were complete from the original review; the two gaps flagged on 2026-07-03
  (payment-service had no audit infrastructure at all; customer-service did not audit the address
  sub-resource) have both been closed - payment-service now has its own `audit_log` table/entity/
  repository/writer wired into `ChargePaymentCommandHandler` and `RefundPaymentCommandHandler`, and
  customer-service's three address handlers now call the existing `AuditLogWriter`. See
  `docs/tasks/sprint-14-testing-and-hardening/14.2-security-audit-report.md` (subtask 14.2.3 and its
  2026-07-06 addendum) for the file-level evidence.

## 10. Production hardening checklist (carried forward)

- [ ] mTLS for internal traffic (mesh or SPIFFE/SPIRE) + default-deny NetworkPolicies.
- [x] Close the payment-service audit gap (HIGH) and confirm/close the customer address audit gap.
      Done 2026-07-06 - see Section 9.
- [ ] `starter-crypto` extraction + key-id/envelope encryption for zero-downtime key rotation, DEK
      backed by KMS/HSM.
- [ ] Real secrets from Vault/K8s Secret; HTTPS everywhere; `sslRequired` enforced; per-env realms.
- [ ] Set `CORS_ALLOWED_ORIGINS` to the production origin allowlist.
- [ ] Alerting on the rate-limiter fail-open path.
</content>
