# web-bff

| Field | Value |
| --- | --- |
| Port | 9020 |
| Architecture Mode (ADR-004) | Simple Service Layer |
| Infrastructure Profile (ADR-006) | Stateless (no primary store) |
| Owning sprint | [Sprint 16](../../docs/tasks/sprint-16-frontend-and-bff/README.md) (post-MVP) |
| Status | Skeleton (no business code) |

Backend-for-Frontend for the SvelteKit web channel (ADR-022). Aggregates and shapes domain service
responses for the frontend; owns the Keycloak token exchange (PKCE) and session lifecycle.
Depends on domain services via REST/OpenFeign (ADR-005). Contract:
[web-bff](../../docs/api-contracts/web-bff.md).

Runtime config is centralized in config-server; this module keeps only the `spring.config.import`
bootstrap. Reuse platform capabilities first ([catalog](../../docs/architecture/platform-capabilities.md)).

## Run (dev)

```bash
SPRING_PROFILES_ACTIVE=dev mvn -pl web-bff spring-boot:run
```
