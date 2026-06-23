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

## Run (dev)

```bash
SPRING_PROFILES_ACTIVE=dev mvn -pl api-gateway spring-boot:run
```
